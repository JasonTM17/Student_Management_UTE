import api from '@/lib/api';
import type {
  RegistrationEligibility,
  RegistrationEnrollment,
  RegistrationPage,
  RegistrationRound,
  RegistrationSection,
  RegistrationSummary,
  RegistrationViolation,
} from './types';

function randomKey(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') return globalThis.crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function page<T>(payload: unknown, nextCursor?: string): RegistrationPage<T> {
  if (Array.isArray(payload)) return { items: payload as T[], nextCursor };
  const value = payload as { data?: unknown; items?: unknown; nextCursor?: string; cursor?: string };
  const items = Array.isArray(value?.items)
    ? value.items
    : Array.isArray(value?.data)
      ? value.data
      : [];
  return { items: items as T[], nextCursor: value?.nextCursor ?? value?.cursor ?? nextCursor };
}

function headerCursor(headers: Record<string, unknown>): string | undefined {
  const value = headers['x-next-cursor'] ?? headers['X-Next-Cursor'];
  return typeof value === 'string' && value.length ? value : undefined;
}

export const registrationApi = {
  async getRounds(params?: { semesterId?: string; cursor?: string; limit?: number }): Promise<RegistrationPage<RegistrationRound>> {
    const response = await api.get('/registration/rounds', { params: { limit: 20, ...params } });
    return page<RegistrationRound>(response.data, headerCursor(response.headers as Record<string, unknown>));
  },
  async getRound(roundId: string): Promise<RegistrationRound> {
    const response = await api.get<RegistrationRound>(`/registration/rounds/${encodeURIComponent(roundId)}`);
    return response.data;
  },
  async getSections(roundId: string, params?: { cursor?: string; limit?: number }): Promise<RegistrationPage<RegistrationSection>> {
    const response = await api.get(`/registration/rounds/${encodeURIComponent(roundId)}/sections`, { params: { limit: 100, ...params } });
    return page<RegistrationSection>(response.data, headerCursor(response.headers as Record<string, unknown>));
  },
  async getEligibility(roundId: string): Promise<RegistrationEligibility> {
    const response = await api.get<RegistrationEligibility>('/me/registration/eligibility', { params: { roundId } });
    return response.data;
  },
  async getSummary(roundId: string): Promise<RegistrationSummary> {
    const response = await api.get<RegistrationSummary>('/me/registration/summary', { params: { roundId } });
    return response.data;
  },
  async getEnrollments(semesterId?: string | null, params?: { cursor?: string; limit?: number }): Promise<RegistrationPage<RegistrationEnrollment>> {
    const response = await api.get('/me/enrollments', { params: { semesterId, limit: 50, ...params } });
    return page<RegistrationEnrollment>(response.data, headerCursor(response.headers as Record<string, unknown>));
  },
  async validate(roundId: string, sectionIds: string[]): Promise<{ valid: boolean; violations: RegistrationViolation[] }> {
    const violations: RegistrationViolation[] = [];
    for (const sectionId of sectionIds) {
      const response = await api.post<{ valid?: boolean; violations?: Array<string | RegistrationViolation> }>('/me/enrollments/validate', { roundId, sectionId });
      for (const item of response.data.violations ?? []) {
        violations.push(typeof item === 'string' ? { code: item, message: item, sectionId } : { ...item, sectionId: item.sectionId ?? sectionId });
      }
    }
    return { valid: violations.length === 0, violations };
  },
  async enroll(roundId: string, sectionId: string, idempotencyKey = randomKey()): Promise<RegistrationEnrollment> {
    const response = await api.post<RegistrationEnrollment | { enrollment: RegistrationEnrollment }>('/me/enrollments', { roundId, sectionId }, { headers: { 'Idempotency-Key': idempotencyKey } });
    return 'enrollment' in response.data ? response.data.enrollment : response.data;
  },
  async drop(enrollmentId: string, idempotencyKey = randomKey()): Promise<void> {
    await api.delete(`/me/enrollments/${encodeURIComponent(enrollmentId)}`, { headers: { 'Idempotency-Key': idempotencyKey } });
  },
  async getSlip(roundId: string): Promise<Blob> {
    const response = await api.get('/me/registration/slip', { params: { roundId }, responseType: 'blob' });
    return response.data as Blob;
  },
};

export interface AdminRegistrationRoundRequest {
  semesterId: string;
  registrationStart: string;
  registrationEnd: string;
  addDropStart: string;
  addDropEnd: string;
  maxCredits: number;
  cohortYears: number[];
  version?: number;
}

export const adminRegistrationApi = {
  async list(): Promise<RegistrationRound[]> {
    const response = await api.get<RegistrationRound[] | { data: RegistrationRound[] }>('/admin/registration/rounds');
    return Array.isArray(response.data) ? response.data : response.data.data;
  },
  async create(request: AdminRegistrationRoundRequest): Promise<RegistrationRound> {
    const response = await api.post<RegistrationRound>('/admin/registration/rounds', request);
    return response.data;
  },
  async update(roundId: string, request: AdminRegistrationRoundRequest): Promise<RegistrationRound> {
    const response = await api.put<RegistrationRound>(`/admin/registration/rounds/${encodeURIComponent(roundId)}`, request);
    return response.data;
  },
  async transition(roundId: string, action: 'open' | 'close' | 'archive', version?: number): Promise<RegistrationRound> {
    const response = await api.post<RegistrationRound>(`/admin/registration/rounds/${encodeURIComponent(roundId)}/${action}`, { version });
    return response.data;
  },
};
