import api, { API_BASE_URL, createRequestId, refreshSessionSingleFlight } from '@/lib/api';
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
  | 'PROPOSAL_OPEN'
  | 'PROPOSALS_PUBLISHED'
  | 'REGISTRATION_OPEN'
  | 'REGISTRATION_CLOSED'
  | 'RESULTS_PUBLISHED'
  | 'CLOSED'
  | 'CANCELLED';

export type ThesisTopicStatus = 'DRAFT' | 'PUBLISHED' | 'APPROVED' | 'REJECTED' | 'ARCHIVED';
export type ThesisGroupStatus =
  'DRAFT' | 'ASSIGNED' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'CANCELLED';
export type ThesisApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ThesisRound {
  id: string;
  name: string;
  thesisType: string;
  registrationStart: string;
  registrationEnd: string;
  lecturerSubmitStart: string;
  lecturerSubmitEnd: string;
  proposalPublishAt?: string | null;
  gvpbDeadline?: string | null;
  reportDate?: string | null;
  defenseDate?: string | null;
  status: ThesisRoundStatus;
}

export interface ThesisTopicSupervisor {
  lecturerId: string;
  supervisorOrder: number;
  /** Resolved server-side; null only when the lecturer has no directory row. */
  firstName?: string | null;
  lastName?: string | null;
  /** Staff/owner only — the server redacts this for students. */
  email?: string | null;
}

/** The server's `normalizeRole` accepts exactly these three seats. */
export type ThesisCouncilMemberRole = 'CHAIR' | 'SECRETARY' | 'MEMBER';

export interface ThesisCouncilMember {
  councilId?: string;
  lecturerId: string;
  memberRole: ThesisCouncilMemberRole;
}

export interface ThesisCouncil {
  id: string;
  roundId: string;
  name: string;
  status?: string;
  members?: ThesisCouncilMember[];
  topicIds?: string[];
}

export interface ThesisGroupReport {
  groupId: string;
  title?: string | null;
  url?: string | null;
  note?: string | null;
  submittedBy: string;
  submittedAt: string;
  updatedAt: string;
  /** Feedback item 7: the attached Word/PDF artifact, when the leader uploaded one. */
  fileName?: string | null;
  fileType?: string | null;
  fileSize?: number | null;
}

export interface ThesisRepositorySupervisor {
  displayName: string;
  supervisorOrder: number;
}

export interface ThesisRepositoryMember {
  displayName: string;
  studentNumber?: string | null;
  isExternal: boolean;
  isLeader: boolean;
}

/** Server-owned archive projection; internal identifiers and contact fields are intentionally absent. */
export interface ThesisRepositoryReport {
  reportId: string;
  title?: string | null;
  url?: string | null;
  note?: string | null;
  submittedAt: string;
  updatedAt: string;
  fileName?: string | null;
  fileType?: string | null;
  fileSize?: number | null;
  topicTitle: string;
  topicDescription?: string | null;
  departmentName: string;
  groupStatus: string;
  approvalStatus: string;
  submittedByDisplayName?: string | null;
  submittedByStudentNumber?: string | null;
  supervisors: ThesisRepositorySupervisor[];
  members: ThesisRepositoryMember[];
}

export type ThesisProgressMilestone =
  | 'ROUND_SELECTED'
  | 'GROUP_CREATED'
  | 'TOPIC_ASSIGNED'
  | 'GROUP_APPROVED'
  | 'REPORT_SUBMITTED'
  | 'COUNCIL_ASSIGNED'
  | 'SCORE_FINALIZED'
  | 'RESULTS_PUBLISHED';

export interface ThesisProgressResponse {
  roundId: string;
  roundStatus: string;
  participationState: 'PARTICIPATING' | 'NOT_PARTICIPATING';
  currentMilestone: ThesisProgressMilestone;
  completedMilestones: ThesisProgressMilestone[];
  attentionState:
    | 'NONE'
    | 'NOT_PARTICIPATING'
    | 'GROUP_REJECTED'
    | 'GROUP_CANCELLED'
    | 'GROUP_INVALID_MEMBER_COUNT'
    | 'PROGRESS_INCONSISTENT'
    | 'RESULT_NOT_AVAILABLE';
  groupId?: string | null;
  groupStatus?: string | null;
  approvalStatus?: string | null;
  memberCount: number;
  topicId?: string | null;
  topicTitle?: string | null;
  reportId?: string | null;
  reportSubmittedAt?: string | null;
  councilId?: string | null;
  finalScore?: number | null;
  finalScoreFinalizedAt?: string | null;
  userReportedGroupStatus?: string | null;
}

export interface ThesisCouncilScore {
  councilId?: string;
  topicId?: string;
  lecturerId?: string;
  score: number;
  component?: string | null;
}

export interface ThesisFinalScore {
  councilId?: string;
  topicId?: string;
  finalScore: number;
  finalizedAt?: string | null;
}

export interface ThesisRoundResult {
  groupId: string;
  topicTitle: string;
  finalScore: number;
  councilName: string;
  leaderStudentId: string;
}

export interface ThesisSupervisedTopic {
  topicId: string;
  title: string;
  topicStatus: string;
  roundId: string;
  roundName: string;
  roundStatus: string;
  reportDate?: string | null;
  groupCount: number;
  pendingGroupCount: number;
}

export interface ThesisCouncilAssignment {
  councilId: string;
  name: string;
  memberRole: string;
  roundId: string;
  roundName: string;
  roundStatus: string;
  reportDate?: string | null;
  gvpbDeadline?: string | null;
  topicCount: number;
}

export interface ThesisGradingTask {
  topicId: string;
  title: string;
  councilId: string;
  councilName: string;
  roundName: string;
  roundStatus: string;
  myScoreRows: number;
}

export interface ThesisLecturerWorkload {
  topics: ThesisSupervisedTopic[];
  councils: ThesisCouncilAssignment[];
  gradingTasks?: ThesisGradingTask[];
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
  finalScore?: number | null;
  resultStatus?: string | null;
}

export interface ThesisGroupMember {
  studentId: string;
  studentNumber?: string | null;
  displayName?: string | null;
  contact?: string | null;
  isExternal?: boolean;
  isLeader?: boolean;
  memberOrder?: number;
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
  members?: ThesisGroupMember[];
}

/**
 * Minimal directory entry used when a leader invites a member into a thesis
 * group. Carries no email: directory lookups must not expose contact PII.
 */
export interface ThesisStudentResult {
  studentId: string;
  studentNumber: string;
  firstName: string;
  lastName: string;
  curriculumCode?: string | null;
  curriculumName?: string | null;
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
  corpusVersion?: string | null;
  corpusHash?: string | null;
  releaseId?: string | null;
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
    | 'PROVIDER_TRUNCATED'
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

export interface AssistantMessagePage {
  items: AssistantMessage[];
  nextCursor?: string;
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
  domain?: AssistantKnowledgeDomain | string;
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
  domain?: AssistantKnowledgeDomain;
  priority?: number;
}

export type AssistantKnowledgeDomain =
  | 'THESIS'
  | 'REGISTRATION'
  | 'ACADEMIC_CATALOG'
  | 'ANNOUNCEMENT'
  | 'POLICY'
  | 'GENERAL_FAQ';

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
  return createRequestId();
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
    lecturerSubmitStart: string;
    lecturerSubmitEnd: string;
    proposalPublishAt?: string;
    gvpbDeadline?: string;
    reportDate?: string;
    defenseDate?: string;
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

  openProposals: async (roundId: string): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>(
      '/thesis/rounds/' + roundId + '/open-proposals',
    );
    return response.data;
  },

  publishResults: async (roundId: string): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>(
      '/thesis/rounds/' + roundId + '/publish-results',
    );
    return response.data;
  },

  listTopics: async (
    roundId: string,
    status?: ThesisTopicStatus,
  ): Promise<ThesisTopic[]> => {
    const response = await api.get<ThesisTopic[]>('/thesis/topics', {
      params: { roundId, status: status ?? 'PUBLISHED' },
    });
    return response.data;
  },

  getTopic: async (topicId: string): Promise<ThesisTopic> => {
    const response = await api.get<ThesisTopic>('/thesis/topics/' + topicId);
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
    member: string | { studentId?: string; displayName?: string; contact?: string },
  ): Promise<ThesisGroup> => {
    const payload = typeof member === 'string' ? { studentId: member } : member;
    const response = await api.post<ThesisGroup>(
      '/thesis/groups/' + groupId + '/members',
      payload,
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

  searchStudents: async (query: string): Promise<ThesisStudentResult[]> => {
    const response = await api.get<ThesisStudentResult[]>(
      '/thesis/students/search',
      { params: { q: query } },
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

  submitReport: async (
    groupId: string,
    data: { title?: string; url: string; note?: string },
  ): Promise<ThesisGroupReport> => {
    const response = await api.post<ThesisGroupReport>(
      '/thesis/groups/' + groupId + '/report',
      data,
    );
    return response.data;
  },

  /** Feedback item 7: upload the report document itself (.pdf/.doc/.docx, <= 20 MB). */
  submitReportFile: async (
    groupId: string,
    data: {
      file: File;
      title?: string;
      note?: string;
      /** Receives 0-100 as axios reports upload progress. */
      onUploadProgress?: (percent: number) => void;
      /** Lets the caller abandon an in-flight upload from the UI. */
      signal?: AbortSignal;
    },
  ): Promise<ThesisGroupReport> => {
    const form = new FormData();
    form.append('file', data.file);
    if (data.title) form.append('title', data.title);
    if (data.note) form.append('note', data.note);
    const response = await api.post<ThesisGroupReport>(
      '/thesis/groups/' + groupId + '/report/file',
      form,
      {
        // A 20 MB document on a campus connection otherwise looks frozen: the
        // submission design calls for a live percentage and a cancel control.
        onUploadProgress: data.onUploadProgress
          ? (event) => {
              const total = event.total ?? 0;
              if (total > 0) {
                data.onUploadProgress!(Math.min(100, Math.round((event.loaded / total) * 100)));
              }
            }
          : undefined,
        signal: data.signal,
      },
    );
    return response.data;
  },

  /** Feedback item 7: fetch the attached document and hand it to the browser's saver. */
  downloadReportFile: async (
    groupId: string,
    report: { fileName?: string | null },
  ): Promise<void> => {
    const response = await api.get<Blob>(
      '/thesis/groups/' + groupId + '/report/file',
      { responseType: 'blob' },
    );
    const header = response.headers?.['content-disposition'] as string | undefined;
    let name = report.fileName || 'thesis-report';
    if (header) {
      const star = header.match(/filename\*=UTF-8''([^;]+)/i);
      const plain = header.match(/filename="?([^";]+)"?/i);
      const encoded = star?.[1] ?? plain?.[1];
      if (encoded) {
        try {
          name = decodeURIComponent(encoded);
        } catch {
          name = encoded;
        }
      }
    }
    const url = URL.createObjectURL(response.data);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = name;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  },

  downloadRepositoryReportFile: async (
    reportId: string,
    report: { fileName?: string | null },
  ): Promise<void> => {
    const response = await api.get<Blob>(
      '/thesis/reports/' + reportId + '/file',
      { responseType: 'blob' },
    );
    const header = response.headers?.['content-disposition'] as string | undefined;
    let name = report.fileName || 'thesis-report';
    if (header) {
      const star = header.match(/filename\*=UTF-8''([^;]+)/i);
      const plain = header.match(/filename="?([^";]+)"?/i);
      const encoded = star?.[1] ?? plain?.[1];
      if (encoded) {
        try {
          name = decodeURIComponent(encoded);
        } catch {
          name = encoded;
        }
      }
    }
    const url = URL.createObjectURL(response.data);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = name;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  },

  getReport: async (groupId: string): Promise<ThesisGroupReport> => {
    const response = await api.get<ThesisGroupReport>(
      '/thesis/groups/' + groupId + '/report',
    );
    return response.data;
  },

  getTopicReport: async (topicId: string): Promise<ThesisGroupReport | null> => {
    try {
      const response = await api.get<ThesisGroupReport>(
        '/thesis/topics/' + topicId + '/report',
      );
      return response.data || null;
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 404 || status === 204) {
        return null;
      }
      throw err;
    }
  },

  listRoundReports: async (roundId: string): Promise<ThesisGroupReport[]> => {
    const response = await api.get<ThesisGroupReport[]>(
      '/thesis/rounds/' + roundId + '/reports',
    );
    return response.data || [];
  },

  listRoundRepository: async (roundId: string): Promise<ThesisRepositoryReport[]> => {
    const response = await api.get<ThesisRepositoryReport[]>(
      '/thesis/rounds/' + roundId + '/repository',
    );
    return response.data || [];
  },

  getMyProgress: async (roundId: string): Promise<ThesisProgressResponse> => {
    const response = await api.get<ThesisProgressResponse>('/thesis/me/progress', {
      params: { roundId },
    });
    return response.data;
  },

  createCouncil: async (
    roundId: string,
    name: string,
  ): Promise<ThesisCouncil> => {
    const response = await api.post<ThesisCouncil>('/thesis/councils', {
      roundId,
      name,
    });
    return response.data;
  },

  listCouncils: async (roundId: string): Promise<ThesisCouncil[]> => {
    const response = await api.get<ThesisCouncil[]>('/thesis/councils', {
      params: { roundId },
    });
    return response.data;
  },

  addCouncilMember: async (
    councilId: string,
    lecturerId: string,
    memberRole: ThesisCouncilMemberRole,
  ): Promise<ThesisCouncilMember> => {
    const response = await api.post<ThesisCouncilMember>(
      '/thesis/councils/' + councilId + '/members',
      { lecturerId, memberRole },
    );
    return response.data;
  },

  removeCouncilMember: async (
    councilId: string,
    lecturerId: string,
  ): Promise<void> => {
    await api.delete('/thesis/councils/' + councilId + '/members/' + lecturerId);
  },

  assignTopicToCouncil: async (
    councilId: string,
    topicId: string,
  ): Promise<void> => {
    await api.post('/thesis/councils/' + councilId + '/topics', { topicId });
  },

  submitScore: async (
    councilId: string,
    topicId: string,
    score: number,
    component?: string,
  ): Promise<ThesisCouncilScore> => {
    const response = await api.post<ThesisCouncilScore>(
      '/thesis/councils/' + councilId + '/topics/' + topicId + '/scores',
      { score, ...(component ? { component } : {}) },
    );
    return response.data;
  },

  listScores: async (
    councilId: string,
    topicId: string,
  ): Promise<ThesisCouncilScore[]> => {
    try {
      const response = await api.get<ThesisCouncilScore[]>(
        '/thesis/councils/' + councilId + '/topics/' + topicId + '/scores',
      );
      return response.data;
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 403 || status === 404 || status === 204) {
        return [];
      }
      throw err;
    }
  },

  finalizeScores: async (
    councilId: string,
    topicId: string,
  ): Promise<ThesisFinalScore> => {
    const response = await api.post<ThesisFinalScore>(
      '/thesis/councils/' + councilId + '/topics/' + topicId + '/finalize',
    );
    return response.data;
  },

  myResults: async (roundId: string): Promise<ThesisRoundResult[]> => {
    const response = await api.get<ThesisRoundResult[]>('/thesis/me/results', {
      params: { roundId },
    });
    return response.data;
  },

  approveGroup: async (groupId: string): Promise<ThesisGroup> => {
    const response = await api.post<ThesisGroup>(
      `/thesis/groups/${groupId}/approve`,
    );
    return response.data;
  },

  rejectGroup: async (
    groupId: string,
    reason: string,
  ): Promise<ThesisGroup> => {
    const response = await api.post<ThesisGroup>(
      `/thesis/groups/${groupId}/reject`,
      { reason },
    );
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

  updateTopic: async (
    topicId: string,
    data: {
      departmentId?: string;
      title?: string;
      description?: string;
      maxGroups?: number;
    },
  ): Promise<ThesisTopic> => {
    const response = await api.put<ThesisTopic>('/thesis/topics/' + topicId, data);
    return response.data;
  },

  setSupervisors: async (
    topicId: string,
    supervisorIds: string[],
  ): Promise<void> => {
    await api.put('/thesis/topics/' + topicId + '/supervisors', {
      supervisorIds,
    });
  },

  listSupervisors: async (
    topicId: string,
  ): Promise<ThesisTopicSupervisor[]> => {
    const response = await api.get<ThesisTopicSupervisor[]>(
      '/thesis/topics/' + topicId + '/supervisors',
    );
    return response.data;
  },

  myWorkload: async (): Promise<ThesisLecturerWorkload> => {
    const response = await api.get<ThesisLecturerWorkload>('/thesis/me/workload');
    return response.data;
  },

  chat: async (
    message: string,
    locale: 'en' | 'vi',
    conversationId?: string,
    clientRequestId = createAssistantRequestId(),
    scope?: 'academic' | 'specialized',
  ): Promise<AssistantReply> => {
    const response = await api.post<AssistantReply>('/assistant/chat', {
      message,
      locale,
      clientRequestId,
      ...(conversationId ? { conversationId } : {}),
      ...(scope ? { scope } : {}),
    });
    return response.data;
  },

  streamChat: async (
    message: string,
    locale: 'en' | 'vi',
    options: {
      conversationId?: string;
      clientRequestId?: string;
      scope?: 'academic' | 'specialized';
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
      ...(options.scope ? { scope: options.scope } : {}),
    };
    const fetchStream = () => {
      // Refresh may rotate the CSRF cookie; read it immediately before each
      // attempt instead of reusing the pre-refresh value.
      const csrfToken =
        typeof document !== 'undefined'
          ? document.cookie.match(/(?:^|; )cc_csrf=([^;]*)/)?.[1]
          : undefined;
      return fetch(`${base}/assistant/chat/stream`, {
        method: 'POST',
        credentials: 'include',
        signal: controller.signal,
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
    // A dead proxy can stall the reader indefinitely after the headers arrive,
    // leaving the panel in "thinking" until the student manually stops. The
    // idle watchdog aborts when no bytes arrive within the window and honors
    // the caller's signal (Stop button) at the same time.
    const IDLE_TIMEOUT_MS = 45_000;
    const controller = new AbortController();
    const onCallerAbort = () => controller.abort();
    options.signal?.addEventListener('abort', onCallerAbort);
    let idleTimer: ReturnType<typeof setTimeout> | undefined;
    const resetIdleWatchdog = () => {
      if (idleTimer !== undefined) clearTimeout(idleTimer);
      idleTimer = setTimeout(() => {
        controller.abort();
      }, IDLE_TIMEOUT_MS);
    };
    resetIdleWatchdog();
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
        resetIdleWatchdog();
        parser.push(decoder.decode(value, { stream: true }));
      }
      parser.push(decoder.decode());
      parser.end();
      if (invalidFrame)
        throw new Error('assistant stream contained malformed event');
      if (order.currentPhase !== 'terminal')
        throw new Error('assistant stream ended without a terminal event');
    } finally {
      if (idleTimer !== undefined) clearTimeout(idleTimer);
      options.signal?.removeEventListener('abort', onCallerAbort);
      reader.releaseLock();
    }
  },

  listConversations: async (params?: {
    limit?: number;
    cursor?: string;
  }): Promise<AssistantConversation[]> => {
    const response = await api.get<AssistantConversation[]>(
      '/assistant/conversations',
      { params },
    );
    return response.data;
  },

  listConversationsPage: async (params?: {
    limit?: number;
    cursor?: string;
  }): Promise<{ items: AssistantConversation[]; nextCursor?: string }> => {
    const response = await api.get<AssistantConversation[]>(
      '/assistant/conversations',
      { params },
    );
    const rawCursor = response.headers?.['x-next-cursor'];
    const nextCursor =
      typeof rawCursor === 'string' && rawCursor ? rawCursor : undefined;
    return { items: response.data, nextCursor };
  },

  getConversationMessages: async (
    conversationId: string,
    params?: { limit?: number; cursor?: string },
  ): Promise<AssistantMessagePage> => {
    const response = await api.get<AssistantMessage[]>(
      `/assistant/conversations/${encodeURIComponent(conversationId)}/messages`,
      { params },
    );
    const rawCursor = response.headers?.['x-next-cursor'];
    const nextCursor =
      typeof rawCursor === 'string' && rawCursor ? rawCursor : undefined;
    return { items: response.data, nextCursor };
  },

  createConversation: async (
    locale: 'en' | 'vi',
  ): Promise<AssistantConversation> => {
    const response = await api.post<AssistantConversation>(
      '/assistant/conversations',
      { locale },
    );
    return response.data;
  },

  deleteConversation: async (conversationId: string): Promise<void> => {
    await api.delete(`/assistant/conversations/${encodeURIComponent(conversationId)}`);
  },

  cancelRequest: async (
    clientRequestId: string,
  ): Promise<{ status: number }> => {
    const response = await api.post(
      `/assistant/requests/${encodeURIComponent(clientRequestId)}/cancel`,
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
      `/assistant/messages/${encodeURIComponent(messageId)}/feedback`,
      { rating, ...(reason ? { reason } : {}) },
    );
  },

  deleteMessageFeedback: async (messageId: string): Promise<void> => {
    await api.delete(
      `/assistant/messages/${encodeURIComponent(messageId)}/feedback`,
    );
  },
};

/** Administrative curated-knowledge boundary. All writes use the shared axios client. */
export const assistantKnowledgeApi = {
  list: async (params?: {
    domain?: AssistantKnowledgeDomain;
    state?: AssistantKnowledgeState;
  }): Promise<AssistantKnowledgeDocument[]> => {
    const response = await api.get<AssistantKnowledgeDocument[]>(
      '/admin/assistant/knowledge',
      { params },
    );
    return response.data;
  },

  create: async (
    request: AssistantKnowledgeRequest,
  ): Promise<AssistantKnowledgeRevision> => {
    const response = await api.post<AssistantKnowledgeRevision>(
      '/admin/assistant/knowledge',
      request,
    );
    return response.data;
  },

  update: async (
    documentId: string,
    request: AssistantKnowledgeRequest,
  ): Promise<AssistantKnowledgeRevision> => {
    const response = await api.put<AssistantKnowledgeRevision>(
      `/admin/assistant/knowledge/${encodeURIComponent(documentId)}`,
      request,
    );
    return response.data;
  },

  submit: async (documentId: string): Promise<AssistantKnowledgeRevision> => {
    const response = await api.post<AssistantKnowledgeRevision>(
      `/admin/assistant/knowledge/${encodeURIComponent(documentId)}/submit`,
    );
    return response.data;
  },

  publish: async (documentId: string): Promise<AssistantKnowledgeRevision> => {
    const response = await api.post<AssistantKnowledgeRevision>(
      `/admin/assistant/knowledge/${encodeURIComponent(documentId)}/publish`,
    );
    return response.data;
  },

  archive: async (documentId: string): Promise<void> => {
    await api.delete(
      `/admin/assistant/knowledge/${encodeURIComponent(documentId)}`,
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
