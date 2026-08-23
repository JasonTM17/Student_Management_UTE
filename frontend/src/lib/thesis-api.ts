import api from '@/lib/api';

export type ThesisRoundStatus =
  | 'DRAFT'
  | 'REGISTRATION_OPEN'
  | 'REGISTRATION_CLOSED'
  | 'PROPOSALS_PUBLISHED'
  | 'DEFENSE_SCHEDULED'
  | 'SCORING_OPEN'
  | 'RESULTS_PUBLISHED'
  | 'CLOSED'
  | 'CANCELLED';

export type ThesisTopicStatus = 'DRAFT' | 'PUBLISHED' | 'RESERVED' | 'CLOSED' | 'CANCELLED';

export type ThesisGroupStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'CANCELLED';

export type ThesisApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export type ThesisCouncilStatus =
  | 'DRAFT'
  | 'SCHEDULED'
  | 'SCORING_OPEN'
  | 'FINALIZED'
  | 'CANCELLED';

export interface ThesisRound {
  id: string;
  name: string;
  thesisType: string;
  registrationStart: string;
  registrationEnd: string;
  proposalPublishAt?: string | null;
  reportDate?: string | null;
  defenseDate?: string | null;
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

export interface ThesisCouncilMember {
  lecturerId: string;
  memberRole: string;
  memberOrder: number;
}

export interface ThesisCouncil {
  id: string;
  roundId: string;
  departmentId: string;
  scheduledAt?: string | null;
  room?: string | null;
  status: ThesisCouncilStatus;
  members: ThesisCouncilMember[];
}

export interface ThesisResult {
  id: string;
  groupId: string;
  totalScore?: number | null;
  grade?: string | null;
  status: 'DRAFT' | 'PUBLISHED';
  publishedAt?: string | null;
}

export interface AssistantReply {
  answer: string;
  model: string;
  degraded: boolean;
}

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
    defenseDate?: string;
  }): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>('/thesis/rounds', data);
    return response.data;
  },

  openRegistration: async (roundId: string): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>(`/thesis/rounds/${roundId}/open-registration`);
    return response.data;
  },

  closeRegistration: async (roundId: string): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>(`/thesis/rounds/${roundId}/close-registration`);
    return response.data;
  },

  publishProposals: async (roundId: string): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>(`/thesis/rounds/${roundId}/publish-proposals`);
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

  addMember: async (groupId: string, studentId: string): Promise<ThesisGroup> => {
    const response = await api.post<ThesisGroup>(`/thesis/groups/${groupId}/members`, {
      studentId,
    });
    return response.data;
  },

  assignTopic: async (groupId: string, topicId: string): Promise<ThesisGroup> => {
    const response = await api.post<ThesisGroup>(`/thesis/groups/${groupId}/topic`, {
      topicId,
    });
    return response.data;
  },

  getGroup: async (groupId: string): Promise<ThesisGroup> => {
    const response = await api.get<ThesisGroup>(`/thesis/groups/${groupId}`);
    return response.data;
  },

  listCouncils: async (roundId: string): Promise<ThesisCouncil[]> => {
    const response = await api.get<ThesisCouncil[]>('/thesis/councils', {
      params: { roundId },
    });
    return response.data;
  },

  submitReview: async (
    councilId: string,
    groupId: string,
    score: number,
    comment: string,
  ): Promise<void> => {
    await api.post('/thesis/reviews', { councilId, groupId, score, comment });
  },

  publishResult: async (councilId: string, groupId: string): Promise<ThesisResult> => {
    const response = await api.post<ThesisResult>('/thesis/results/publish', {
      councilId,
      groupId,
    });
    return response.data;
  },

  publishTopic: async (topicId: string): Promise<ThesisTopic> => {
    const response = await api.post<ThesisTopic>(`/thesis/topics/${topicId}/publish`);
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

  createCouncil: async (data: {
    roundId: string;
    departmentId: string;
  }): Promise<ThesisCouncil> => {
    const response = await api.post<ThesisCouncil>('/thesis/councils', data);
    return response.data;
  },

  addCouncilMember: async (
    councilId: string,
    data: { lecturerId: string; memberRole: string; memberOrder: number },
  ): Promise<ThesisCouncil> => {
    const response = await api.post<ThesisCouncil>(`/thesis/councils/${councilId}/members`, data);
    return response.data;
  },

  decideGroup: async (groupId: string, approved: boolean, reason?: string): Promise<ThesisGroup> => {
    const response = await api.post<ThesisGroup>(`/thesis/groups/${groupId}/decision`, {
      approved,
      reason,
    });
    return response.data;
  },

  scheduleCouncil: async (councilId: string, scheduledAt: string, room: string): Promise<ThesisCouncil> => {
    const response = await api.post<ThesisCouncil>(`/thesis/councils/${councilId}/schedule`, {
      scheduledAt,
      room,
    });
    return response.data;
  },

  openScoring: async (councilId: string): Promise<ThesisCouncil> => {
    const response = await api.post<ThesisCouncil>(`/thesis/councils/${councilId}/open-scoring`);
    return response.data;
  },

  chat: async (message: string, locale: 'en' | 'vi' = 'en'): Promise<AssistantReply> => {
    const response = await api.post<AssistantReply>('/thesis/assistant/chat', {
      message,
      locale,
    });
    return response.data;
  },
};
