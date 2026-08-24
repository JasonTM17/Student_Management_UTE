import { Platform } from 'react-native';

export type JsonObject = Record<string, unknown>;
export type AssistantLocale = 'en' | 'vi';

declare const process: {
  env: Record<string, string | undefined>;
};

export interface AssistantCitation {
  id: string;
  slug: string;
  title: string;
  source: string;
  locale: AssistantLocale | 'both';
  excerpt: string;
  domain?: string | null;
  sourceKind?: 'CURATED' | 'CATALOG' | string | null;
  sourceId?: string | null;
  revisionId?: string | null;
  revisionVersion?: number | null;
  snapshotHash?: string | null;
  entityType?: string | null;
  entityId?: string | null;
  updatedAt?: string | null;
}

export interface AssistantReply {
  answer: string;
  model: string;
  degraded: boolean;
  reasonCode: 'ANSWERED' | 'NO_MATCH' | 'KNOWLEDGE_UNAVAILABLE' | string;
  locale: AssistantLocale;
  citations: AssistantCitation[];
  conversationId?: string | null;
  messageId?: string | null;
  requestId?: string | null;
  clientRequestId?: string | null;
  turnId?: string | null;
  replayed?: boolean;
  terminalStatus?: 'COMPLETED' | 'CANCELLED' | 'FAILED_PRE_DISPATCH' | 'FAILED_AMBIGUOUS' | 'PURGED' | string | null;
}

export interface AssistantConversation {
  id: string;
  title?: string | null;
  locale: AssistantLocale;
  createdAt: string;
  updatedAt: string;
  messageCount?: number;
}

export interface AssistantHistoryMessage {
  id: string;
  role: 'user' | 'assistant' | 'USER' | 'ASSISTANT';
  content: string;
  createdAt: string;
  model?: string | null;
  degraded?: boolean;
  reasonCode?: string | null;
  citations?: AssistantCitation[];
  feedback?: AssistantFeedbackRating | null;
}

export interface AssistantConversationListResponse {
  data: AssistantConversation[];
  nextCursor?: string | null;
}

export interface AssistantMessageListResponse {
  data: AssistantHistoryMessage[];
  nextCursor?: string | null;
}

export type AssistantFeedbackRating = 'UP' | 'DOWN';
export type AssistantFeedbackReason = 'HELPFUL' | 'CLEAR' | 'INCORRECT' | 'OUTDATED' | 'NOT_RELEVANT' | 'UNSAFE';

export interface AssistantPage<T> {
  data: T[];
  nextCursor?: string | null;
}

export interface AuthUser {
  id: string;
  email: string;
  firstName?: string | null;
  lastName?: string | null;
  roles?: string[];
  permissions?: string[];
  studentId?: string | null;
  lecturerId?: string | null;
}

export interface LoginResponse {
  user: AuthUser;
  accessToken: string;
  refreshToken: string;
}

export interface MobileSection {
  id: string;
  courseId?: string;
  courseCode?: string;
  courseName?: string;
  credits?: number;
  sectionNumber: string;
  semesterId: string;
  capacity: number;
  enrolledCount: number;
  status: string;
  lecturerName?: string | null;
  classroom?: string | null;
  selected?: boolean;
  remainingSeats?: number;
  violations?: string[];
  course?: { code?: string; name?: string; nameEn?: string; nameVi?: string; credits?: number };
  schedules?: Array<{ id?: string; dayOfWeek: number; startTime: string; endTime: string; classroom?: { building?: string; roomNumber?: string } | null; building?: string | null; roomNumber?: string | null }>;
}

export interface MobileEnrollment {
  id: string;
  sectionId: string;
  semesterId: string;
  status: string;
  finalGrade?: number | null;
  letterGrade?: string | null;
  gradeStatus?: string;
  section?: MobileSection;
}

export interface RegistrationRound {
  id: string;
  semesterId: string;
  status: string;
  registrationStart: string;
  registrationEnd: string;
  addDropStart: string;
  addDropEnd: string;
  serverNow: string;
  institutionTimeZone: string;
  maxCredits: number;
}

export interface RegistrationEligibility {
  roundId: string;
  eligibilityState: string;
  priorityRank?: number | null;
  maxCredits: number;
  selectedCredits: number;
  reasonCode?: string | null;
  serverNow: string;
}

export interface RegistrationSummary {
  roundId: string;
  selectedCredits: number;
  maxCredits: number;
  selectedCount: number;
  selectedSections: MobileSection[];
}

export interface EnrollmentMutationResponse {
  enrollment: MobileEnrollment;
  replayed: boolean;
  clientRequestId: string;
}

export interface RegistrationValidationResponse {
  valid: boolean;
  violations: string[];
}

export interface MobileGrade {
  id: string;
  courseCode: string;
  courseName: string;
  finalGrade?: number | null;
  letterGrade?: string | null;
  gradeStatus: string;
  credits?: number;
}

export interface MobileAttendanceSummary {
  sectionId: string;
  courseCode: string;
  courseName: string;
  courseNameEn?: string | null;
  courseNameVi?: string | null;
  total: number;
  present: number;
  absent: number;
  late: number;
  excused: number;
  attendanceRate: number;
}

export interface MobileThesisRound {
  id: string;
  name: string;
  status: string;
  registrationStart: string;
  registrationEnd: string;
}

export interface MobileThesisTopic {
  id: string;
  roundId: string;
  departmentId: string;
  title: string;
  description: string;
  maxGroups: number;
  status: string;
}

export interface MobileThesisGroup {
  id: string;
  roundId: string;
  leaderStudentId: string;
  topicId?: string | null;
  status: string;
  approvalStatus: string;
  memberStudentIds: string[];
}

export interface MobileNotification {
  id: string;
  title: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

export interface ApiClientOptions {
  baseUrl?: string;
  mode?: ApiMode;
  getAccessToken?: () => string | undefined;
}

export type ApiMode = 'preview' | 'live';

export interface ApiClient {
  readonly baseUrl: string;
  readonly mode: ApiMode;
  setAccessToken(token: string | undefined): void;
  setSessionTokens(accessToken: string | undefined, refreshToken: string | undefined): void;
  getRefreshToken(): string | undefined;
  clearAccessToken(): void;
  request<TResponse>(path: string, init?: RequestInit): Promise<TResponse>;
  requestWithMeta<TResponse>(path: string, init?: RequestInit): Promise<{ data: TResponse; headers: Headers; requestId: string }>;
  requestBytes(path: string, init?: RequestInit): Promise<{ data: Uint8Array; headers: Headers; requestId: string }>;
  get<TResponse>(path: string, init?: RequestInit): Promise<TResponse>;
  post<TResponse>(path: string, body?: unknown, init?: RequestInit): Promise<TResponse>;
  put<TResponse>(path: string, body?: unknown, init?: RequestInit): Promise<TResponse>;
  patch<TResponse>(path: string, body?: unknown, init?: RequestInit): Promise<TResponse>;
  delete<TResponse>(path: string, init?: RequestInit): Promise<TResponse>;
}

export class ApiClientError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly requestId: string;
  readonly retryable: boolean;
  readonly violations: JsonObject[];

  constructor(message: string, status: number, requestId: string, code?: string, retryable = false, violations: JsonObject[] = []) {
    super(message);
    this.name = 'ApiClientError';
    this.status = status;
    this.requestId = requestId;
    this.code = code;
    this.retryable = retryable;
    this.violations = violations;
  }
}

export const DEFAULT_API_BASE_URL = Platform.OS === 'android'
  ? 'http://10.0.2.2:4010/api/v1'
  : 'http://127.0.0.1:4010/api/v1';

const configuredApiBaseUrl = process.env.EXPO_PUBLIC_API_URL;
const configuredApiMode: ApiMode = process.env.EXPO_PUBLIC_API_MODE === 'preview' ? 'preview' : 'live';

function normalizeBaseUrl(value: string) {
  return value.replace(/\/+$/, '');
}

function normalizePath(path: string) {
  return path.startsWith('/') ? path : `/${path}`;
}

function createRequestId() {
  return `mobile-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/** RFC 4122 id used for the assistant idempotency contract. */
export function createAssistantClientRequestId() {
  const cryptoApi = globalThis.crypto as Crypto | undefined;
  if (cryptoApi?.randomUUID) return cryptoApi.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16);
    const value = character === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

/** UUID that must be retained by callers across retries of one mutation. */
export const createIdempotencyKey = createAssistantClientRequestId;

async function readResponseBody(response: Response) {
  if (response.status === 204) {
    return undefined;
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) {
    return response.json();
  }

  const text = await response.text();
  return text || undefined;
}

function getErrorDetails(body: unknown) {
  if (!body || typeof body !== 'object') {
    return { message: 'The API request failed', code: undefined, retryable: false, violations: [] as JsonObject[] };
  }

  const payload = body as JsonObject;
  return {
    message: typeof payload.message === 'string' ? payload.message : typeof payload.title === 'string' ? payload.title : 'The API request failed',
    code: typeof payload.code === 'string' ? payload.code : undefined,
    retryable: payload.retryable === true,
    violations: Array.isArray(payload.violations) ? payload.violations.filter((item): item is JsonObject => Boolean(item && typeof item === 'object')) : [],
  };
}

function shouldRefreshAfterUnauthorized(path: string, status: number) {
  const normalizedPath = normalizePath(path);
  return (
    status === 401 &&
    !normalizedPath.startsWith('/auth/login') &&
    !normalizedPath.startsWith('/auth/refresh') &&
    !normalizedPath.startsWith('/auth/logout')
  );
}

export function createApiClient(options: ApiClientOptions = {}): ApiClient {
  const baseUrl = normalizeBaseUrl(
    options.baseUrl ?? configuredApiBaseUrl ?? DEFAULT_API_BASE_URL,
  );
  const mode = options.mode ?? configuredApiMode;
  let accessToken: string | undefined;
  let refreshToken: string | undefined;
  const getAccessToken = options.getAccessToken ?? (() => accessToken);

  async function refreshMobileSession(requestId: string) {
    if (!refreshToken) {
      return false;
    }

    const response = await fetch(`${baseUrl}${apiRoutes.auth.refresh}`, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-Request-Id': `${requestId}-refresh`,
      },
      body: JSON.stringify({ refreshToken }),
    });
    const body = await readResponseBody(response);

    if (!response.ok) {
      accessToken = undefined;
      refreshToken = undefined;
      const details = getErrorDetails(body);
      throw new ApiClientError(
        details.message,
        response.status,
        requestId,
        details.code ?? (response.status === 401 ? 'SESSION_EXPIRED' : response.status === 403 ? 'FORBIDDEN' : undefined),
        details.retryable,
        details.violations,
      );
    }

    const nextSession = body as LoginResponse;
    if (!nextSession?.accessToken || !nextSession.refreshToken) {
      accessToken = undefined;
      refreshToken = undefined;
      throw new ApiClientError(
        'The Java auth refresh response did not include rotated tokens',
        401,
        requestId,
        'INVALID_REFRESH_RESPONSE',
      );
    }

    accessToken = nextSession.accessToken;
    refreshToken = nextSession.refreshToken;
    return true;
  }

  const client: ApiClient = {
    baseUrl,
    mode,

    setAccessToken(token) {
      accessToken = token;
    },

    setSessionTokens(nextAccessToken, nextRefreshToken) {
      accessToken = nextAccessToken;
      refreshToken = nextRefreshToken;
    },

    getRefreshToken() {
      return refreshToken;
    },

    clearAccessToken() {
      accessToken = undefined;
      refreshToken = undefined;
    },

    async requestWithMeta<TResponse>(path: string, init: RequestInit = {}) {
      const requestId = createRequestId();
      if (mode !== 'live') {
        throw new ApiClientError(
          'Live mobile API mode is disabled for this preview build',
          0,
          requestId,
          'MOBILE_API_PREVIEW',
        );
      }
      const headers = new Headers(init.headers);
      const token = getAccessToken();

      headers.set('Accept', 'application/json');
      headers.set('X-Request-Id', requestId);
      if (init.body && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json');
      }
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }

      let response: Response;
      try {
        response = await fetch(`${baseUrl}${normalizePath(path)}`, { ...init, headers });
      } catch (error) {
        throw new ApiClientError(error instanceof Error ? error.message : 'Network unavailable', 0, requestId, 'NETWORK_OFFLINE', true);
      }
      let body = await readResponseBody(response);

      if (shouldRefreshAfterUnauthorized(path, response.status) && refreshToken) {
        const refreshed = await refreshMobileSession(requestId);
        if (refreshed && accessToken) {
          headers.set('Authorization', `Bearer ${accessToken}`);
          try {
            response = await fetch(`${baseUrl}${normalizePath(path)}`, { ...init, headers });
          } catch (error) {
            throw new ApiClientError(error instanceof Error ? error.message : 'Network unavailable', 0, requestId, 'NETWORK_OFFLINE', true);
          }
          body = await readResponseBody(response);
        }
      }

      if (!response.ok) {
        const details = getErrorDetails(body);
        throw new ApiClientError(
          details.message,
          response.status,
          requestId,
          details.code ?? (response.status === 401 ? 'SESSION_EXPIRED' : response.status === 403 ? 'FORBIDDEN' : undefined),
          details.retryable,
          details.violations,
        );
      }

      return { data: body as TResponse, headers: response.headers, requestId };
    },

    async requestBytes(path: string, init: RequestInit = {}) {
      const requestId = createRequestId();
      if (mode !== 'live') throw new ApiClientError('Live mobile API mode is disabled for this preview build', 0, requestId, 'MOBILE_API_PREVIEW');
      const headers = new Headers(init.headers);
      const token = getAccessToken();
      headers.set('Accept', 'application/pdf,application/octet-stream');
      headers.set('X-Request-Id', requestId);
      if (token) headers.set('Authorization', `Bearer ${token}`);
      let response: Response;
      try { response = await fetch(`${baseUrl}${normalizePath(path)}`, { ...init, headers }); }
      catch (error) { throw new ApiClientError(error instanceof Error ? error.message : 'Network unavailable', 0, requestId, 'NETWORK_OFFLINE', true); }
      if (!response.ok) {
        const body = await readResponseBody(response);
        const details = getErrorDetails(body);
        throw new ApiClientError(details.message, response.status, requestId, details.code ?? (response.status === 401 ? 'SESSION_EXPIRED' : response.status === 403 ? 'FORBIDDEN' : undefined), details.retryable, details.violations);
      }
      return { data: new Uint8Array(await response.arrayBuffer()), headers: response.headers, requestId };
    },

    async request<TResponse>(path: string, init: RequestInit = {}) {
      const result = await client.requestWithMeta<TResponse>(path, init);
      return result.data;
    },

    get<TResponse>(path: string, init: RequestInit = {}) {
      return client.request<TResponse>(path, { ...init, method: 'GET' });
    },

    post<TResponse>(path: string, body: unknown = undefined, init: RequestInit = {}) {
      return client.request<TResponse>(path, {
        ...init,
        method: 'POST',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    },

    put<TResponse>(path: string, body: unknown = undefined, init: RequestInit = {}) {
      return client.request<TResponse>(path, {
        ...init,
        method: 'PUT',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    },

    patch<TResponse>(path: string, body: unknown = undefined, init: RequestInit = {}) {
      return client.request<TResponse>(path, {
        ...init,
        method: 'PATCH',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    },

    delete<TResponse>(path: string, init: RequestInit = {}) {
      return client.request<TResponse>(path, { ...init, method: 'DELETE' });
    },
  };

  return client;
}

export const apiClient = createApiClient();

export const apiRoutes = {
  auth: {
    login: '/auth/login',
    refresh: '/auth/refresh',
    logout: '/auth/logout',
    me: '/auth/me',
  },
  identity: '/me',
  health: '/health/liveness',
  contract: '/contract',
  notifications: '/notifications/my',
  thesis: {
    rounds: '/thesis/rounds',
    topics: '/thesis/topics',
    groups: '/thesis/groups',
    assistantChat: '/thesis/assistant/chat',
    assistantConversations: '/thesis/assistant/conversations',
    assistantCancel: '/thesis/assistant/requests',
  },
  sections: '/sections',
  enrollments: '/enrollments/my',
  grades: '/enrollments/my/grades',
  attendanceSummary: '/attendance/my/summary',
  registration: {
    rounds: '/registration/rounds',
    eligibility: '/me/registration/eligibility',
    summary: '/me/registration/summary',
    enrollments: '/me/enrollments',
    validate: '/me/enrollments/validate',
    slip: '/me/registration/slip',
  },
} as const;

type RegistrationSnapshot = {
  round?: RegistrationRound;
  sections?: MobileSection[];
  eligibility?: RegistrationEligibility;
  summary?: RegistrationSummary;
  enrollments?: MobileEnrollment[];
};
let registrationSnapshot: RegistrationSnapshot = {};
export function getRegistrationSnapshot() { return registrationSnapshot; }

export const campusApi = {
  health: () => apiClient.get<JsonObject>(apiRoutes.health),
  contract: () => apiClient.get<JsonObject>(apiRoutes.contract),
  me: () => apiClient.get<JsonObject>(apiRoutes.identity),
  account: () => apiClient.get<AuthUser>(apiRoutes.auth.me),
  login: (email: string, password: string) =>
    apiClient.post<LoginResponse>(apiRoutes.auth.login, { email, password }),
  refresh: async () => {
    const response = await apiClient.post<LoginResponse>(
      apiRoutes.auth.refresh,
      { refreshToken: apiClient.getRefreshToken() },
    );
    apiClient.setSessionTokens(response.accessToken, response.refreshToken);
    return response;
  },
  logout: () =>
    apiClient.post<void>(apiRoutes.auth.logout, { refreshToken: apiClient.getRefreshToken() }),
  sections: (semesterId?: string) =>
    apiClient.get<{ data: MobileSection[] }>(
      apiRoutes.sections + (semesterId ? `?semesterId=${encodeURIComponent(semesterId)}` : ''),
    ),
  enrollments: (semesterId?: string) =>
    apiClient.get<MobileEnrollment[]>(
      apiRoutes.enrollments + (semesterId ? `?semesterId=${encodeURIComponent(semesterId)}` : ''),
    ),
  enroll: (sectionId: string, locale: AssistantLocale = 'vi') =>
    apiClient.post<MobileEnrollment>('/enrollments/enroll', { sectionId, locale }),
  dropEnrollment: (enrollmentId: string) =>
    apiClient.post<{ message: string }>(`/enrollments/${enrollmentId}/drop`, {}),
  registrationRounds: async (semesterId?: string) => {
    const query = semesterId ? `?semesterId=${encodeURIComponent(semesterId)}&limit=20` : '?limit=20';
    const page = await apiClient.get<{ items: RegistrationRound[]; nextCursor?: string | null }>(`${apiRoutes.registration.rounds}${query}`);
    const round = page.items?.[0];
    if (round) registrationSnapshot = { ...registrationSnapshot, round };
    return page;
  },
  registrationRound: async (roundId: string) => {
    const round = await apiClient.get<RegistrationRound>(`${apiRoutes.registration.rounds}/${encodeURIComponent(roundId)}`);
    registrationSnapshot = { ...registrationSnapshot, round };
    return round;
  },
  registrationSections: async (roundId: string) => {
    const sections = await apiClient.get<MobileSection[]>(`${apiRoutes.registration.rounds}/${encodeURIComponent(roundId)}/sections`);
    registrationSnapshot = { ...registrationSnapshot, sections };
    return sections;
  },
  registrationEligibility: async (roundId: string) => {
    const eligibility = await apiClient.get<RegistrationEligibility>(`${apiRoutes.registration.eligibility}?roundId=${encodeURIComponent(roundId)}`);
    registrationSnapshot = { ...registrationSnapshot, eligibility };
    return eligibility;
  },
  registrationSummary: async (roundId: string) => {
    const summary = await apiClient.get<RegistrationSummary>(`${apiRoutes.registration.summary}?roundId=${encodeURIComponent(roundId)}`);
    registrationSnapshot = { ...registrationSnapshot, summary };
    return summary;
  },
  registrationEnrollments: async (semesterId?: string, cursor?: string, limit = 50) => {
    const query = new URLSearchParams({ limit: String(limit) });
    if (semesterId) query.set('semesterId', semesterId);
    if (cursor) query.set('cursor', cursor);
    const response = await apiClient.requestWithMeta<{ items: MobileEnrollment[]; nextCursor?: string | null } | MobileEnrollment[]>(`${apiRoutes.registration.enrollments}?${query.toString()}`, { method: 'GET' });
    const payload = Array.isArray(response.data) ? response.data : response.data.items ?? [];
    registrationSnapshot = { ...registrationSnapshot, enrollments: payload };
    return { items: payload, nextCursor: response.headers.get('X-Next-Cursor') || (Array.isArray(response.data) ? null : response.data.nextCursor) };
  },
  validateEnrollment: (sectionId: string, roundId: string) =>
    apiClient.post<RegistrationValidationResponse>(apiRoutes.registration.validate, { sectionId, roundId }),
  registrationEnroll: (sectionId: string, roundId: string, idempotencyKey: string) =>
    apiClient.post<EnrollmentMutationResponse>(apiRoutes.registration.enrollments, { sectionId, roundId }, { headers: { 'Idempotency-Key': idempotencyKey } }),
  registrationDrop: (enrollmentId: string, idempotencyKey: string) =>
    apiClient.delete<void>(`${apiRoutes.registration.enrollments}/${encodeURIComponent(enrollmentId)}`, { headers: { 'Idempotency-Key': idempotencyKey } }),
  registrationSlip: (roundId: string) =>
    apiClient.requestBytes(`${apiRoutes.registration.slip}?roundId=${encodeURIComponent(roundId)}`),
  grades: (semesterId?: string) =>
    apiClient.get<MobileGrade[]>(
      apiRoutes.grades + (semesterId ? `?semesterId=${encodeURIComponent(semesterId)}` : ''),
    ),
  attendanceSummary: (semesterId?: string) =>
    apiClient.get<MobileAttendanceSummary[]>(
      apiRoutes.attendanceSummary + (semesterId ? `?semesterId=${encodeURIComponent(semesterId)}` : ''),
    ),
  notifications: () => apiClient.get<{ data: MobileNotification[] }>(apiRoutes.notifications),
  markNotificationRead: (id: string) =>
    apiClient.patch<JsonObject>(`/notifications/my/${id}/read`, {}),
  markAllNotificationsRead: () =>
    apiClient.patch<{ updated: number }>('/notifications/my/read-all', {}),
  thesisRounds: () => apiClient.get<MobileThesisRound[]>(apiRoutes.thesis.rounds),
  thesisTopics: (roundId: string) =>
    apiClient.get<MobileThesisTopic[]>(
      `${apiRoutes.thesis.topics}?roundId=${encodeURIComponent(roundId)}&status=PUBLISHED`,
    ),
  thesisGroups: (roundId: string) =>
    apiClient.get<MobileThesisGroup[]>(
      `${apiRoutes.thesis.groups}?roundId=${encodeURIComponent(roundId)}`,
    ),
  createThesisGroup: (roundId: string) =>
    apiClient.post<MobileThesisGroup>(apiRoutes.thesis.groups, { roundId }),
  assignThesisTopic: (groupId: string, topicId: string) =>
    apiClient.post<MobileThesisGroup>(`${apiRoutes.thesis.groups}/${groupId}/topic`, { topicId }),
  assistantChat: (
    message: string,
    locale: AssistantLocale = 'en',
    conversationId?: string,
    clientRequestIdOrInit?: string | RequestInit,
    init?: RequestInit,
  ) =>
    apiClient.post<AssistantReply>(
      apiRoutes.thesis.assistantChat,
      {
        message,
        locale,
        clientRequestId: typeof clientRequestIdOrInit === 'string' ? clientRequestIdOrInit : createAssistantClientRequestId(),
        ...(conversationId ? { conversationId } : {}),
      },
      typeof clientRequestIdOrInit === 'string' ? init : clientRequestIdOrInit,
    ),
  assistantConversationsPage: async (limit = 20, cursor?: string): Promise<AssistantPage<AssistantConversation>> => {
    const query = new URLSearchParams({ limit: String(limit) });
    if (cursor) query.set('cursor', cursor);
    const response = await apiClient.requestWithMeta<AssistantConversation[] | AssistantConversationListResponse>(
      `${apiRoutes.thesis.assistantConversations}?${query.toString()}`,
      { method: 'GET' },
    );
    const payload = Array.isArray(response.data) ? response.data : response.data.data;
    return { data: payload, nextCursor: response.headers.get('X-Next-Cursor') ?? (Array.isArray(response.data) ? null : response.data.nextCursor) };
  },
  assistantConversations: async () => campusApi.assistantConversationsPage(),
  assistantConversationMessagesPage: async (conversationId: string, limit = 50, cursor?: string): Promise<AssistantPage<AssistantHistoryMessage>> => {
    const query = new URLSearchParams({ limit: String(limit) });
    if (cursor) query.set('cursor', cursor);
    const response = await apiClient.requestWithMeta<AssistantHistoryMessage[] | AssistantMessageListResponse>(
      `${apiRoutes.thesis.assistantConversations}/${encodeURIComponent(conversationId)}/messages?${query.toString()}`,
      { method: 'GET' },
    );
    const payload = Array.isArray(response.data) ? response.data : response.data.data;
    return { data: payload, nextCursor: response.headers.get('X-Next-Cursor') ?? (Array.isArray(response.data) ? null : response.data.nextCursor) };
  },
  assistantConversationMessages: async (conversationId: string) => campusApi.assistantConversationMessagesPage(conversationId),
  deleteAssistantConversation: (conversationId: string) =>
    apiClient.delete<void>(
      `${apiRoutes.thesis.assistantConversations}/${encodeURIComponent(conversationId)}`,
    ),
  cancelAssistantRequest: (clientRequestId: string) =>
    apiClient.post<void>(`${apiRoutes.thesis.assistantCancel}/${encodeURIComponent(clientRequestId)}/cancel`, {}),
  putAssistantFeedback: (messageId: string, rating: AssistantFeedbackRating, reason?: AssistantFeedbackReason) =>
    apiClient.put<void>(`/thesis/assistant/messages/${encodeURIComponent(messageId)}/feedback`, { rating, ...(reason ? { reason } : {}) }),
  deleteAssistantFeedback: (messageId: string) =>
    apiClient.delete<void>(`/thesis/assistant/messages/${encodeURIComponent(messageId)}/feedback`),
};
