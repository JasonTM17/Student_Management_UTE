import type { StudentGradeRecord } from '@/types/api';

/**
 * Letter grade → 4.0-scale points, following the official HCMUTE conversion
 * table for the 10-point scale (the same bands the backend assigns when it
 * turns a 0-10 score into a letter): A+/A=4.0, B+=3.5, B=3.0, C+=2.5, C=2.0,
 * D+=1.5, D=1.0, F=0. The minus rows never come out of the 10-point bands;
 * they only exist so legacy records with those letters still render.
 */
export const GRADE_POINTS: Record<string, number> = {
  'A+': 4,
  A: 4,
  'A-': 3.7,
  'B+': 3.5,
  B: 3,
  'B-': 2.7,
  'C+': 2.5,
  C: 2,
  'C-': 1.7,
  'D+': 1.5,
  D: 1,
  'D-': 0.7,
  F: 0,
};

/**
 * The official 10-point → letter → 4.0 conversion table (feedback item 2).
 * `min` is inclusive, `max` exclusive except the last band, which catches F.
 */
export interface GradeConversionRow {
  letter: string;
  min: number;
  max: number | null;
  point: number;
}

export const CONVERSION_TABLE: GradeConversionRow[] = [
  { letter: 'A+', min: 9.0, max: 10.01, point: 4 },
  { letter: 'A', min: 8.5, max: 9.0, point: 4 },
  { letter: 'B+', min: 8.0, max: 8.5, point: 3.5 },
  { letter: 'B', min: 7.0, max: 8.0, point: 3 },
  { letter: 'C+', min: 6.5, max: 7.0, point: 2.5 },
  { letter: 'C', min: 5.5, max: 6.5, point: 2 },
  { letter: 'D+', min: 5.0, max: 5.5, point: 1.5 },
  { letter: 'D', min: 4.0, max: 5.0, point: 1 },
  { letter: 'F', min: 0, max: 4.0, point: 0 },
];

/** Convert a 0-10 score to its official letter grade. */
export function letterFromScore(score: number): string {
  const row = CONVERSION_TABLE.find((entry) => score >= entry.min && score < (entry.max ?? Infinity));
  return row ? row.letter : 'F';
}

/** Convert a 0-10 score to its official 4.0-scale point. */
export function pointFromScore(score: number): number {
  const row = CONVERSION_TABLE.find((entry) => score >= entry.min && score < (entry.max ?? Infinity));
  return row ? row.point : 0;
}

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

export type GradeBand =
  | 'EXCELLENT'
  | 'GOOD'
  | 'FAIR'
  | 'UPPER_AVERAGE'
  | 'AVERAGE'
  | 'BELOW_AVERAGE'
  | 'PASS'
  | 'RETAKE';

/**
 * Single source of truth for classifying a thesis score (feedback item 2):
 * the same 10-point bands as the course scale, replacing the divergent local
 * classifier the thesis page used to carry.
 */
export function classifyThesisScore(score: number): { letter: string; gpa4: string; band: GradeBand } {
  const letter = letterFromScore(score);
  const point = pointFromScore(score);
  const band: GradeBand =
    letter === 'A+' || letter === 'A'
      ? 'EXCELLENT'
      : letter === 'B+'
        ? 'GOOD'
        : letter === 'B'
          ? 'FAIR'
          : letter === 'C+'
            ? 'UPPER_AVERAGE'
            : letter === 'C'
              ? 'AVERAGE'
              : letter === 'D+'
                ? 'BELOW_AVERAGE'
                : letter === 'D'
                  ? 'PASS'
                  : 'RETAKE';
  return { letter, gpa4: point.toFixed(1), band };
}
