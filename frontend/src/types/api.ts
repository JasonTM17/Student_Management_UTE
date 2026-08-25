// API Response Types
export interface ApiResponse<T> {
  data: T;
  meta?: {
    total: number;
    page: number;
    limit: number;
    totalPages: number;
  };
}

// User & Auth Types
export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  gender?: 'MALE' | 'FEMALE' | 'OTHER';
  dateOfBirth?: string;
  address?: string;
  status: string;
  emailVerified?: boolean;
  role?: 'ADMIN' | 'SUPER_ADMIN' | 'LECTURER' | 'STUDENT';
  roles?: string[];
  createdAt: string;
  // Linked identities from JWT
  studentId?: string | null;
  lecturerId?: string | null;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export interface LoginResponse {
  user: User;
  accessToken: string;
  refreshToken: string;
}

export interface RegistrationPendingResponse {
  email: string;
  verificationRequired: boolean;
  expiresInSeconds: number;
  resendAfterSeconds: number;
}

export interface AuthActionResponse {
  message: string;
}

// Student Types
export interface Student {
  id: string;
  userId: string;
  studentId: string;
  curriculumId: string;
  year: number;
  status: string;
  admissionDate: string;
  createdAt: string;
  user?: User;
  curriculum?: Curriculum;
}

// Course & Section Types
export interface Course {
  id: string;
  code: string;
  name: string;
  nameEn?: string;
  nameVi?: string;
  description?: string;
  descriptionEn?: string;
  descriptionVi?: string;
  credits: number;
  departmentId: string;
  isActive: boolean;
  createdAt: string;
  department?: Department;
}

export interface Section {
  id: string;
  sectionNumber: string;
  courseId: string;
  semesterId: string;
  lecturerId?: string;
  classroomId?: string;
  capacity: number;
  enrolledCount: number;
  status: 'OPEN' | 'CLOSED' | 'CANCELLED';
  maxCredits?: number;
  createdAt: string;
  course?: Course & { department?: Department };
  semester?: Semester;
  lecturer?: Lecturer;
  classroom?: Classroom;
  schedules?: SectionSchedule[];
}

export interface SectionSchedule {
  id: string;
  sectionId: string;
  classroomId: string;
  dayOfWeek: number; // 0=Sunday, 1=Monday, etc.
  startTime: string; // HH:mm format
  endTime: string; // HH:mm format
  classroom?: Classroom;
}

export interface Classroom {
  id: string;
  building: string;
  roomNumber: string;
  capacity: number;
  type: string;
  isActive: boolean;
}

// Enrollment Types
export interface Enrollment {
  id: string;
  studentId: string;
  sectionId: string;
  semesterId: string;
  status: 'ENROLLED' | 'PENDING' | 'CONFIRMED' | 'DROPPED' | 'COMPLETED' | 'CANCELLED';
  enrolledAt: string;
  droppedAt?: string;
  gradeStatus: 'DRAFT' | 'PUBLISHED' | 'APPEALED';
  finalGrade?: number;
  letterGrade?: string;
  createdAt: string;
  student?: Student;
  section?: Section;
  semester?: Semester;
}

export type EnrollmentActionResult = Enrollment;

// Academic Types
export interface Semester {
  id: string;
  name: string;
  nameEn?: string;
  nameVi?: string;
  type: string;
  academicYearId: string;
  startDate: string;
  endDate: string;
  registrationStart?: string;
  registrationEnd?: string;
  addDropStart?: string;
  addDropEnd?: string;
  status: 'DRAFT' | 'ACTIVE' | 'REGISTRATION_OPEN' | 'ADD_DROP_OPEN' | 'IN_PROGRESS' | 'CLOSED' | 'ARCHIVED';
  createdAt: string;
  academicYear?: AcademicYear;
}

export interface AcademicYear {
  id: string;
  year: number;
  startDate: string;
  endDate: string;
  isCurrent: boolean;
  createdAt: string;
}

export interface Department {
  id: string;
  name: string;
  nameEn?: string;
  nameVi?: string;
  code: string;
  description?: string;
  descriptionEn?: string;
  descriptionVi?: string;
  facultyId: string;
  isActive: boolean;
  createdAt: string;
  faculty?: Faculty;
}

export interface Faculty {
  id: string;
  name: string;
  nameEn?: string;
  nameVi?: string;
  code: string;
  description?: string;
  descriptionEn?: string;
  descriptionVi?: string;
  isActive: boolean;
  createdAt: string;
}

export interface Curriculum {
  id: string;
  name: string;
  nameEn?: string;
  nameVi?: string;
  code: string;
  departmentId: string;
  academicYearId: string;
  totalCredits: number;
  description?: string;
  descriptionEn?: string;
  descriptionVi?: string;
  isActive: boolean;
  createdAt: string;
}

export interface Lecturer {
  id: string;
  userId: string;
  departmentId: string;
  employeeId: string;
  title?: string;
  specialization?: string;
  office?: string;
  phone?: string;
  isActive: boolean;
  createdAt: string;
  user?: User;
  department?: Department;
}

// Grade Types
export interface StudentGradeRecord {
  id: string;
  courseCode: string;
  courseName: string;
  courseNameEn?: string;
  courseNameVi?: string;
  credits: number;
  sectionCode: string;
  lecturerName: string | null;
  semester: string;
  semesterNameEn?: string;
  semesterNameVi?: string;
  semesterId: string;
  finalGrade: number | null;
  letterGrade: string | null;
  gradePoint?: number | null;
  gradeStatus: 'DRAFT' | 'PUBLISHED' | 'APPEALED';
  enrollmentStatus: 'PENDING' | 'CONFIRMED' | 'DROPPED' | 'COMPLETED' | 'CANCELLED';
}

export interface StudentTranscriptSemester {
  semesterId: string;
  semesterName: string;
  semesterNameEn?: string;
  semesterNameVi?: string;
  records: StudentGradeRecord[];
  gpa: number;
  creditsEarned: number;
  creditsAttempted: number;
}

export interface StudentTranscriptSummary {
  cumulativeGpa: number;
  totalCreditsEarned: number;
  totalCreditsAttempted: number;
}

export interface StudentTranscript {
  summary: StudentTranscriptSummary;
  semesters: StudentTranscriptSemester[];
}

export interface StudentGrade {
  id: string;
  enrollmentId: string;
  gradeItemId: string;
  score?: number;
  letterGrade?: string;
}

export interface GradeUpdate {
  enrollmentId: string;
  finalGrade: number;
  letterGrade: string;
}

export interface GradingSection {
  id: string;
  sectionId: string;
  sectionNumber: string;
  courseCode: string;
  courseName: string;
  courseNameEn?: string;
  courseNameVi?: string;
  credits: number;
  departmentName: string;
  departmentNameEn?: string;
  departmentNameVi?: string;
  semester: string;
  semesterName?: string;
  semesterNameEn?: string;
  semesterNameVi?: string;
  enrolledCount: number;
  gradedCount: number;
  publishedCount: number;
  gradeStatus: 'NONE' | 'PARTIAL' | 'ALL_GRADED';
  canPublish: boolean;
}

export interface LecturerSection {
  id: string;
  sectionId: string;
  sectionNumber: string;
  courseCode: string;
  courseName: string;
  courseNameEn?: string;
  courseNameVi?: string;
  credits: number;
  capacity: number;
  enrolledCount: number;
  departmentName: string;
  departmentNameEn?: string;
  departmentNameVi?: string;
  status: 'OPEN' | 'CLOSED' | 'CANCELLED';
  schedules: {
    id: string;
    dayOfWeek: number;
    startTime: string;
    endTime: string;
    building: string;
    roomNumber: string;
  }[];
}

export type TranscriptResponse = StudentTranscript;

export interface SectionGrades {
  sectionId: string;
  sectionNumber: string;
  courseCode: string;
  courseName: string;
  courseNameEn?: string;
  courseNameVi?: string;
  lecturerName?: string;
  status?: 'OPEN' | 'CLOSED' | 'CANCELLED';
  credits?: number;
  departmentName?: string;
  departmentNameEn?: string;
  departmentNameVi?: string;
  semester?: string;
  semesterNameEn?: string;
  semesterNameVi?: string;
  enrollments: {
    id: string;
    studentId: string;
    studentName: string;
    studentCode: string;
    email?: string;
    midtermScore?: number;
    finalScore?: number;
    finalGrade?: number;
    letterGrade?: string;
    gradeStatus?: 'DRAFT' | 'PUBLISHED' | 'APPEALED';
    enrollmentStatus?: 'PENDING' | 'CONFIRMED' | 'DROPPED' | 'COMPLETED' | 'CANCELLED';
  }[];
}
