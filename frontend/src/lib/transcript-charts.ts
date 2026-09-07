import type { StudentTranscriptSemester } from '@/types/api';

export interface GpaTrendPoint {
  label: string;
  fullLabel: string;
  gpa: number;
}

export interface GradeDistributionBucket {
  letter: string;
  count: number;
}

const LETTER_ORDER = [
  'A+', 'A', 'A-', 'B+', 'B', 'B-', 'C+', 'C', 'C-', 'D+', 'D', 'F',
];

/**
 * The transcript API returns semesters newest-first; a trend only reads well
 * oldest-first, so the order is reversed here. One graded semester yields a
 * single point — the chart component renders that as a dot, not a broken line.
 */
export function buildGpaTrendPoints(
  semesters: StudentTranscriptSemester[],
  locale: string,
): GpaTrendPoint[] {
  return [...semesters]
    .reverse()
    .map((semester, index) => ({
      label: locale === 'vi' ? `HK${index + 1}` : `T${index + 1}`,
      fullLabel:
        (locale === 'vi'
          ? semester.semesterNameVi ?? semester.semesterNameEn
          : semester.semesterNameEn ?? semester.semesterNameVi) ??
        semester.semesterName,
      gpa: semester.gpa,
    }));
}

/**
 * Buckets published letter grades for the selected transcript scope. Records
 * without a letter grade are ignored; buckets follow the standard A..F order.
 */
export function buildGradeDistribution(
  semesters: StudentTranscriptSemester[],
): GradeDistributionBucket[] {
  const counts = new Map<string, number>();
  semesters.forEach((semester) => {
    semester.records.forEach((record) => {
      if (!record.letterGrade) return;
      counts.set(record.letterGrade, (counts.get(record.letterGrade) ?? 0) + 1);
    });
  });
  return [...counts.entries()]
    .map(([letter, count]) => ({ letter, count }))
    .sort((a, b) => {
      const aIndex = LETTER_ORDER.indexOf(a.letter);
      const bIndex = LETTER_ORDER.indexOf(b.letter);
      return (
        (aIndex === -1 ? LETTER_ORDER.length : aIndex) -
        (bIndex === -1 ? LETTER_ORDER.length : bIndex)
      );
    });
}
