import type { AssistantCitation } from '@/lib/thesis-api';

export interface ChatMessage {
  id: string;
  role: 'assistant' | 'user';
  content: string;
  citations?: AssistantCitation[];
  degraded?: boolean;
  reasonCode?: string;
  /**
   * True when the message is a locally synthesized terminal fence outcome
   * (reconciliation 404 turn-not-found, a 409 whose code is anything other
   * than TURN_IN_PROGRESS, quota, auth/forbidden): the turn's idempotency key
   * was retired server-side, so the UI must not offer the degraded-answer
   * retry affordance on it. See AssistantPanel's documented exclusion list.
   */
  terminalFence?: boolean;
  /** ISO instant when a quota-limited turn resets, echoed from the server. */
  resetAt?: string;
  model?: string;
  pending?: boolean;
  feedback?: 'UP' | 'DOWN';
  feedbackReason?: string;
  feedbackPending?: boolean;
  feedbackRetry?: AssistantFeedbackSelection;
  createdAt?: string;
}

export type AssistantFeedbackRating = 'UP' | 'DOWN' | null;
export type AssistantFeedbackReason =
  | 'HELPFUL'
  | 'CLEAR'
  | 'INCORRECT'
  | 'OUTDATED'
  | 'NOT_RELEVANT'
  | 'UNSAFE';

export interface AssistantFeedbackSelection {
  rating: AssistantFeedbackRating;
  reason?: AssistantFeedbackReason;
}

export type AssistantError =
  | 'unavailable'
  | 'quota'
  | 'rate-limited'
  | 'offline'
  | 'unauthorized'
  | 'forbidden'
  | 'turn-in-progress';

/**
 * A message is only feedback-eligible once the server has replaced the
 * optimistic id with a real turn UUID. Guard-blocked, quota, cancelled and
 * personal-context answers keep a client-only id forever; showing thumbs on
 * those made every click a silent 400 and lost exactly the worst-answer
 * signal the feedback loop exists for.
 */
const PERSISTED_MESSAGE_ID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function isFeedbackEligibleAssistantMessage(message: ChatMessage): boolean {
  return PERSISTED_MESSAGE_ID_PATTERN.test(message.id);
}

export interface AssistantState {
  messages: ChatMessage[];
  conversationId?: string;
  model?: string;
  error?: AssistantError;
  /** Server-supplied Retry-After hint for the rate-limited error state. */
  retryAfterSeconds?: number;
}

export type AssistantReplyPatch = {
  answer?: string;
  content?: string;
  model?: string;
  degraded?: boolean;
  reasonCode?: string;
  terminalFence?: boolean;
  resetAt?: string | null;
  locale?: 'en' | 'vi';
  citations?: AssistantCitation[];
  messageId?: string | null;
  conversationId?: string | null;
};

export type AssistantAction =
  | { type: 'reset'; conversationId?: string; messages?: ChatMessage[] }
  | { type: 'prepend'; messages: ChatMessage[] }
  | { type: 'user'; message: ChatMessage }
  | { type: 'assistant-start'; message: ChatMessage }
  | { type: 'retry-start'; prompt: string }
  | { type: 'delta'; text: string }
  | { type: 'replace'; text: string; reasonCode?: string }
  | { type: 'meta'; model?: string; conversationId?: string }
  | { type: 'citation'; citation: AssistantCitation }
  | { type: 'complete'; reply: AssistantReplyPatch }
  | { type: 'error'; kind?: AssistantState['error'] }
  | {
      type: 'stream-failed';
      kind?: AssistantState['error'];
      retryAfterSeconds?: number;
    }
  | { type: 'feedback-start'; messageId: string }
  | {
      type: 'feedback-saved' | 'feedback-failed';
      messageId: string;
      rating: AssistantFeedbackRating;
      reason?: AssistantFeedbackReason;
    }
  | { type: 'clear-error' };

/**
 * Deterministic guard refusals: the same input is always blocked again, so
 * the stream ends locally without a JSON replay round trip. Mirrors the
 * reason codes produced by the server AssistantInputGuard.
 */
export const GUARD_BLOCKED_CODES = new Set([
  'PROMPT_INJECTION',
  'SENSITIVE_EMAIL',
  'SENSITIVE_PHONE',
  'SENSITIVE_STUDENT_ID',
  'SENSITIVE_CREDENTIAL',
  'TECHNICAL_REQUEST_BLOCKED',
]);

export const TRANSIENT_TERMINAL_CODES = new Set([
  'TURN_CANCELLED',
  'TURN_TERMINAL_RACE',
  'TURN_NOT_ACTIVE',
  'FAILED_AMBIGUOUS',
  'PURGED',
  // The server emits TURN_PURGED; both spellings stay transient so a
  // replayable-key retry shows purposeful copy instead of a wasted replay.
  'TURN_PURGED',
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
    case 'prepend':
      return {
        ...state,
        messages: [...action.messages, ...state.messages],
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
          terminalFence: undefined,
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
      // A replace carrying reason ANSWERED is a deterministic spacing repair of
      // an otherwise fully successful answer (the server normalizes glued
      // numbers at the provider boundary). It must not flash the degraded
      // badge; every other replace reason (or an untagged local replace from
      // the guard/cancel paths) is a genuine degradation.
      const isSuccessfulRepair = action.reasonCode === 'ANSWERED';
      const messages = [...state.messages];
      messages[index] = {
        ...messages[index],
        content: action.text,
        degraded: isSuccessfulRepair ? false : true,
        reasonCode: isSuccessfulRepair ? action.reasonCode : messages[index].reasonCode,
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
        terminalFence: action.reply.terminalFence ?? current.terminalFence,
        resetAt: action.reply.resetAt ?? current.resetAt,
        model: action.reply.model ?? current.model,
        pending: false,
        id: action.reply.messageId ?? current.id,
        createdAt: current.createdAt ?? new Date().toISOString(),
      };
      return {
        ...state,
        messages,
        model: action.reply.model ?? state.model,
        conversationId: action.reply.conversationId ?? state.conversationId,
      };
    }
    case 'error':
      // A 401 terminal state is fenced (authLockedRef) and must not be
      // visually downgraded: a side call failing with a generic error (New
      // Chat, history paging) may not replace the sign-in affordance with a
      // retry button that can only ever no-op.
      if (state.error === 'unauthorized' && (action.kind ?? 'unavailable') !== 'unauthorized') {
        return state;
      }
      return {
        ...state,
        error: action.kind ?? 'unavailable',
        retryAfterSeconds: undefined,
      };
    case 'stream-failed': {
      const last = state.messages[state.messages.length - 1];
      const messages =
        last?.role === 'assistant' && last.pending
          ? state.messages.slice(0, -1)
          : state.messages;
      return {
        ...state,
        messages,
        error: action.kind ?? 'unavailable',
        retryAfterSeconds: action.retryAfterSeconds,
      };
    }
    case 'clear-error':
      return { ...state, error: undefined, retryAfterSeconds: undefined };
    case 'feedback-start': {
      const messages = state.messages.map((message) =>
        message.id === action.messageId
          ? {
              ...message,
              feedbackPending: true,
              feedbackRetry: undefined,
            }
          : message,
      );
      return { ...state, messages };
    }
    case 'feedback-saved': {
      const messages = state.messages.map((message) =>
        message.id === action.messageId
          ? {
              ...message,
              feedback: action.rating ?? undefined,
              feedbackReason: action.rating ? action.reason : undefined,
              feedbackPending: false,
              feedbackRetry: undefined,
            }
          : message,
      );
      return { ...state, messages };
    }
    case 'feedback-failed': {
      const messages = state.messages.map((message) =>
        message.id === action.messageId
          ? {
              ...message,
              feedbackPending: false,
              feedbackRetry: { rating: action.rating, reason: action.reason },
            }
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
  feedbackReason?: string | null;
  createdAt?: string;
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
    feedbackReason: message.feedbackReason ?? undefined,
    createdAt: message.createdAt,
  };
}
