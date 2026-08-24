export type RegistrationRoundStatus =
  | 'DRAFT'
  | 'OPEN'
  | 'REGISTRATION_OPEN'
  | 'ADD_DROP_OPEN'
  | 'CLOSED'
  | 'ARCHIVED'
  | string;

export interface RegistrationRound {
  id: string;
  semesterId?: string | null;
  semester?: { id?: string; name?: string; nameVi?: string; nameEn?: string } | null;
  semesterName?: string | null;
  status: RegistrationRoundStatus;
  registrationStart?: string | null;
  registrationEnd?: string | null;
  addDropStart?: string | null;
  addDropEnd?: string | null;
  serverNow?: string | null;
  institutionTimeZone?: string | null;
  maxCredits?: number | null;
  version?: number | null;
  eligibilityState?: 'ELIGIBLE' | 'INELIGIBLE' | 'PENDING' | string;
  priorityRank?: number | null;
  reasonCode?: string | null;
}

export interface RegistrationSlot {
  id?: string;
  dayOfWeek: number | string;
  startTime: string;
  endTime: string;
  classroom?: string | null;
  building?: string | null;
  roomNumber?: string | null;
}

export interface RegistrationSection {
  id: string;
  courseId?: string;
  courseCode: string;
  courseName: string;
  courseNameEn?: string | null;
  courseNameVi?: string | null;
  credits: number;
  department?: string | null;
  departmentCode?: string | null;
  sectionNumber: string;
  lecturer?: string | null;
  classroom?: string | null;
  schedules: RegistrationSlot[];
  capacity: number;
  enrolledCount: number;
  remainingSeats: number;
  status: string;
  prerequisiteState?: 'MET' | 'NOT_MET' | 'UNKNOWN' | string;
  corequisiteState?: 'MET' | 'NOT_MET' | 'UNKNOWN' | string;
  selected?: boolean;
  eligibilityViolations?: RegistrationViolation[];
}

export interface RegistrationViolation {
  code: string;
  message: string;
  sectionId?: string;
  conflictsWithSectionId?: string;
}

export interface RegistrationEligibility {
  state: 'ELIGIBLE' | 'INELIGIBLE' | 'PENDING' | string;
  priorityRank?: number | null;
  reasonCode?: string | null;
  message?: string | null;
}

export interface RegistrationSummary {
  maxCredits: number;
  selectedCredits: number;
  enrolledCredits?: number;
  enrollmentCount?: number;
}

export interface RegistrationEnrollment {
  id: string;
  sectionId: string;
  status: string;
  courseCode?: string;
  courseName?: string;
  credits?: number;
  sectionNumber?: string;
}

export interface RegistrationPage<T> {
  items: T[];
  nextCursor?: string;
}
