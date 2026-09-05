import type { AssistantCitation } from '@/lib/thesis-api';

export interface ChatMessage {
  id: string;
  role: 'assistant' | 'user';
  content: string;
  citations?: AssistantCitation[];
  degraded?: boolean;
  reasonCode?: string;
  model?: string;
  pending?: boolean;
  feedback?: 'UP' | 'DOWN';
}

export type AssistantError =
  'unavailable' | 'quota' | 'offline' | 'unauthorized' | 'forbidden';

export interface AssistantState {
  messages: ChatMessage[];
  conversationId?: string;
  model?: string;
  error?: AssistantError;
}

export type AssistantReplyPatch = {
  answer?: string;
  content?: string;
  model?: string;
  degraded?: boolean;
  reasonCode?: string;
  locale?: 'en' | 'vi';
  citations?: AssistantCitation[];
  messageId?: string | null;
  conversationId?: string | null;
};

export type AssistantAction =
  | { type: 'reset'; conversationId?: string; messages?: ChatMessage[] }
  | { type: 'user'; message: ChatMessage }
  | { type: 'assistant-start'; message: ChatMessage }
  | { type: 'retry-start'; prompt: string }
  | { type: 'delta'; text: string }
  | { type: 'replace'; text: string }
  | { type: 'meta'; model?: string; conversationId?: string }
  | { type: 'citation'; citation: AssistantCitation }
  | { type: 'complete'; reply: AssistantReplyPatch }
  | { type: 'error'; kind?: AssistantState['error'] }
  | { type: 'feedback'; messageId: string; rating: 'UP' | 'DOWN' }
  | { type: 'clear-error' };

export const TRANSIENT_TERMINAL_CODES = new Set([
  'TURN_CANCELLED',
  'TURN_TERMINAL_RACE',
  'TURN_NOT_ACTIVE',
  'FAILED_AMBIGUOUS',
  'PURGED',
]);

export function assistantReducer(
  state: AssistantState,
  action: AssistantAction,
): AssistantState {
  switch (action.type) {
    case 'reset':
      return {
        messages: action.messages ?? [],
        conversationId: action.conversationId,
      };
    case 'user':
      return {
        ...state,
        error: undefined,
        messages: [...state.messages, action.message],
      };
    case 'assistant-start':
      return {
        ...state,
        error: undefined,
        messages: [...state.messages, action.message],
      };
    case 'retry-start': {
      const messages = [...state.messages];
      const last = messages[messages.length - 1];
      if (last?.role === 'assistant') {
        messages[messages.length - 1] = {
          ...last,
          content: '',
          citations: [],
          pending: true,
          degraded: undefined,
          reasonCode: undefined,
        };
      } else {
        messages.push({
          id: `${Date.now()}-retry-assistant`,
          role: 'assistant',
          content: '',
          pending: true,
        });
      }
      return { ...state, error: undefined, messages };
    }
    case 'delta': {
      const index = state.messages.length - 1;
      if (index < 0 || state.messages[index].role !== 'assistant') return state;
      const messages = [...state.messages];
      messages[index] = {
        ...messages[index],
        content: messages[index].content + action.text,
      };
      return { ...state, messages };
    }
    case 'replace': {
      const index = state.messages.length - 1;
      if (index < 0 || state.messages[index].role !== 'assistant') return state;
      const messages = [...state.messages];
      messages[index] = {
        ...messages[index],
        content: action.text,
        degraded: true,
        pending: true,
      };
      return { ...state, messages };
    }
    case 'meta':
      return {
        ...state,
        model: action.model ?? state.model,
        conversationId: action.conversationId ?? state.conversationId,
      };
    case 'citation': {
      const index = state.messages.length - 1;
      if (index < 0 || state.messages[index].role !== 'assistant') return state;
      const messages = [...state.messages];
      messages[index] = {
        ...messages[index],
        citations: [...(messages[index].citations ?? []), action.citation],
      };
      return { ...state, messages };
    }
    case 'complete': {
      const index = state.messages.length - 1;
      if (index < 0 || state.messages[index].role !== 'assistant') return state;
      const messages = [...state.messages];
      const current = messages[index];
      messages[index] = {
        ...current,
        content: action.reply.content ?? current.content,
        citations: action.reply.citations ?? current.citations,
        degraded: action.reply.degraded ?? current.degraded,
        reasonCode: action.reply.reasonCode ?? current.reasonCode,
        model: action.reply.model ?? current.model,
        pending: false,
        id: action.reply.messageId ?? current.id,
      };
      return {
        ...state,
        messages,
        model: action.reply.model ?? state.model,
        conversationId: action.reply.conversationId ?? state.conversationId,
      };
    }
    case 'error':
      return { ...state, error: action.kind ?? 'unavailable' };
    case 'clear-error':
      return { ...state, error: undefined };
    case 'feedback': {
      const messages = state.messages.map((message) =>
        message.id === action.messageId
          ? { ...message, feedback: action.rating }
          : message,
      );
      return { ...state, messages };
    }
    default:
      return state;
  }
}

export const initialState: AssistantState = { messages: [] };

export function fromHistoryMessage(message: {
  id: string;
  role: 'assistant' | 'user' | 'ASSISTANT' | 'USER';
  content: string;
  citations?: AssistantCitation[];
  degraded?: boolean;
  reasonCode?: string | null;
  model?: string | null;
  feedback?: 'UP' | 'DOWN' | null;
}): ChatMessage {
  const role = message.role.toLowerCase() === 'user' ? 'user' : 'assistant';
  return {
    id: message.id,
    role,
    content: message.content,
    citations: message.citations,
    degraded: message.degraded,
    reasonCode: message.reasonCode ?? undefined,
    model: message.model ?? undefined,
    feedback: message.feedback ?? undefined,
  };
}
