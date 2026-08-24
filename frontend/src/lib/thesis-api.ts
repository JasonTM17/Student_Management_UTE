import api, { API_BASE_URL, refreshSessionSingleFlight } from '@/lib/api';
import {
  createAssistantSseParser,
  parseAssistantStreamEvent,
  AssistantStreamOrder,
} from '@/lib/assistant-stream';
export {
  createAssistantSseParser,
  parseAssistantStreamEvent,
  AssistantStreamOrder,
} from '@/lib/assistant-stream';

export type ThesisRoundStatus =
  | 'DRAFT'
  | 'REGISTRATION_OPEN'
  | 'REGISTRATION_CLOSED'
  | 'PROPOSALS_PUBLISHED'
  | 'CLOSED'
  | 'CANCELLED';

export type ThesisTopicStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type ThesisGroupStatus =
  'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'CANCELLED';
export type ThesisApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ThesisRound {
  id: string;
  name: string;
  thesisType: string;
  registrationStart: string;
  registrationEnd: string;
  proposalPublishAt?: string | null;
  reportDate?: string | null;
  status: ThesisRoundStatus;
}

export interface ThesisTopic {
  id: string;
  roundId: string;
  departmentId: string;
  title: string;
  description: string;
  maxGroups: number;
  status: ThesisTopicStatus;
  createdBy: string;
}

export interface ThesisGroup {
  id: string;
  roundId: string;
  leaderStudentId: string;
  topicId?: string | null;
  status: ThesisGroupStatus;
  approvalStatus: ThesisApprovalStatus;
  rejectionReason?: string | null;
  memberStudentIds: string[];
}

export interface AssistantCitation {
  id: string;
  slug: string;
  title: string;
  source: string;
  locale: 'en' | 'vi' | 'both';
  excerpt: string;
  domain?: string;
  sourceKind?: 'CURATED' | 'CATALOG' | string;
  sourceId?: string;
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
  reasonCode:
    | 'ANSWERED'
    | 'NO_MATCH'
    | 'KNOWLEDGE_UNAVAILABLE'
    | 'PROVIDER_DISABLED'
    | 'PROVIDER_UNAVAILABLE'
    | 'HISTORY_UNAVAILABLE'
    | 'QUOTA_EXCEEDED'
    | 'CANCELLED'
    | string;
  locale: 'en' | 'vi';
  citations: AssistantCitation[];
  conversationId?: string | null;
  messageId?: string | null;
  requestId?: string;
  clientRequestId?: string;
  turnId?: string;
  replayed?: boolean;
  terminalStatus?: string;
}

export interface AssistantConversation {
  id: string;
  title?: string | null;
  locale: 'en' | 'vi';
  createdAt?: string;
  updatedAt?: string;
}

export interface AssistantMessage {
  id: string;
  conversationId?: string;
  role: 'assistant' | 'user' | 'ASSISTANT' | 'USER';
  content: string;
  model?: string | null;
  degraded?: boolean;
  reasonCode?: AssistantReply['reasonCode'] | string | null;
  citations?: AssistantCitation[];
  feedback?: 'UP' | 'DOWN' | null;
  createdAt: string;
}

export type AssistantKnowledgeState =
  | 'UNVERSIONED'
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'PUBLISHED'
  | 'ARCHIVED';

export interface AssistantKnowledgeDocument {
  documentId: string;
  revisionId?: string | null;
  version: number;
  state: AssistantKnowledgeState | string;
  locale: 'vi' | 'en' | 'both' | string;
  slug: string;
  title: string;
  content: string;
  source: string;
  priority: number;
  createdBy?: string | null;
  reviewedBy?: string | null;
  createdAt?: string | null;
  publishedAt?: string | null;
}

export interface AssistantKnowledgeRequest {
  slug: string;
  locale: 'vi' | 'en' | 'both';
  title: string;
  content: string;
  source: string;
  priority?: number;
}

export interface AssistantKnowledgeRevision {
  documentId: string;
  revisionId: string;
  version: number;
  state: AssistantKnowledgeState | string;
}

export interface AssistantCatalogCoverage {
  departments: number;
  courses: number;
  curricula: number;
  semesters: number;
}

export type AssistantStreamEvent =
  | {
      type: 'meta';
      requestId?: string;
      clientRequestId?: string;
      turnId?: string;
      conversationId?: string;
      model?: string;
      locale?: 'en' | 'vi';
    }
  | { type: 'delta'; sequence?: number; text: string; sourceIds?: string[] }
  | { type: 'replace'; text: string; sourceIds?: string[]; reasonCode?: string }
  | { type: 'citation'; citation: AssistantCitation }
  | {
      type: 'done';
      requestId?: string;
      turnId?: string;
      messageId?: string;
      reasonCode?: AssistantReply['reasonCode'] | string;
      degraded?: boolean;
    }
  | { type: 'error'; code?: string; retryable?: boolean };

export function createAssistantRequestId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function')
    return globalThis.crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
}

/** Parse one or more SSE frames. Kept pure so the stream contract can be tested without a browser. */
export const thesisApi = {
  listRounds: async (): Promise<ThesisRound[]> => {
    const response = await api.get<ThesisRound[]>('/thesis/rounds');
    return response.data;
  },

  createRound: async (data: {
    name: string;
    thesisType: string;
    registrationStart: string;
    registrationEnd: string;
    proposalPublishAt?: string;
    reportDate?: string;
  }): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>('/thesis/rounds', data);
    return response.data;
  },

  openRegistration: async (roundId: string): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>(
      '/thesis/rounds/' + roundId + '/open-registration',
    );
    return response.data;
  },

  closeRegistration: async (roundId: string): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>(
      '/thesis/rounds/' + roundId + '/close-registration',
    );
    return response.data;
  },

  publishProposals: async (roundId: string): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>(
      '/thesis/rounds/' + roundId + '/publish-proposals',
    );
    return response.data;
  },

  listTopics: async (roundId: string): Promise<ThesisTopic[]> => {
    const response = await api.get<ThesisTopic[]>('/thesis/topics', {
      params: { roundId, status: 'PUBLISHED' },
    });
    return response.data;
  },

  listGroups: async (roundId: string): Promise<ThesisGroup[]> => {
    const response = await api.get<ThesisGroup[]>('/thesis/groups', {
      params: { roundId },
    });
    return response.data;
  },

  createGroup: async (roundId: string): Promise<ThesisGroup> => {
    const response = await api.post<ThesisGroup>('/thesis/groups', { roundId });
    return response.data;
  },

  addMember: async (
    groupId: string,
    studentId: string,
  ): Promise<ThesisGroup> => {
    const response = await api.post<ThesisGroup>(
      '/thesis/groups/' + groupId + '/members',
      { studentId },
    );
    return response.data;
  },

  removeMember: async (
    groupId: string,
    studentId: string,
  ): Promise<ThesisGroup> => {
    const response = await api.delete<ThesisGroup>(
      '/thesis/groups/' + groupId + '/members/' + studentId,
    );
    return response.data;
  },

  assignTopic: async (
    groupId: string,
    topicId: string,
  ): Promise<ThesisGroup> => {
    const response = await api.post<ThesisGroup>(
      '/thesis/groups/' + groupId + '/topic',
      { topicId },
    );
    return response.data;
  },

  updateProgress: async (
    groupId: string,
    status: string,
  ): Promise<ThesisGroup> => {
    const response = await api.patch<ThesisGroup>(
      '/thesis/groups/' + groupId + '/progress',
      { status },
    );
    return response.data;
  },

  getGroup: async (groupId: string): Promise<ThesisGroup> => {
    const response = await api.get<ThesisGroup>('/thesis/groups/' + groupId);
    return response.data;
  },

  publishTopic: async (topicId: string): Promise<ThesisTopic> => {
    const response = await api.post<ThesisTopic>(
      '/thesis/topics/' + topicId + '/publish',
    );
    return response.data;
  },

  createTopic: async (data: {
    roundId: string;
    departmentId: string;
    title: string;
    description: string;
    maxGroups: number;
  }): Promise<ThesisTopic> => {
    const response = await api.post<ThesisTopic>('/thesis/topics', data);
    return response.data;
  },

  chat: async (
    message: string,
    locale: 'en' | 'vi',
    conversationId?: string,
    clientRequestId = createAssistantRequestId(),
  ): Promise<AssistantReply> => {
    const response = await api.post<AssistantReply>('/thesis/assistant/chat', {
      message,
      locale,
      clientRequestId,
      ...(conversationId ? { conversationId } : {}),
    });
    return response.data;
  },

  streamChat: async (
    message: string,
    locale: 'en' | 'vi',
    options: {
      conversationId?: string;
      clientRequestId?: string;
      signal?: AbortSignal;
      onEvent: (event: AssistantStreamEvent) => void;
    },
  ): Promise<void> => {
    const base = API_BASE_URL.endsWith('/')
      ? API_BASE_URL.slice(0, -1)
      : API_BASE_URL;
    const requestId = options.clientRequestId ?? createAssistantRequestId();
    const requestBody = {
      message,
      locale,
      clientRequestId: requestId,
      ...(options.conversationId
        ? { conversationId: options.conversationId }
        : {}),
    };
    const fetchStream = () => {
      // Refresh may rotate the CSRF cookie; read it immediately before each
      // attempt instead of reusing the pre-refresh value.
      const csrfToken =
        typeof document !== 'undefined'
          ? document.cookie.match(/(?:^|; )cc_csrf=([^;]*)/)?.[1]
          : undefined;
      return fetch(`${base}/thesis/assistant/chat/stream`, {
        method: 'POST',
        credentials: 'include',
        signal: options.signal,
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
          ...(csrfToken
            ? { 'X-CSRF-Token': decodeURIComponent(csrfToken) }
            : {}),
        },
        body: JSON.stringify(requestBody),
      });
    };
    let response = await fetchStream();
    if (response.status === 401) {
      try {
        await refreshSessionSingleFlight();
        response = await fetchStream();
      } catch {
        throw new Error('assistant stream unauthorized');
      }
    }
    if (!response.ok) {
      const error = new Error(
        `assistant stream failed (${response.status})`,
      ) as Error & { status?: number };
      error.status = response.status;
      throw error;
    }
    if (!response.body) throw new Error('assistant stream has no body');

    const order = new AssistantStreamOrder();
    let invalidFrame = false;
    const parser = createAssistantSseParser(
      (event) => {
        const validated = parseAssistantStreamEvent(event);
        order.accept(validated);
        options.onEvent(validated as AssistantStreamEvent);
      },
      {
        onInvalid: () => {
          invalidFrame = true;
        },
      },
    );
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        parser.push(decoder.decode(value, { stream: true }));
      }
      parser.push(decoder.decode());
      parser.end();
      if (invalidFrame)
        throw new Error('assistant stream contained malformed event');
      if (order.currentPhase !== 'terminal')
        throw new Error('assistant stream ended without a terminal event');
    } finally {
      reader.releaseLock();
    }
  },

  listConversations: async (params?: {
    limit?: number;
    cursor?: string;
  }): Promise<AssistantConversation[]> => {
    const response = await api.get<AssistantConversation[]>(
      '/thesis/assistant/conversations',
      { params },
    );
    return response.data;
  },

  getConversationMessages: async (
    conversationId: string,
    params?: { limit?: number; cursor?: string },
  ): Promise<AssistantMessage[]> => {
    const response = await api.get<AssistantMessage[]>(
      `/thesis/assistant/conversations/${encodeURIComponent(conversationId)}/messages`,
      { params },
    );
    return response.data;
  },

  createConversation: async (
    locale: 'en' | 'vi',
  ): Promise<AssistantConversation> => {
    const response = await api.post<AssistantConversation>(
      '/thesis/assistant/conversations',
      { locale },
    );
    return response.data;
  },

  deleteConversation: async (conversationId: string): Promise<void> => {
    await api.delete(`/thesis/assistant/conversations/${encodeURIComponent(conversationId)}`);
  },

  cancelRequest: async (
    clientRequestId: string,
  ): Promise<{ status: number }> => {
    const response = await api.post(
      `/thesis/assistant/requests/${encodeURIComponent(clientRequestId)}/cancel`,
    );
    return { status: response.status };
  },

  setMessageFeedback: async (
    messageId: string,
    rating: 'UP' | 'DOWN',
    reason?:
      | 'HELPFUL'
      | 'CLEAR'
      | 'INCORRECT'
      | 'OUTDATED'
      | 'NOT_RELEVANT'
      | 'UNSAFE',
  ): Promise<void> => {
    await api.put(
      `/thesis/assistant/messages/${encodeURIComponent(messageId)}/feedback`,
      { rating, ...(reason ? { reason } : {}) },
    );
  },

  deleteMessageFeedback: async (messageId: string): Promise<void> => {
    await api.delete(
      `/thesis/assistant/messages/${encodeURIComponent(messageId)}/feedback`,
    );
  },
};

/** Administrative curated-knowledge boundary. All writes use the shared axios client. */
export const assistantKnowledgeApi = {
  list: async (params?: {
    domain?: 'THESIS' | 'ACADEMIC';
    state?: AssistantKnowledgeState;
  }): Promise<AssistantKnowledgeDocument[]> => {
    const response = await api.get<AssistantKnowledgeDocument[]>(
      '/admin/thesis/assistant/knowledge',
      { params },
    );
    return response.data;
  },

  create: async (
    request: AssistantKnowledgeRequest,
  ): Promise<AssistantKnowledgeRevision> => {
    const response = await api.post<AssistantKnowledgeRevision>(
      '/admin/thesis/assistant/knowledge',
      request,
    );
    return response.data;
  },

  update: async (
    documentId: string,
    request: AssistantKnowledgeRequest,
  ): Promise<AssistantKnowledgeRevision> => {
    const response = await api.put<AssistantKnowledgeRevision>(
      `/admin/thesis/assistant/knowledge/${encodeURIComponent(documentId)}`,
      request,
    );
    return response.data;
  },

  submit: async (documentId: string): Promise<AssistantKnowledgeRevision> => {
    const response = await api.post<AssistantKnowledgeRevision>(
      `/admin/thesis/assistant/knowledge/${encodeURIComponent(documentId)}/submit`,
    );
    return response.data;
  },

  publish: async (documentId: string): Promise<AssistantKnowledgeRevision> => {
    const response = await api.post<AssistantKnowledgeRevision>(
      `/admin/thesis/assistant/knowledge/${encodeURIComponent(documentId)}/publish`,
    );
    return response.data;
  },

  archive: async (documentId: string): Promise<void> => {
    await api.delete(
      `/admin/thesis/assistant/knowledge/${encodeURIComponent(documentId)}`,
    );
  },

  /** Read-only coverage summary from the public academic catalog endpoints. */
  getCatalogCoverage: async (): Promise<AssistantCatalogCoverage> => {
    const [departments, courses, curricula, semesters] = await Promise.all([
      api.get<{ meta?: { total?: number }; data?: unknown[] }>('/departments', {
        params: { page: 1, limit: 1 },
      }),
      api.get<{ meta?: { total?: number }; data?: unknown[] }>('/courses', {
        params: { page: 1, limit: 1 },
      }),
      api.get<{ meta?: { total?: number }; data?: unknown[] }>('/curricula', {
        params: { page: 1, limit: 1 },
      }),
      api.get<{ meta?: { total?: number }; data?: unknown[] }>('/semesters', {
        params: { page: 1, limit: 1 },
      }),
    ]);
    const count = (value: { meta?: { total?: number }; data?: unknown[] }) =>
      value.meta?.total ?? value.data?.length ?? 0;
    return {
      departments: count(departments.data),
      courses: count(courses.data),
      curricula: count(curricula.data),
      semesters: count(semesters.data),
    };
  },
};
