import type { StudentGradeRecord } from '@/types/api';

/**
 * Letter grade → 4.0-scale points, following the convention already used by the
 * transcript and grade pages at HCMUTE.
 */
export const GRADE_POINTS: Record<string, number> = {
  'A+': 4,
  A: 4,
  'A-': 3.7,
  'B+': 3.3,
  B: 3,
  'B-': 2.7,
  'C+': 2.3,
  C: 2,
  'C-': 1.7,
  'D+': 1.3,
  D: 1,
  'D-': 0.7,
  F: 0,
};

/**
 * The grades API omits `gradePoint` on some rows, which made the 4.0 column
 * read as "not available" even when the letter grade was known. Fall back to
 * the letter grade so the converted value is always shown when it is derivable.
 * Returns null only when neither source is available.
 */
export function resolveGradePoint(
  record: Pick<StudentGradeRecord, 'gradePoint' | 'letterGrade'>,
): number | null {
  if (typeof record.gradePoint === 'number' && Number.isFinite(record.gradePoint)) {
    return record.gradePoint;
  }
  const letter = record.letterGrade;
  if (letter && GRADE_POINTS[letter] !== undefined) {
    return GRADE_POINTS[letter];
  }
  return null;
}

/** A credit-weighted 4.0 GPA over the records that have a derivable grade point. */
export function computeGpa4(records: StudentGradeRecord[]): number | null {
  let weighted = 0;
  let credits = 0;
  for (const record of records) {
    const point = resolveGradePoint(record);
    if (point === null || !Number.isFinite(record.credits)) continue;
    weighted += point * record.credits;
    credits += record.credits;
  }
  return credits > 0 ? weighted / credits : null;
}
