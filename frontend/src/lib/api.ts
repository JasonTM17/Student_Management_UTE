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
  Lecturer,
  GradingSection,
  LecturerSection,
  SectionGrades,
  SectionSchedule,
} from '@/types/api';
import { addLocalePrefix, stripLocaleFromPathname } from '@/i18n/paths';
import { resolvePublicApiBaseUrl } from '@/lib/public-api-url';
import { CSRF_COOKIE_NAME } from '@/lib/session-hint';

export const API_BASE_URL = resolvePublicApiBaseUrl(process.env.NEXT_PUBLIC_API_URL);

// crypto.randomUUID only exists in secure contexts (HTTPS/localhost); the Docker
// web stack serves plain HTTP on LAN IPs, so fall back to a timestamp-based id.
export function createRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
}
type ApiObject = Record<string, unknown>;
type AuthRequestConfig = AxiosRequestConfig & {
  skipAuthRefresh?: boolean;
  skipAuthRedirect?: boolean;
  _retry?: boolean;
  _retryNoCache?: boolean;
};
type AuthInternalRequestConfig = InternalAxiosRequestConfig & {
  skipAuthRefresh?: boolean;
  skipAuthRedirect?: boolean;
  _retry?: boolean;
  _retryNoCache?: boolean;
};
type AnnouncementRecord = {
  id: string;
  title: string;
  content: string;
  priority: string;
  createdAt: string;
  semester?: { name: string; nameEn?: string; nameVi?: string } | null;
  section?: {
    sectionNumber: string;
    course?: { code?: string; name?: string; nameEn?: string; nameVi?: string };
  } | null;
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
const AUTH_REFRESH_ROUTE_PATTERN = /^\/auth\/(login|register|refresh|logout)(?:\/|$)/;
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
  return applyCsrfHeader(config as AuthInternalRequestConfig);
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalConfig = error.config as AuthRequestConfig | undefined;
    const unauthorized = error.response?.status === 401;
    const notModified = error.response?.status === 304;

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

  register: async (data: {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
  }): Promise<LoginResponse> => {
    const response = await api.post<LoginResponse>('/auth/register', data, {
      skipAuthRefresh: true,
      skipAuthRedirect: true,
    } as AuthRequestConfig);
    return response.data;
  },

  me: async (): Promise<User> => {
    const response = await api.get<User>('/auth/me', {
      skipAuthRedirect: true,
      skipAuthRefresh: true,
    } as AuthRequestConfig);
    return response.data;
  },

  updateProfile: async (data: {
    firstName?: string;
    lastName?: string;
    phone?: string;
    dateOfBirth?: string;
    address?: string;
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
    grades: { enrollmentId: string; finalGrade: number | null; letterGrade: string }[],
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
  create: async (data: ApiObject): Promise<User> => {
    const response = await api.post<User>('/users', data);
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
  }): Promise<ApiResponse<AnnouncementRecord[]>> => {
    const response = await api.get<ApiResponse<AnnouncementRecord[]>>(
      '/announcements',
      { params },
    );
    return response.data;
  },
  create: async (data: ApiObject): Promise<ApiObject> => {
    const response = await api.post<ApiObject>('/announcements', data);
    return response.data;
  },
  update: async (id: string, data: ApiObject): Promise<ApiObject> => {
    const response = await api.put<ApiObject>(`/announcements/${id}`, data);
    return response.data;
  },
  delete: async (id: string): Promise<{ message: string }> => {
    const response = await api.delete<{ message: string }>(
      `/announcements/${id}`,
    );
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

export default api;
