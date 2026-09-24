/**
 * Grade export utilities for course sections.
 * Generates RFC 4180 compliant CSV with UTF-8 BOM (\uFEFF)
 * for seamless compatibility with Microsoft Excel on Windows without character corruption.
 */

export interface GradeExportStudent {
  studentId: string;
  studentCode: string;
  studentName: string;
  email?: string;
  processScore: number | null;
  finalExamScore: number | null;
  totalScore?: number | null;
  letterGrade?: string | null;
  gradeStatus?: string | null;
}

export interface GradeDistributionSummary {
  totalStudents: number;
  gradedCount: number;
  ungradedCount: number;
  passedCount: number;
  failedCount: number;
  passRate: number;
  averageScore: number | null;
  counts: {
    'A+': number;
    A: number;
    'B+': number;
    B: number;
    'C+': number;
    C: number;
    'D+': number;
    D: number;
    F: number;
  };
}

export interface GradeCsvOptions {
  sectionNumber: string;
  courseCode?: string;
  courseName?: string;
  semesterName?: string;
  lecturerName?: string;
  students: GradeExportStudent[];
  locale?: 'vi' | 'en';
}

/**
 * Standard university credit grading scale (Thang điểm 10 -> Chữ theo quy chế Bộ GD&ĐT & UTE).
 */
export function calculateGrade(score: number): string {
  if (score >= 9.0) return 'A+';
  if (score >= 8.5) return 'A';
  if (score >= 8.0) return 'B+';
  if (score >= 7.0) return 'B';
  if (score >= 6.5) return 'C+';
  if (score >= 5.5) return 'C';
  if (score >= 5.0) return 'D+';
  if (score >= 4.0) return 'D';
  return 'F';
}

/**
 * Calculates total 10-point scale grade with 50/50 weighting.
 */
export function calculateTotalScore(processScore: number | null, finalExamScore: number | null): number | null {
  if (processScore === null || finalExamScore === null || !Number.isFinite(processScore) || !Number.isFinite(finalExamScore)) {
    return null;
  }
  return Math.round(((processScore + finalExamScore) / 2) * 100) / 100;
}

/**
 * Computes grade distribution and summary metrics for a section.
 */
export function computeGradeSummary(students: GradeExportStudent[]): GradeDistributionSummary {
  const counts = {
    'A+': 0,
    A: 0,
    'B+': 0,
    B: 0,
    'C+': 0,
    C: 0,
    'D+': 0,
    D: 0,
    F: 0,
  };

  let gradedCount = 0;
  let passedCount = 0;
  let totalScoreSum = 0;

  students.forEach((student) => {
    const total = student.totalScore !== undefined && student.totalScore !== null
      ? student.totalScore
      : calculateTotalScore(student.processScore, student.finalExamScore);

    if (total !== null && Number.isFinite(total)) {
      gradedCount += 1;
      totalScoreSum += total;
      const letter = student.letterGrade || calculateGrade(total);
      if (letter in counts) {
        counts[letter as keyof typeof counts] += 1;
      }
      if (total >= 4.0) {
        passedCount += 1;
      }
    }
  });

  const totalStudents = students.length;
  const ungradedCount = totalStudents - gradedCount;
  const failedCount = gradedCount - passedCount;
  const passRate = gradedCount > 0 ? Math.round((passedCount / gradedCount) * 1000) / 10 : 0;
  const averageScore = gradedCount > 0 ? Math.round((totalScoreSum / gradedCount) * 100) / 100 : null;

  return {
    totalStudents,
    gradedCount,
    ungradedCount,
    passedCount,
    failedCount,
    passRate,
    averageScore,
    counts,
  };
}

/**
 * Escapes a cell value for RFC 4180 CSV standard compliance.
 */
export function escapeCsvField(value: unknown): string {
  if (value === null || value === undefined) return '""';
  const str = String(value);
  return `"${str.replace(/"/g, '""')}"`;
}

/**
 * Generates an institutional grade roster in CSV format with UTF-8 BOM
 * for direct compatibility with Microsoft Excel on Windows.
 */
export function generateGradeCsv(options: GradeCsvOptions): string {
  const {
    sectionNumber,
    courseCode = '',
    courseName = '',
    semesterName = '',
    lecturerName = '',
    students,
    locale = 'vi',
  } = options;

  const isVi = locale === 'vi';
  const summary = computeGradeSummary(students);
  const rows: string[] = [];

  // 1. Institutional Header
  rows.push(
    escapeCsvField(
      isVi
        ? 'TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP. HỒ CHÍ MINH — CAMPUSCORE'
        : 'HO CHI MINH CITY UNIVERSITY OF TECHNOLOGY AND ENGINEERING — CAMPUSCORE',
    ),
  );
  rows.push(
    escapeCsvField(
      isVi
        ? 'BẢNG ĐIỂM TỔNG KẾT HỌC PHẦN'
        : 'COURSE SECTION FINAL GRADE ROSTER',
    ),
  );
  rows.push('');

  // 2. Section Metadata
  const fullCourse = [courseCode, courseName].filter(Boolean).join(' - ');
  rows.push(
    [
      escapeCsvField(isVi ? 'Lớp học phần:' : 'Section:'),
      escapeCsvField(sectionNumber),
      escapeCsvField(isVi ? 'Học phần:' : 'Course:'),
      escapeCsvField(fullCourse),
    ].join(','),
  );

  rows.push(
    [
      escapeCsvField(isVi ? 'Học kỳ:' : 'Semester:'),
      escapeCsvField(semesterName || (isVi ? 'Học kỳ hiện tại' : 'Current term')),
      escapeCsvField(isVi ? 'Giảng viên:' : 'Instructor:'),
      escapeCsvField(lecturerName || (isVi ? 'Ban Giảng viên' : 'Faculty')),
    ].join(','),
  );
  rows.push('');

  // 3. Grade Distribution Summary
  rows.push(
    escapeCsvField(
      isVi ? '--- THỐNG KÊ KẾT QUẢ ĐÁNH GIÁ ---' : '--- GRADE DISTRIBUTION SUMMARY ---',
    ),
  );
  rows.push(
    [
      escapeCsvField(isVi ? 'Sĩ số:' : 'Total:'),
      escapeCsvField(summary.totalStudents),
      escapeCsvField(isVi ? 'Đã chấm:' : 'Graded:'),
      escapeCsvField(summary.gradedCount),
      escapeCsvField(isVi ? 'Chưa chấm:' : 'Ungraded:'),
      escapeCsvField(summary.ungradedCount),
      escapeCsvField(isVi ? 'Đạt (>= 4.0):' : 'Passed:'),
      escapeCsvField(summary.passedCount),
      escapeCsvField(isVi ? 'Không đạt (< 4.0):' : 'Failed:'),
      escapeCsvField(summary.failedCount),
      escapeCsvField(isVi ? 'Tỷ lệ đạt:' : 'Pass Rate:'),
      escapeCsvField(`${summary.passRate.toFixed(1)}%`),
      escapeCsvField(isVi ? 'Điểm trung bình:' : 'Average:'),
      escapeCsvField(summary.averageScore !== null ? summary.averageScore.toFixed(2) : 'N/A'),
    ].join(','),
  );

  rows.push(
    [
      escapeCsvField('A+ / A:'),
      escapeCsvField(summary.counts['A+'] + summary.counts.A),
      escapeCsvField('B+ / B:'),
      escapeCsvField(summary.counts['B+'] + summary.counts.B),
      escapeCsvField('C+ / C:'),
      escapeCsvField(summary.counts['C+'] + summary.counts.C),
      escapeCsvField('D+ / D:'),
      escapeCsvField(summary.counts['D+'] + summary.counts.D),
      escapeCsvField('F:'),
      escapeCsvField(summary.counts.F),
    ].join(','),
  );
  rows.push('');

  // 4. Student Table Header
  rows.push(
    [
      escapeCsvField(isVi ? 'STT' : 'No.'),
      escapeCsvField(isVi ? 'Mã sinh viên' : 'Student ID'),
      escapeCsvField(isVi ? 'Họ và tên' : 'Full Name'),
      escapeCsvField(isVi ? 'Email' : 'Email'),
      escapeCsvField(isVi ? 'Điểm quá trình (50%)' : 'Process Score (50%)'),
      escapeCsvField(isVi ? 'Điểm thi kết thúc (50%)' : 'Final Exam Score (50%)'),
      escapeCsvField(isVi ? 'Tổng kết (Hệ 10)' : 'Total (Scale 10)'),
      escapeCsvField(isVi ? 'Điểm chữ' : 'Letter Grade'),
      escapeCsvField(isVi ? 'Trạng thái' : 'Status'),
    ].join(','),
  );

  // 5. Student Rows
  students.forEach((student, index) => {
    const total = student.totalScore !== undefined && student.totalScore !== null
      ? student.totalScore
      : calculateTotalScore(student.processScore, student.finalExamScore);

    const letter = student.letterGrade || (total !== null ? calculateGrade(total) : '');
    const statusText = student.gradeStatus === 'PUBLISHED'
      ? (isVi ? 'Đã công bố' : 'Published')
      : (isVi ? 'Bản nháp' : 'Draft');

    rows.push(
      [
        escapeCsvField(index + 1),
        escapeCsvField(student.studentCode || student.studentId),
        escapeCsvField(student.studentName),
        escapeCsvField(student.email || ''),
        escapeCsvField(student.processScore !== null ? student.processScore.toFixed(1) : ''),
        escapeCsvField(student.finalExamScore !== null ? student.finalExamScore.toFixed(1) : ''),
        escapeCsvField(total !== null ? total.toFixed(2) : ''),
        escapeCsvField(letter),
        escapeCsvField(statusText),
      ].join(','),
    );
  });

  // Prepend UTF-8 BOM (\uFEFF) for Microsoft Excel on Windows
  return '\uFEFF' + rows.join('\r\n') + '\r\n';
}

/**
 * Triggers a browser download of a CSV file.
 */
export function downloadCsvFile(filename: string, csvContent: string): void {
  if (typeof window === 'undefined') return;
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename.endsWith('.csv') ? filename : `${filename}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
