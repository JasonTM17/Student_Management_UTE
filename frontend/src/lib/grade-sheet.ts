/**
 * Parser for feedback item 9: bulk grade entry from a spreadsheet.
 *
 * Accepts the shapes Excel actually produces — a CSV or semicolon export, and
 * the tab-separated block that copying a range puts on the clipboard. Rows are
 * matched to enrollments by student code or email so column order elsewhere in
 * the sheet does not matter. Pure data in, pure data out: the caller decides
 * what to do with the result, and nothing here touches the network.
 */

export interface GradeImportRow {
  enrollmentId: string;
  processScore: number;
  finalExamScore: number;
}

export interface GradeImportStudent {
  enrollmentId: string;
  studentCode: string;
  email?: string | null;
}

export interface SkippedRow {
  line: number;
  reason: 'missing-student' | 'unknown-student' | 'invalid-score' | 'duplicate';
}

export interface ParsedGradeSheet {
  applied: GradeImportRow[];
  skipped: SkippedRow[];
}

/** Normalises a sheet cell: trimmed, thousands separators dropped, comma decimal accepted. */
function toScore(raw: string): number | null {
  const cleaned = raw.trim().replace(/\s/g, '').replace(/,/g, '.');
  if (cleaned === '') return null;
  const value = Number(cleaned);
  return Number.isFinite(value) ? value : null;
}

const CODE_HINT = /(code|msv|ma\s*sv|ma\s*sinh\s*vien|mã)/i;
const PROCESS_HINT = /(đqt|dqt|process|quá\s*trình|qua\s*trinh|midterm)/i;
const FINAL_HINT = /(đck|dck|final|cuối|cuoi)/i;
const EMAIL_HINT = /@/;

function looksLikeHeader(cells: string[]): boolean {
  return cells.some((cell) => CODE_HINT.test(cell) || PROCESS_HINT.test(cell) || FINAL_HINT.test(cell));
}

function splitRow(line: string): string[] {
  if (line.includes('\t')) return line.split('\t');
  if (line.includes(';')) return line.split(';');
  return line.split(',');
}

export function parseGradeSheet(text: string, students: GradeImportStudent[]): ParsedGradeSheet {
  const applied: GradeImportRow[] = [];
  const skipped: SkippedRow[] = [];
  const byCode = new Map(students.map((student) => [student.studentCode.toLowerCase(), student]));
  const byEmail = new Map(students.filter((s) => s.email).map((s) => [s.email!.toLowerCase(), s]));

  const lines = text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line !== '');
  if (lines.length === 0) {
    return { applied, skipped: [{ line: 1, reason: 'missing-student' }] };
  }

  let codeIndex = 0;
  let processIndex = 1;
  let finalIndex = 2;
  let firstDataRow = 0;

  const firstCells = splitRow(lines[0]);
  if (looksLikeHeader(firstCells)) {
    firstCells.forEach((cell, index) => {
      if (EMAIL_HINT.test(cell) || CODE_HINT.test(cell)) codeIndex = index;
      else if (PROCESS_HINT.test(cell)) processIndex = index;
      else if (FINAL_HINT.test(cell)) finalIndex = index;
    });
    firstDataRow = 1;
  }

  const seen = new Set<string>();
  lines.forEach((line, offset) => {
    const lineNumber = offset + 1;
    if (offset < firstDataRow) return;
    const cells = splitRow(line);
    const key = (cells[codeIndex] ?? '').trim().toLowerCase();
    if (key === '') {
      skipped.push({ line: lineNumber, reason: 'missing-student' });
      return;
    }
    if (seen.has(key)) {
      skipped.push({ line: lineNumber, reason: 'duplicate' });
      return;
    }
    seen.add(key);

    const student = byCode.get(key) ?? byEmail.get(key);
    if (!student) {
      skipped.push({ line: lineNumber, reason: 'unknown-student' });
      return;
    }

    const processScore = toScore(cells[processIndex] ?? '');
    const finalExamScore = toScore(cells[finalIndex] ?? '');
    if (
      processScore === null ||
      finalExamScore === null ||
      processScore < 0 ||
      processScore > 10 ||
      finalExamScore < 0 ||
      finalExamScore > 10
    ) {
      skipped.push({ line: lineNumber, reason: 'invalid-score' });
      return;
    }

    applied.push({ enrollmentId: student.enrollmentId, processScore, finalExamScore });
  });

  return { applied, skipped };
}
