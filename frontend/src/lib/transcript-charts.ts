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
 * Builds overall cumulative GPA trend points across semesters in chronological order.
 */
export function buildCumulativeGpaTrendPoints(
  semesters: StudentTranscriptSemester[],
  locale: string,
  serverCumulativeGpa?: number | null,
): GpaTrendPoint[] {
  const chronological = [...semesters].reverse();
  let accumulatedPoints = 0;
  let accumulatedCredits = 0;

  return chronological.map((semester, index) => {
    const semCredits = semester.creditsAttempted ?? 0;
    accumulatedPoints += semester.gpa * semCredits;
    accumulatedCredits += semCredits;
    let cumulativeGpa =
      accumulatedCredits > 0 ? accumulatedPoints / accumulatedCredits : semester.gpa;

    if (index === chronological.length - 1 && typeof serverCumulativeGpa === 'number') {
      cumulativeGpa = serverCumulativeGpa;
    }

    return {
      label: locale === 'vi' ? `HK${index + 1}` : `T${index + 1}`,
      fullLabel:
        (locale === 'vi'
          ? semester.semesterNameVi ?? semester.semesterNameEn
          : semester.semesterNameEn ?? semester.semesterNameVi) ??
        semester.semesterName,
      gpa: Math.round(cumulativeGpa * 100) / 100,
    };
  });
}

/**
 * Per-semester average of the 0-10 final grades. Semesters without any graded
 * record are skipped so the dashed ten-scale line only spans graded terms;
 * the remaining points stay index-aligned with the GPA line's x axis.
 */
export function buildTenScaleTrendPoints(
  semesters: StudentTranscriptSemester[],
  locale: string,
): GpaTrendPoint[] {
  return [...semesters]
    .reverse()
    .filter((semester) =>
      semester.records.some((record) => typeof record.finalGrade === 'number'),
    )
    .map((semester, index) => {
      const graded = semester.records.filter(
        (record) => typeof record.finalGrade === 'number',
      );
      // ĐTB hệ 10 is credit-weighted: a 4-credit course moves the average
      // four times as much as a 1-credit one. A simple mean under-weighted
      // heavy courses and disagreed with the registrar's number.
      const weighted = graded.reduce(
        (sum, record) => sum + (record.finalGrade ?? 0) * (record.credits || 0),
        0,
      );
      const credits = graded.reduce((sum, record) => sum + (record.credits || 0), 0);
      const average = credits > 0 ? weighted / credits : 0;

      return {
        label: locale === 'vi' ? `HK${index + 1}` : `T${index + 1}`,
        fullLabel:
          (locale === 'vi'
            ? semester.semesterNameVi ?? semester.semesterNameEn
            : semester.semesterNameEn ?? semester.semesterNameVi) ??
          semester.semesterName,
        gpa: Math.round(average * 10) / 10,
      };
    });
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
