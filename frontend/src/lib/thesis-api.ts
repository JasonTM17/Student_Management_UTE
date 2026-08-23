import api from '@/lib/api';

export type ThesisRoundStatus =
  | 'DRAFT'
  | 'REGISTRATION_OPEN'
  | 'REGISTRATION_CLOSED'
  | 'PROPOSALS_PUBLISHED'
  | 'CLOSED'
  | 'CANCELLED';

export type ThesisTopicStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type ThesisGroupStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'COMPLETED' | 'CANCELLED';
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
}

export interface AssistantReply {
  answer: string;
  model: string;
  degraded: boolean;
  reasonCode: 'ANSWERED' | 'NO_MATCH' | 'KNOWLEDGE_UNAVAILABLE';
  locale: 'en' | 'vi';
  citations: AssistantCitation[];
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
  }): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>('/thesis/rounds', data);
    return response.data;
  },

  openRegistration: async (roundId: string): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>('/thesis/rounds/' + roundId + '/open-registration');
    return response.data;
  },

  closeRegistration: async (roundId: string): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>('/thesis/rounds/' + roundId + '/close-registration');
    return response.data;
  },

  publishProposals: async (roundId: string): Promise<ThesisRound> => {
    const response = await api.post<ThesisRound>('/thesis/rounds/' + roundId + '/publish-proposals');
    return response.data;
  },

  listTopics: async (roundId: string): Promise<ThesisTopic[]> => {
    const response = await api.get<ThesisTopic[]>('/thesis/topics', {
      params: { roundId, status: 'PUBLISHED' },
    });
    return response.data;
  },

  listGroups: async (roundId: string): Promise<ThesisGroup[]> => {
    const response = await api.get<ThesisGroup[]>('/thesis/groups', { params: { roundId } });
    return response.data;
  },

  createGroup: async (roundId: string): Promise<ThesisGroup> => {
    const response = await api.post<ThesisGroup>('/thesis/groups', { roundId });
    return response.data;
  },

  addMember: async (groupId: string, studentId: string): Promise<ThesisGroup> => {
    const response = await api.post<ThesisGroup>('/thesis/groups/' + groupId + '/members', { studentId });
    return response.data;
  },

  removeMember: async (groupId: string, studentId: string): Promise<ThesisGroup> => {
    const response = await api.delete<ThesisGroup>('/thesis/groups/' + groupId + '/members/' + studentId);
    return response.data;
  },

  assignTopic: async (groupId: string, topicId: string): Promise<ThesisGroup> => {
    const response = await api.post<ThesisGroup>('/thesis/groups/' + groupId + '/topic', { topicId });
    return response.data;
  },

  updateProgress: async (groupId: string, status: string): Promise<ThesisGroup> => {
    const response = await api.patch<ThesisGroup>('/thesis/groups/' + groupId + '/progress', { status });
    return response.data;
  },

  getGroup: async (groupId: string): Promise<ThesisGroup> => {
    const response = await api.get<ThesisGroup>('/thesis/groups/' + groupId);
    return response.data;
  },

  publishTopic: async (topicId: string): Promise<ThesisTopic> => {
    const response = await api.post<ThesisTopic>('/thesis/topics/' + topicId + '/publish');
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

  chat: async (message: string, locale: 'en' | 'vi'): Promise<AssistantReply> => {
    const response = await api.post<AssistantReply>('/thesis/assistant/chat', { message, locale });
    return response.data;
  },
};
