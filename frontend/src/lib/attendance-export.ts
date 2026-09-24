import type { AttendanceStatus } from './api';

export interface AttendanceExportStudent {
  studentId: string;
  studentCode: string;
  studentName: string;
  status: AttendanceStatus | null;
  notes?: string;
}

export interface AttendanceExportMetrics {
  total: number;
  present: number;
  absent: number;
  late: number;
  excused: number;
  rate: number;
}

export interface AttendanceCsvOptions {
  sectionNumber: string;
  courseCode?: string;
  courseName?: string;
  date: string;
  lecturerName?: string;
  semesterName?: string;
  metrics: AttendanceExportMetrics;
  students: AttendanceExportStudent[];
  locale?: 'vi' | 'en';
}

const STATUS_LABELS: Record<'vi' | 'en', Record<string, string>> = {
  vi: {
    PRESENT: 'Có mặt',
    ABSENT: 'Vắng',
    LATE: 'Đi trễ',
    EXCUSED: 'Có phép',
    UNMARKED: 'Chưa điểm danh',
  },
  en: {
    PRESENT: 'Present',
    ABSENT: 'Absent',
    LATE: 'Late',
    EXCUSED: 'Excused',
    UNMARKED: 'Unmarked',
  },
};

/**
 * Escapes a cell value for RFC 4180 CSV standard compliance.
 * Wraps values in quotes and escapes internal double-quotes by doubling them.
 */
export function escapeCsvField(value: unknown): string {
  if (value === null || value === undefined) return '""';
  const str = String(value);
  return `"${str.replace(/"/g, '""')}"`;
}

/**
 * Generates an institutional attendance report in CSV format with UTF-8 BOM
 * for direct compatibility with Microsoft Excel on Windows.
 */
export function generateAttendanceCsv(options: AttendanceCsvOptions): string {
  const {
    sectionNumber,
    courseCode = '',
    courseName = '',
    date,
    lecturerName = '',
    semesterName = '',
    metrics,
    students,
    locale = 'vi',
  } = options;

  const labels = STATUS_LABELS[locale] || STATUS_LABELS.vi;
  const isVi = locale === 'vi';

  const rows: string[] = [];

  // 1. Institutional header
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
        ? 'BẢNG ĐIỂM DANH LỚP HỌC PHẦN'
        : 'COURSE SECTION ATTENDANCE ROSTER',
    ),
  );
  rows.push('');

  // 2. Metadata block
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
      escapeCsvField(isVi ? 'Ngày điểm danh:' : 'Date:'),
      escapeCsvField(date),
      escapeCsvField(isVi ? 'Học kỳ:' : 'Semester:'),
      escapeCsvField(semesterName || (isVi ? 'Học kỳ hiện tại' : 'Current term')),
    ].join(','),
  );

  if (lecturerName) {
    rows.push(
      [
        escapeCsvField(isVi ? 'Giảng viên phụ trách:' : 'Instructor:'),
        escapeCsvField(lecturerName),
      ].join(','),
    );
  }

  // 3. Statistics summary block
  rows.push(
    [
      escapeCsvField(isVi ? 'Tổng sĩ số:' : 'Total:'),
      escapeCsvField(metrics.total),
      escapeCsvField(isVi ? 'Có mặt:' : 'Present:'),
      escapeCsvField(metrics.present),
      escapeCsvField(isVi ? 'Vắng:' : 'Absent:'),
      escapeCsvField(metrics.absent),
      escapeCsvField(isVi ? 'Đi trễ:' : 'Late:'),
      escapeCsvField(metrics.late),
      escapeCsvField(isVi ? 'Có phép:' : 'Excused:'),
      escapeCsvField(metrics.excused),
      escapeCsvField(isVi ? 'Tỷ lệ chuyên cần:' : 'Attendance Rate:'),
      escapeCsvField(`${metrics.rate.toFixed(1)}%`),
    ].join(','),
  );
  rows.push('');

  // 4. Student Roster Table Header
  rows.push(
    [
      escapeCsvField(isVi ? 'STT' : 'No.'),
      escapeCsvField(isVi ? 'Mã sinh viên' : 'Student ID'),
      escapeCsvField(isVi ? 'Họ và tên' : 'Full Name'),
      escapeCsvField(isVi ? 'Trạng thái điểm danh' : 'Attendance Status'),
      escapeCsvField(isVi ? 'Ghi chú' : 'Notes'),
    ].join(','),
  );

  // 5. Student Roster Rows
  students.forEach((student, index) => {
    const statusText = student.status ? labels[student.status] || student.status : labels.UNMARKED;
    rows.push(
      [
        escapeCsvField(index + 1),
        escapeCsvField(student.studentCode || student.studentId),
        escapeCsvField(student.studentName),
        escapeCsvField(statusText),
        escapeCsvField(student.notes || ''),
      ].join(','),
    );
  });

  // Prepend UTF-8 BOM (\uFEFF) for Excel on Windows to read Vietnamese characters accurately
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
