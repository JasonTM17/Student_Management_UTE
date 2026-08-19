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

  listCouncils: async (roundId: string): Promise<ThesisCouncil[]> => {
    const response = await api.get<ThesisCouncil[]>('/thesis/councils', {
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

  chat: async (message: string, locale: 'en' | 'vi'): Promise<AssistantReply> => {
    const response = await api.post<AssistantReply>('/thesis/assistant/chat', {
      message,
      locale,
    });
    return response.data;
  },
};
