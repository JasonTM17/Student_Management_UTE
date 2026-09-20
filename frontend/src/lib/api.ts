import axios, {
  AxiosError,
  AxiosRequestConfig,
  InternalAxiosRequestConfig,
} from 'axios';
import {
  LoginResponse,
  ApiResponse,
  User,
  Section,
  Enrollment,
  EnrollmentActionResult,
  Semester,
  Department,
  Course,
  StudentGradeRecord,
  StudentTranscript,
  TranscriptResponse,
  AcademicYear,
  Classroom,
  Curriculum,
  Lecturer,
  GradingSection,
  LecturerSection,
  SectionGrades,
  SectionSchedule,
  MyCurriculumResponse,
  StudentGradesByEnrollmentResponse,
} from '@/types/api';
import { addLocalePrefix, stripLocaleFromPathname } from '@/i18n/paths';
import { resolvePublicApiBaseUrl } from '@/lib/public-api-url';
import { CSRF_COOKIE_NAME } from '@/lib/session-hint';

export const API_BASE_URL = resolvePublicApiBaseUrl(process.env.NEXT_PUBLIC_API_URL);

// crypto.randomUUID only exists in secure contexts (HTTPS/localhost). The
// fallback must still be an RFC 4122 UUID because Spring validates the
// assistant idempotency contract before it can process the request.
export function createRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  if (typeof globalThis.crypto?.getRandomValues === 'function') {
    globalThis.crypto.getRandomValues(bytes);
  } else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * 256);
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
type ApiObject = Record<string, unknown>;
type AuthRequestConfig = AxiosRequestConfig & {
  skipAuthRefresh?: boolean;
  skipAuthRedirect?: boolean;
  _retry?: boolean;
  _retryNoCache?: boolean;
  _coldStartRetry?: boolean;
};
type AuthInternalRequestConfig = InternalAxiosRequestConfig & {
  skipAuthRefresh?: boolean;
  skipAuthRedirect?: boolean;
  _retry?: boolean;
  _retryNoCache?: boolean;
  _coldStartRetry?: boolean;
};
export type AnnouncementPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
export type AnnouncementAudienceRole =
  | 'STUDENT'
  | 'LECTURER'
  | 'ADMIN'
  | 'SUPER_ADMIN';

export type AnnouncementRecord = {
  id: string;
  title: string;
  content: string;
  priority: AnnouncementPriority | string;
  createdAt: string;
  updatedAt?: string;
  publishAt?: string | null;
  expiresAt?: string | null;
  version?: number;
  archivedAt?: string | null;
  archivedBy?: string | null;
  publishedBy?: string | null;
  targetRoles?: string[];
  targetYears?: number[];
  isGlobal?: boolean;
  semesterId?: string | null;
  sectionId?: string | null;
  sectionNumber?: string | null;
  courseCode?: string | null;
  courseName?: string | null;
  lecturerId?: string | null;
  lecturerDisplayName?: string | null;
  semesterName?: string | null;
  semester?: { name: string; nameEn?: string; nameVi?: string } | null;
  section?: {
    sectionNumber?: string;
    course?: { code?: string; name?: string; nameEn?: string; nameVi?: string };
  } | null;
  lecturer?: { id?: string; displayName?: string } | null;
  categoryId?: string | null;
  status?: string;
  coverImageUrl?: string | null;
  summary?: string | null;
  readingTimeMinutes?: number;
  viewCount?: number;
  uniqueReaderCount?: number;
  slug?: string | null;
  featuredOrder?: number | null;
  displayOrder?: number | null;
  tags?: Array<{ id: string; slug: string; nameVi: string; nameEn: string }>;
  attachments?: Array<{
    id: string;
    fileName: string;
    fileUrl: string;
    fileSizeBytes: number;
    mimeType: string;
    checksumSha256?: string;
    downloadCount?: number;
  }>;
  gallery?: Array<{
    id: string;
    mediaUrl: string;
    captionVi: string;
    captionEn?: string;
    altText: string;
    isCover?: boolean;
  }>;
};


export type CreditLimitApplication = {
  id: string;
  studentId: string;
  studentCode: string;
  studentName: string;
  studentEmail: string;
  semesterId: string;
  semesterName: string;
  roundId: string;
  roundName: string;
  standardLimit: number;
  requestedLimit: number;
  reason: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  reviewerNote?: string | null;
  createdAt: string;
  updatedAt: string;
};
export type AnnouncementMutation = Partial<Pick<AnnouncementRecord,
  'title' | 'content' | 'priority' | 'targetRoles' | 'targetYears' | 'isGlobal' |
  'publishAt' | 'expiresAt' | 'semesterId' | 'sectionId' | 'lecturerId'>> & {
  reason?: string;
  expectedVersion?: number;
};
export type AnnouncementHistoryRecord = {
  id: string;
  announcementId: string;
  action: string;
  actorId?: string;
  actorLabel?: string | null;
  reason?: string;
  version: number;
  createdAt: string;
  before?: Record<string, unknown> | null;
  after?: Record<string, unknown> | null;
};
type NotificationRecord = {
  id: string;
  title?: string;
  content?: string;
  message?: string;
  isRead: boolean;
  createdAt: string;
};
type SectionDetail = Section & {
  schedules?: Array<
    Pick<SectionSchedule, 'dayOfWeek' | 'startTime' | 'endTime'> & {
      classroom?: { id?: string } | null;
    }
  >;
};

const CSRF_HEADER_NAME = 'X-CSRF-Token';
const AUTH_REFRESH_ROUTE_PATTERN = /^\/auth\/(login|refresh|logout)(?:\/|$)/;
const MUTATING_METHODS = new Set(['post', 'put', 'patch', 'delete']);

function isBrowser() {
  return typeof window !== 'undefined' && typeof document !== 'undefined';
}

function redirectToLogin(reason: 'session-expired' | 'unauthorized') {
  if (!isBrowser()) {
    return;
  }

  const { locale, pathname } = stripLocaleFromPathname(window.location.pathname);
  const loginPath = locale ? addLocalePrefix('/login', locale) : '/login';
  const loginUrl = new URL(loginPath, window.location.origin);
  loginUrl.searchParams.set('reason', reason);
  if (pathname.startsWith('/admin')) {
    loginUrl.searchParams.set('portal', 'admin');
  } else if (pathname.startsWith('/dashboard/lecturer')) {
    loginUrl.searchParams.set('portal', 'lecturer');
  } else {
    loginUrl.searchParams.set('portal', 'student');
  }
  window.location.href = loginUrl.toString();
}

function getCookie(name: string): string | undefined {
  if (!isBrowser()) {
    return undefined;
  }

  const escapedName = name.replace(/([.*+?^${}()|[\]\\])/g, '\\$1');
  const match = document.cookie.match(
    new RegExp(`(?:^|; )${escapedName}=([^;]*)`),
  );

  return match ? decodeURIComponent(match[1]) : undefined;
}

function isMutatingRequest(config?: AxiosRequestConfig) {
  const method = (config?.method ?? 'get').toLowerCase();
  return MUTATING_METHODS.has(method);
}

function getRequestPath(config?: AxiosRequestConfig) {
  const url = config?.url ?? '';
  const baseURL = config?.baseURL ?? API_BASE_URL;

  try {
    return new URL(url, baseURL || (isBrowser() ? window.location.origin : '')).pathname;
  } catch {
    return url;
  }
}

function shouldAttemptSessionRefresh(config?: AuthRequestConfig) {
  if (!config || config.skipAuthRefresh) {
    return false;
  }

  return !AUTH_REFRESH_ROUTE_PATTERN.test(getRequestPath(config));
}

function isSafeRequest(config?: AxiosRequestConfig) {
  const method = (config?.method ?? 'get').toLowerCase();
  return method === 'get' || method === 'head';
}

function appendNoCacheParam(config: AuthRequestConfig) {
  const cacheBustKey = '_cc_nocache';
  const cacheBustValue = `${Date.now()}-${Math.random()
    .toString(36)
    .slice(2, 8)}`;

  if (config.params && typeof config.params === 'object') {
    config.params = {
      ...(config.params as Record<string, unknown>),
      [cacheBustKey]: cacheBustValue,
    };
    return config;
  }

  config.params = { [cacheBustKey]: cacheBustValue };
  return config;
}

function applyCsrfHeader(config: AuthInternalRequestConfig) {
  if (!isBrowser() || !isMutatingRequest(config)) {
    return config;
  }

  const csrfToken = getCookie(CSRF_COOKIE_NAME);
  if (!csrfToken) {
    return config;
  }

  config.headers.set(CSRF_HEADER_NAME, csrfToken);
  return config;
}

const api = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
    'Cache-Control': 'no-store',
    Pragma: 'no-cache',
    Expires: '0',
  },
});

// All browser transports share one refresh promise. This prevents concurrent
// SSE/JSON requests from rotating the refresh token independently and racing
// before the first stream byte is received.
let sessionRefreshPromise: Promise<LoginResponse> | null = null;

export function refreshSessionSingleFlight(): Promise<LoginResponse> {
  if (sessionRefreshPromise) return sessionRefreshPromise;
  sessionRefreshPromise = api
    .post<LoginResponse>(
      '/auth/refresh',
      {},
      { skipAuthRefresh: true, skipAuthRedirect: true } as AuthRequestConfig,
    )
    .then((response) => response.data)
    .finally(() => {
      sessionRefreshPromise = null;
    });
  return sessionRefreshPromise;
}

api.interceptors.request.use((config) => {
  // Let the browser adapter add the multipart boundary for file uploads. The
  // instance-wide JSON default otherwise makes Spring reject FormData before
  // the controller can validate or store the document.
  if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
    config.headers.delete('Content-Type');
  }
  return applyCsrfHeader(config as AuthInternalRequestConfig);
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalConfig = error.config as AuthRequestConfig | undefined;
    const unauthorized = error.response?.status === 401;
    const notModified = error.response?.status === 304;

    // Render's free tier sleeps and answers the first probe with a gateway
    // error or nothing at all. One bounded retry after a short backoff wakes
    // it without surfacing a spurious failure to idempotent reads.
    const backendWarming =
      !error.response || [502, 503, 504].includes(error.response.status ?? 0);
    if (
      backendWarming &&
      originalConfig &&
      isSafeRequest(originalConfig) &&
      !originalConfig._coldStartRetry
    ) {
      originalConfig._coldStartRetry = true;
      await new Promise((resolve) => setTimeout(resolve, 3000));
      return api(originalConfig);
    }

    if (notModified && originalConfig && isSafeRequest(originalConfig) && !originalConfig._retryNoCache) {
      originalConfig._retryNoCache = true;
      return api(appendNoCacheParam(originalConfig));
    }

    if (unauthorized && originalConfig && shouldAttemptSessionRefresh(originalConfig) && !originalConfig._retry) {
      originalConfig._retry = true;

      try {
        await refreshSessionSingleFlight();

        return api(originalConfig);
      } catch (refreshError) {
        if (isBrowser() && !originalConfig.skipAuthRedirect) {
          redirectToLogin('session-expired');
        }

        return Promise.reject(refreshError);
      }
    }

    if (unauthorized && isBrowser() && originalConfig && !originalConfig.skipAuthRedirect) {
      redirectToLogin('unauthorized');
    }

    return Promise.reject(error);
  },
);

// Auth API
export const authApi = {
  login: async (email: string, password: string): Promise<LoginResponse> => {
    const response = await api.post<LoginResponse>(
      '/auth/login',
      {
        email,
        password,
      },
      {
        skipAuthRefresh: true,
        skipAuthRedirect: true,
      } as AuthRequestConfig,
    );
    return response.data;
  },

  me: async (): Promise<User> => {
    const response = await api.get<User>('/auth/me', {
      skipAuthRedirect: true,
      skipAuthRefresh: true,
    } as AuthRequestConfig);
    return response.data;
  },

  // The full name is school-managed, so it is not part of this payload; the
  // server ignores it if an older client still sends it.
  updateProfile: async (data: {
    phone?: string;
    dateOfBirth?: string;
    address?: string;
    avatar?: string;
  }): Promise<User> => {
    const response = await api.put<User>('/auth/profile', data);
    return response.data;
  },

  logout: async (): Promise<void> => {
    await api.post(
      '/auth/logout',
      {},
      {
        skipAuthRefresh: true,
        skipAuthRedirect: true,
      } as AuthRequestConfig,
    );
  },

  refresh: async (): Promise<LoginResponse> => {
    return refreshSessionSingleFlight();
  },

  changePassword: async (
    oldPassword: string,
    newPassword: string,
  ): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>(
      '/auth/change-password',
      { oldPassword, newPassword },
    );
    return response.data;
  },

};

// Sections API
export const sectionsApi = {
  getAll: async (params?: {
    page?: number;
    limit?: number;
    semesterId?: string;
    departmentId?: string;
    courseId?: string;
  }): Promise<ApiResponse<Section[]>> => {
    const response = await api.get<ApiResponse<Section[]>>('/sections', {
      params,
    });
    return response.data;
  },

  getById: async (id: string): Promise<Section> => {
    const response = await api.get<Section>(`/sections/${id}`);
    return response.data;
  },

  getSectionGrades: async (sectionId: string): Promise<any> => {
    const response = await api.get<SectionGrades>(
      `/sections/${sectionId}/grades`,
    );
    return response.data;
  },

  getMySchedule: async (semesterId?: string): Promise<LecturerSection[]> => {
    const response = await api.get<LecturerSection[]>('/sections/my/schedule', {
      params: { semesterId },
    });
    return response.data;
  },

  getMyGradingSections: async (
    semesterId?: string,
  ): Promise<GradingSection[]> => {
    const response = await api.get<GradingSection[]>('/sections/my/grading', {
      params: { semesterId },
    });
    return response.data;
  },

  updateSectionGrades: async (
    sectionId: string,
    grades: { enrollmentId: string; processScore: number; finalExamScore: number }[],
  ): Promise<{ message: string }> => {
    const response = await api.put<{ message: string }>(
      `/sections/${sectionId}/grades`,
      { grades },
    );
    return response.data;
  },

  publishSectionGrades: async (
    sectionId: string,
  ): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>(
      `/sections/${sectionId}/grades/publish`,
    );
    return response.data;
  },
};

// Enrollments API
export const registrationApi = {
  rounds: async (semesterId?: string) => {
    const response = await api.get<Array<{
      id: string;
      semesterId: string;
      name: string;
      kind: string;
      status: string;
      windowStart: string;
      windowEnd: string;
      creditLimit: number;
    }>>('/registration/rounds', { params: { semesterId } });
    return response.data;
  },
  eligibility: async (params?: { semesterId?: string; roundId?: string }) => {
    const response = await api.get<{
      roundId: string;
      semesterId: string;
      kind: string;
      eligible: boolean;
      creditLimit: number;
      creditsUsed: number;
      creditsRemaining: number;
      windowStart: string;
      windowEnd: string;
    }>('/me/registration/eligibility', { params });
    return response.data;
  },
  sections: async (params?: { semesterId?: string; roundId?: string }) => {
    const response = await api.get<Array<{
      id: string;
      sectionNumber: string;
      courseId: string;
      courseCode: string;
      courseName: string;
      credits: number;
      capacity: number;
      enrolledCount: number;
      remainingSeats: number;
      status: string;
      scheduleConflict: boolean;
      alreadyEnrolled: boolean;
    }>>('/me/registration/sections', { params });
    return response.data;
  },
  summary: async (semesterId?: string) => {
    const response = await api.get<{
      roundId: string;
      creditLimit: number;
      creditsUsed: number;
      creditsRemaining: number;
      enrollmentIds: string[];
    }>('/me/registration/summary', { params: { semesterId } });
    return response.data;
  },
  creditLimitApplication: async (roundId: string): Promise<CreditLimitApplication | null> => {
    const response = await api.get<CreditLimitApplication>(
      '/me/registration/credit-limit-application',
      { params: { roundId } },
    );
    return response.status === 204 ? null : response.data;
  },
  submitCreditLimitApplication: async (
    roundId: string,
    reason: string,
  ): Promise<CreditLimitApplication> => {
    const response = await api.post<CreditLimitApplication>(
      '/me/registration/credit-limit-applications',
      { roundId, reason },
    );
    return response.data;
  },
};

export const adminRegistrationApi = {
  creditLimitApplications: async (
    status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'ALL' = 'PENDING',
  ): Promise<CreditLimitApplication[]> => {
    const response = await api.get<CreditLimitApplication[]>(
      '/admin/registration/credit-limit-applications',
      { params: { status } },
    );
    return response.data;
  },
  reviewCreditLimitApplication: async (
    id: string,
    decision: 'APPROVED' | 'REJECTED',
    note?: string,
  ): Promise<CreditLimitApplication> => {
    const response = await api.post<CreditLimitApplication>(
      `/admin/registration/credit-limit-applications/${id}/review`,
      { decision, note },
    );
    return response.data;
  },
};

export const enrollmentsApi = {
  enroll: async (
    sectionId: string,
    locale?: 'en' | 'vi',
  ): Promise<EnrollmentActionResult> => {
    const response = await api.post<EnrollmentActionResult>(
      '/me/enrollments',
      { sectionId, locale },
      { headers: { 'Idempotency-Key': createRequestId() } },
    );
    return response.data;
  },

  drop: async (enrollmentId: string): Promise<{ message: string }> => {
    const response = await api.post<{ message: string }>(
      `/me/enrollments/${enrollmentId}/drop`,
      {},
      { headers: { 'Idempotency-Key': createRequestId() } },
    );
    return response.data;
  },

  getMyEnrollments: async (semesterId?: string): Promise<Enrollment[]> => {
    const response = await api.get<Enrollment[]>('/enrollments/my', {
      params: { semesterId },
    });
    return response.data;
  },

  getAll: async (params?: {
    page?: number;
    limit?: number;
    status?: string;
    semesterId?: string;
    studentId?: string;
    courseId?: string;
    sectionId?: string;
  }): Promise<ApiResponse<Enrollment[]>> => {
    const response = await api.get<ApiResponse<Enrollment[]>>('/enrollments', {
      params,
    });
    return response.data;
  },

  getById: async (id: string): Promise<Enrollment> => {
    const response = await api.get<Enrollment>(`/enrollments/${id}`);
    return response.data;
  },

  delete: async (id: string): Promise<{ message: string }> => {
    const response = await api.delete<{ message: string }>(
      `/enrollments/${id}`,
    );
    return response.data;
  },

  exportCsv: async (params?: {
    status?: string;
    semesterId?: string;
    studentId?: string;
    courseId?: string;
  }): Promise<string> => {
    const response = await api.get<string>('/enrollments/export/csv', {
      params,
    });
    return response.data;
  },
};

// Semesters API
export const semestersApi = {
  getAll: async (): Promise<ApiResponse<Semester[]>> => {
    const response = await api.get<ApiResponse<Semester[]>>('/semesters');
    return response.data;
  },
};

// Departments API
export const departmentsApi = {
  getAll: async (params?: {
    page?: number;
    limit?: number;
  }): Promise<ApiResponse<Department[]>> => {
    const response = await api.get<ApiResponse<Department[]>>('/departments', {
      params,
    });
    return response.data;
  },
  getById: async (id: string): Promise<Department> => {
    const response = await api.get<Department>(`/departments/${id}`);
    return response.data;
  },
  create: async (data: Partial<Department>): Promise<Department> => {
    const response = await api.post<Department>('/departments', data);
    return response.data;
  },
  update: async (
    id: string,
    data: Partial<Department>,
  ): Promise<Department> => {
    const response = await api.put<Department>(`/departments/${id}`, data);
    return response.data;
  },
  delete: async (id: string): Promise<{ message: string }> => {
    const response = await api.delete<{ message: string }>(
      `/departments/${id}`,
    );
    return response.data;
  },
};

// Courses API
export const coursesApi = {
  getAll: async (params?: {
    page?: number;
    limit?: number;
    departmentId?: string;
  }): Promise<ApiResponse<Course[]>> => {
    const response = await api.get<ApiResponse<Course[]>>('/courses', {
      params,
    });
    return response.data;
  },
  getById: async (id: string): Promise<Course> => {
    const response = await api.get<Course>(`/courses/${id}`);
    return response.data;
  },
  create: async (data: Partial<Course>): Promise<Course> => {
    const response = await api.post<Course>('/courses', data);
    return response.data;
  },
  update: async (id: string, data: Partial<Course>): Promise<Course> => {
    const response = await api.put<Course>(`/courses/${id}`, data);
    return response.data;
  },
  delete: async (id: string): Promise<{ message: string }> => {
    const response = await api.delete<{ message: string }>(`/courses/${id}`);
    return response.data;
  },
};

// Grades API
export const gradesApi = {
  getMyGrades: async (semesterId?: string): Promise<StudentGradeRecord[]> => {
    const response = await api.get<StudentGradeRecord[]>(
      '/enrollments/my/grades',
      { params: { semesterId } },
    );
    return response.data;
  },

  getMyTranscript: async (semesterId?: string): Promise<StudentTranscript> => {
    const response = await api.get<StudentTranscript>(
      '/enrollments/my/transcript',
      { params: { semesterId } },
    );
    return response.data;
  },

  getStudentGradesByEnrollment: async (
    enrollmentId: string,
  ): Promise<StudentGradesByEnrollmentResponse> => {
    const response = await api.get<StudentGradesByEnrollmentResponse>(
      `/grades/student-grades/enrollment/${enrollmentId}`,
    );
    return response.data;
  },
};

// Curriculum API
export const curriculumApi = {
  getMyCurriculum: async (): Promise<MyCurriculumResponse> => {
    const response = await api.get<MyCurriculumResponse>('/me/curriculum');
    return response.data;
  },
};

// Admin Users API
export const usersApi = {
  getAll: async (params?: {
    page?: number;
    limit?: number;
    status?: string;
    search?: string;
  }): Promise<ApiResponse<User[]>> => {
    const response = await api.get<ApiResponse<User[]>>('/users', { params });
    return response.data;
  },
  create: async (data: ApiObject): Promise<User & { temporaryPassword?: string }> => {
    const response = await api.post<User & { temporaryPassword?: string }>('/users', data);
    return response.data;
  },
  resetPassword: async (id: string): Promise<User & { temporaryPassword?: string }> => {
    const response = await api.post<User & { temporaryPassword?: string }>(
      `/users/${id}/password-reset`,
    );
    return response.data;
  },
  update: async (id: string, data: ApiObject): Promise<User> => {
    const response = await api.put<User>(`/users/${id}`, data);
    return response.data;
  },
  delete: async (id: string): Promise<{ message: string }> => {
    const response = await api.delete<{ message: string }>(`/users/${id}`);
    return response.data;
  },
};

// Admin Semesters API
export const adminSemestersApi = {
  getAll: async (params?: {
    page?: number;
    limit?: number;
  }): Promise<ApiResponse<Semester[]>> => {
    const response = await api.get<ApiResponse<Semester[]>>('/semesters', {
      params,
    });
    return response.data;
  },
  create: async (data: ApiObject): Promise<Semester> => {
    const response = await api.post<Semester>('/semesters', data);
    return response.data;
  },
  update: async (id: string, data: ApiObject): Promise<Semester> => {
    const response = await api.put<Semester>(`/semesters/${id}`, data);
    return response.data;
  },
  delete: async (id: string): Promise<{ message: string }> => {
    const response = await api.delete<{ message: string }>(`/semesters/${id}`);
    return response.data;
  },
};

// Admin Sections API
export const adminSectionsApi = {
  getAll: async (params?: {
    page?: number;
    limit?: number;
    semesterId?: string;
    departmentId?: string;
    courseId?: string;
  }): Promise<ApiResponse<Section[]>> => {
    const response = await api.get<ApiResponse<Section[]>>('/sections', {
      params,
    });
    return response.data;
  },
  getById: async (id: string): Promise<SectionDetail> => {
    const response = await api.get<SectionDetail>(`/sections/${id}`);
    return response.data;
  },
  create: async (data: ApiObject): Promise<Section> => {
    const response = await api.post<Section>('/sections', data);
    return response.data;
  },
  update: async (id: string, data: ApiObject): Promise<Section> => {
    const response = await api.put<Section>(`/sections/${id}`, data);
    return response.data;
  },
  delete: async (id: string): Promise<{ message: string }> => {
    const response = await api.delete<{ message: string }>(`/sections/${id}`);
    return response.data;
  },
};

// Admin Lecturers API
export const lecturersApi = {
  getAll: async (params?: {
    page?: number;
    limit?: number;
  }): Promise<ApiResponse<Lecturer[]>> => {
    const response = await api.get<ApiResponse<Lecturer[]>>('/lecturers', {
      params,
    });
    return response.data;
  },
  getById: async (id: string): Promise<Lecturer> => {
    const response = await api.get<Lecturer>(`/lecturers/${id}`);
    return response.data;
  },
  create: async (data: ApiObject): Promise<Lecturer> => {
    const response = await api.post<Lecturer>('/lecturers', data);
    return response.data;
  },
  update: async (id: string, data: ApiObject): Promise<Lecturer> => {
    const response = await api.put<Lecturer>(`/lecturers/${id}`, data);
    return response.data;
  },
  delete: async (id: string): Promise<{ message: string }> => {
    const response = await api.delete<{ message: string }>(`/lecturers/${id}`);
    return response.data;
  },
};

// Admin Classrooms API
export const classroomsApi = {
  getAll: async (params?: {
    page?: number;
    limit?: number;
  }): Promise<ApiResponse<Classroom[]>> => {
    const response = await api.get<ApiResponse<Classroom[]>>('/classrooms', {
      params,
    });
    return response.data;
  },
  getById: async (id: string): Promise<Classroom> => {
    const response = await api.get<Classroom>(`/classrooms/${id}`);
    return response.data;
  },
  create: async (data: ApiObject): Promise<Classroom> => {
    const response = await api.post<Classroom>('/classrooms', data);
    return response.data;
  },
  update: async (id: string, data: ApiObject): Promise<Classroom> => {
    const response = await api.put<Classroom>(`/classrooms/${id}`, data);
    return response.data;
  },
  delete: async (id: string): Promise<{ message: string }> => {
    const response = await api.delete<{ message: string }>(`/classrooms/${id}`);
    return response.data;
  },
};

// Admin Academic Years API
export const academicYearsApi = {
  getAll: async (params?: {
    page?: number;
    limit?: number;
  }): Promise<ApiResponse<AcademicYear[]>> => {
    const response = await api.get<ApiResponse<AcademicYear[]>>(
      '/academic-years',
      { params },
    );
    return response.data;
  },
  getById: async (id: string): Promise<AcademicYear> => {
    const response = await api.get<AcademicYear>(`/academic-years/${id}`);
    return response.data;
  },
  create: async (data: ApiObject): Promise<AcademicYear> => {
    const response = await api.post<AcademicYear>('/academic-years', data);
    return response.data;
  },
  update: async (id: string, data: ApiObject): Promise<AcademicYear> => {
    const response = await api.put<AcademicYear>(`/academic-years/${id}`, data);
    return response.data;
  },
  delete: async (id: string): Promise<{ message: string }> => {
    const response = await api.delete<{ message: string }>(
      `/academic-years/${id}`,
    );
    return response.data;
  },
};

// Admin Curricula API
export const curriculaApi = {
  getAll: async (params?: {
    page?: number;
    limit?: number;
  }): Promise<ApiResponse<Curriculum[]>> => {
    const response = await api.get<ApiResponse<Curriculum[]>>('/curricula', {
      params,
    });
    return response.data;
  },
  getById: async (id: string): Promise<Curriculum> => {
    const response = await api.get<Curriculum>(`/curricula/${id}`);
    return response.data;
  },
};

// Announcements API
export const announcementsApi = {
  getMy: async (params?: {
    page?: number;
    limit?: number;
  }): Promise<ApiResponse<AnnouncementRecord[]>> => {
    const response = await api.get<ApiResponse<AnnouncementRecord[]>>(
      '/announcements/my',
      { params },
    );
    return response.data;
  },
  // Admin
  getAll: async (params?: {
    page?: number;
    limit?: number;
    semesterId?: string;
    sectionId?: string;
    priority?: string;
    status?: 'ACTIVE' | 'ARCHIVED' | 'ALL';
  }): Promise<ApiResponse<AnnouncementRecord[]>> => {
    const response = await api.get<ApiResponse<AnnouncementRecord[]>>(
      '/announcements',
      { params },
    );
    return response.data;
  },
  create: async (data: AnnouncementMutation): Promise<AnnouncementRecord> => {
    const response = await api.post<AnnouncementRecord>('/announcements', data);
    return response.data;
  },
  update: async (id: string, data: AnnouncementMutation): Promise<AnnouncementRecord> => {
    const response = await api.put<AnnouncementRecord>(`/announcements/${id}`, data);
    return response.data;
  },
  archive: async (id: string, data: { reason: string; expectedVersion: number }): Promise<AnnouncementRecord> => {
    const response = await api.post<AnnouncementRecord>(`/announcements/${id}/archive`, data);
    return response.data;
  },
  restore: async (id: string, data: { reason: string; expectedVersion: number }): Promise<AnnouncementRecord> => {
    const response = await api.post<AnnouncementRecord>(`/announcements/${id}/restore`, data);
    return response.data;
  },
  history: async (id: string, params?: { page?: number; limit?: number }): Promise<ApiResponse<AnnouncementHistoryRecord[]>> => {
    const response = await api.get<ApiResponse<AnnouncementHistoryRecord[]>>(`/announcements/${id}/history`, { params });
    return response.data;
  },
  delete: async (id: string): Promise<{ message: string }> => {
    const response = await api.delete<{ message: string }>(
      `/announcements/${id}`,
    );
    return response.data;
  },
  getPublic: async (params?: {
    page?: number;
    limit?: number;
  }): Promise<ApiResponse<AnnouncementRecord[]>> => {
    const response = await api.get<ApiResponse<AnnouncementRecord[]>>(
      '/announcements/public',
      { params },
    );
    return response.data;
  },
  updateDisplayOrder: async (
    id: string,
    displayOrder: number,
  ): Promise<AnnouncementRecord> => {
    const response = await api.patch<AnnouncementRecord>(
      `/announcements/${id}/order`,
      { displayOrder },
    );
    return response.data;
  },
};

export interface DepartmentStat {
  code: string;
  name: string;
  students: number;
  courses: number;
  sections: number;
}

export interface SemesterTrend {
  semester: string;
  count: number;
  completionRate: number;
  activeStudents: number;
}

export interface FacultyRank {
  rank: string;
  count: number;
}

export interface GradeDistributionEntry {
  grade: string;
  count: number;
}

export interface AdminAnalyticsOverview {
  departments: DepartmentStat[];
  semesterTrends: SemesterTrend[];
  facultyRanks: FacultyRank[];
  gradeDistribution: GradeDistributionEntry[];
  totals: {
    students: number;
    lecturers: number;
    courses: number;
    enrollments: number;
  };
}

export const campusDistributionApi = {
  getOverview: async (): Promise<AdminAnalyticsOverview> => {
    const response = await api.get<AdminAnalyticsOverview>('/academic/distribution-overview/overview');
    return response.data;
  },
};

// Notifications API
export const notificationsApi = {
  getMy: async (params?: {
    page?: number;
    limit?: number;
    isRead?: boolean;
  }): Promise<ApiResponse<NotificationRecord[]>> => {
    const response = await api.get<ApiResponse<NotificationRecord[]>>(
      '/notifications/my',
      { params },
    );
    return {
      ...response.data,
      data: (response.data.data ?? []).map((notification) => ({
        ...notification,
        content: notification.content ?? notification.message ?? '',
      })),
    };
  },
  markRead: async (id: string): Promise<ApiObject> => {
    const response = await api.patch<ApiObject>(
      `/notifications/my/${id}/read`,
      {},
    );
    return response.data;
  },
  markAllRead: async (): Promise<{ updated: number }> => {
    const response = await api.patch<{ updated: number }>(
      '/notifications/my/read-all',
      {},
    );
    return response.data;
  },
};

// Student Conduct / Training Points ("Điểm rèn luyện" - DRL) API
export interface ConductCriteriaScore {
  code: string;
  nameVi: string;
  nameEn: string;
  maxScore: number;
  score: number;
  description: string;
}

export interface ConductActivity {
  id: string;
  title: string;
  category: string;
  points: number;
  activityDate: string;
  organizer: string;
  certificateUrl?: string;
}

export interface ConductSemesterScore {
  id: string;
  semesterId: string;
  semesterName: string;
  criteria1Score: number;
  criteria2Score: number;
  criteria3Score: number;
  criteria4Score: number;
  criteria5Score: number;
  totalScore: number;
  classification: string;
  classificationVi: string;
  status: string;
  evaluatorName?: string;
  criteria: ConductCriteriaScore[];
  activities: ConductActivity[];
}

export interface StudentConductSummary {
  studentId: string;
  studentCode: string;
  fullName: string;
  cumulativeAverageScore: number;
  cumulativeClassificationVi: string;
  currentSemester: ConductSemesterScore | null;
  history: ConductSemesterScore[];
}

export const conductApi = {
  getMyConduct: async (): Promise<StudentConductSummary> => {
    const response = await api.get<StudentConductSummary>('/conduct/my');
    return response.data;
  },
  getSemesterScore: async (semesterId: string): Promise<ConductSemesterScore> => {
    const response = await api.get<ConductSemesterScore>(`/conduct/my/semester/${semesterId}`);
    return response.data;
  },
  getStudentConduct: async (studentId: string): Promise<StudentConductSummary> => {
    const response = await api.get<StudentConductSummary>(`/conduct/student/${studentId}`);
    return response.data;
  },
};

export default api;
