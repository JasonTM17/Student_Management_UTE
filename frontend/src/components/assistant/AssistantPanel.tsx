'use client';

import {
  FormEvent,
  KeyboardEvent,
  useCallback,
  useEffect,
  useReducer,
  useRef,
  useState,
} from 'react';
import {
  Bookmark,
  Bot,
  ChevronLeft,
  History,
  LoaderCircle,
  MessageCircle,
  RotateCcw,
  Send,
  Square,
  ThumbsDown,
  ThumbsUp,
  Trash2,
  X,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useDialogFocusTrap } from '@/components/ui/use-dialog-focus-trap';
import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import {
  createAssistantRequestId,
  thesisApi,
  type AssistantCitation,
  type AssistantConversation,
  type AssistantStreamEvent,
} from '@/lib/thesis-api';

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
type AssistantError =
  'unavailable' | 'quota' | 'offline' | 'unauthorized' | 'forbidden';
interface AssistantState {
  messages: ChatMessage[];
  conversationId?: string;
  model?: string;
  error?: AssistantError;
}
type AssistantReplyPatch = {
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
type AssistantAction =
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

const TRANSIENT_TERMINAL_CODES = new Set([
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
const initialState: AssistantState = { messages: [] };

function AssistantLauncherMark() {
  return (
    <span
      className="relative inline-flex size-8 items-center justify-center"
      aria-hidden="true"
    >
      <MessageCircle className="size-8 stroke-[1.75] transition-transform duration-200 ease-out group-hover:scale-[1.04]" />
      <Bookmark className="absolute left-1/2 top-1/2 size-3.5 -translate-x-1/2 -translate-y-[42%] fill-current/20 stroke-[2.25]" />
    </span>
  );
}
function fromHistoryMessage(message: {
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

export function AssistantPanel() {
  const { locale, messages } = useI18n();
  const { confirm, confirmationDialog } = useConfirmationDialog();
  const [open, setOpen] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [input, setInput] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [history, setHistory] = useState<AssistantConversation[]>([]);
  const [historyStatus, setHistoryStatus] = useState<
    'idle' | 'loading' | 'loaded' | 'error'
  >('idle');
  const [deletingConversationId, setDeletingConversationId] =
    useState<string>();
  const [lastPrompt, setLastPrompt] = useState<string>();
  const [state, dispatch] = useReducer(assistantReducer, initialState);
  const launcherRef = useRef<HTMLButtonElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const logRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  const requestGenerationRef = useRef(0);
  const activeRequestIdRef = useRef<string>();
  const retryRequestIdRef = useRef<string>();
  // Preserve the conversation value that was part of the canonical request
  // hash. A newly-created conversation is revealed in `meta` before `done`,
  // but a lost-ack retry must keep the original null/specific value.
  const activeConversationIdRef = useRef<string | undefined>();
  const retryConversationIdRef = useRef<string | undefined>();
  const activePromptRef = useRef<string>();
  const selectedHistoryRef = useRef(false);
  const historyFetchedRef = useRef(false);
  const userScrolledRef = useRef(false);

  const loadHistory = useCallback(() => {
    if (historyStatus === 'loading') return;
    setHistoryStatus('loading');
    void thesisApi
      .listConversations({ limit: 20 })
      .then((items) => {
        setHistory(items);
        setHistoryStatus('loaded');
        historyFetchedRef.current = true;
      })
      .catch(() => setHistoryStatus('error'));
  }, [historyStatus]);

  const reconcileHistory = () => {
    // A server-created conversation remains hidden until its terminal commit;
    // clear the one-fetch latch so the next render observes the committed row.
    historyFetchedRef.current = false;
    selectedHistoryRef.current = false;
    setHistoryStatus('idle');
  };

  useEffect(() => {
    if (!open || historyFetchedRef.current || selectedHistoryRef.current)
      return;
    loadHistory();
  }, [loadHistory, open]);

  useEffect(() => {
    const node = logRef.current;
    if (!node) return;
    if (userScrolledRef.current && !isSending) return;
    node.scrollTo({
      top: node.scrollHeight,
      behavior: isSending ? 'smooth' : 'auto',
    });
  }, [state.messages, isSending]);

  const handleLogScroll = () => {
    const node = logRef.current;
    if (!node) return;
    userScrolledRef.current =
      node.scrollHeight - node.scrollTop - node.clientHeight > 48;
  };

  const closePanel = useCallback(() => {
    requestGenerationRef.current += 1;
    abortRef.current?.abort();
    setIsSending(false);
    setOpen(false);
    setShowHistory(false);
    activeRequestIdRef.current = undefined;
    retryRequestIdRef.current = undefined;
    activeConversationIdRef.current = undefined;
    retryConversationIdRef.current = undefined;
    activePromptRef.current = undefined;
    selectedHistoryRef.current = false;
    historyFetchedRef.current = false;
    setHistoryStatus('idle');
    requestAnimationFrame(() => launcherRef.current?.focus());
  }, []);

  const handlePanelEscape = useCallback(() => {
    if (showHistory) {
      setShowHistory(false);
      requestAnimationFrame(() => inputRef.current?.focus());
      return;
    }
    closePanel();
  }, [closePanel, showHistory]);

  // The shared trap owns the previous `event.key !== 'Escape'` guard while
  // adding Tab containment, scroll locking and nested-dialog coordination.
  const panelRef = useDialogFocusTrap<HTMLElement>({
    open,
    onClose: handlePanelEscape,
    initialFocusRef: inputRef,
    restoreFocusRef: launcherRef,
  });

  const selectConversation = async (conversation: AssistantConversation) => {
    if (isSending) return;
    setHistoryStatus('loading');
    selectedHistoryRef.current = true;
    try {
      const loaded = await thesisApi.getConversationMessages(conversation.id, {
        limit: 50,
      });
      dispatch({
        type: 'reset',
        conversationId: conversation.id,
        messages: loaded.map(fromHistoryMessage),
      });
      setShowHistory(false);
      setHistoryStatus('loaded');
    } catch {
      setHistoryStatus('error');
    }
  };

  const createConversation = async () => {
    if (isSending) return;
    try {
      const conversation = await thesisApi.createConversation(locale);
      // The API keeps a freshly-created conversation PENDING and excludes it
      // from history until the first terminal assistant commit. Keep the id
      // locally so the next send can target it without showing an empty row.
      selectedHistoryRef.current = true;
      dispatch({ type: 'reset', conversationId: conversation.id });
      setShowHistory(false);
    } catch {
      dispatch({ type: 'error', kind: 'unavailable' });
    }
  };

  const deleteConversation = async (conversationId: string) => {
    if (isSending || deletingConversationId) return;
    const confirmed = await confirm({
      title: messages.assistant.deleteConversation,
      message:
        messages.assistant.deleteConversationConfirm ??
        messages.assistant.deleteConversation,
      variant: 'destructive',
    });
    if (!confirmed) return;
    setDeletingConversationId(conversationId);
    try {
      await thesisApi.deleteConversation(conversationId);
      setHistory((current) =>
        current.filter((item) => item.id !== conversationId),
      );
      if (state.conversationId === conversationId) {
        selectedHistoryRef.current = true;
        dispatch({ type: 'reset' });
      }
    } catch {
      setHistoryStatus('error');
    } finally {
      setDeletingConversationId(undefined);
    }
  };

  const applyStreamEvent = (event: AssistantStreamEvent) => {
    if (event.type === 'meta')
      dispatch({
        type: 'meta',
        model: event.model,
        conversationId: event.conversationId,
      });
    else if (event.type === 'delta')
      dispatch({ type: 'delta', text: event.text });
    else if (event.type === 'replace')
      dispatch({ type: 'replace', text: event.text });
    else if (event.type === 'citation')
      dispatch({ type: 'citation', citation: event.citation });
    else if (event.type === 'done')
      dispatch({
        type: 'complete',
        reply: {
          messageId: event.messageId,
          reasonCode: event.reasonCode,
          degraded: event.degraded,
        },
      });
    else if (event.type === 'error') {
      if (TRANSIENT_TERMINAL_CODES.has(event.code ?? '')) {
        // A cancel, purge, or lease fence can arrive after one provider delta
        // crossed the transport boundary. Replace that transient text before
        // surfacing the stable terminal state so no partial answer remains
        // visible while the server preserves zero-message cancellation.
        dispatch({ type: 'replace', text: '' });
        dispatch({
          type: 'complete',
          reply: {
            content:
              event.code === 'TURN_CANCELLED'
                ? messages.assistant.cancelled
                : messages.assistant.unavailable,
            degraded: true,
            reasonCode: event.code,
          },
        });
      }
      throw new Error(event.code ?? 'assistant stream error');
    }
  };

  const sendMessage = async (
    event: FormEvent<HTMLFormElement>,
    retryPrompt?: string,
  ) => {
    event.preventDefault();
    const message = (retryPrompt ?? input).trim();
    if (!message || isSending) return;
    const isRetry = retryPrompt !== undefined;
    setInput('');
    setLastPrompt(message);
    dispatch({ type: 'clear-error' });
    if (isRetry) dispatch({ type: 'retry-start', prompt: message });
    else {
      dispatch({
        type: 'user',
        message: { id: `${Date.now()}-user`, role: 'user', content: message },
      });
      dispatch({
        type: 'assistant-start',
        message: {
          id: `${Date.now()}-assistant`,
          role: 'assistant',
          content: '',
          pending: true,
        },
      });
    }
    setIsSending(true);
    const controller = new AbortController();
    const generation = ++requestGenerationRef.current;
    const clientRequestId =
      activeRequestIdRef.current ??
      (isRetry ? retryRequestIdRef.current : undefined) ??
      createAssistantRequestId();
    activeRequestIdRef.current = clientRequestId;
    retryRequestIdRef.current = clientRequestId;
    const requestedConversationId = isRetry
      ? retryConversationIdRef.current
      : state.conversationId;
    activeConversationIdRef.current = requestedConversationId;
    retryConversationIdRef.current = requestedConversationId;
    activePromptRef.current = message;
    abortRef.current = controller;
    const isCurrentRequest = () =>
      requestGenerationRef.current === generation &&
      abortRef.current === controller;
    let sawDelta = false;
    let sawDone = false;
    let terminalReconciled = false;
    try {
      await thesisApi.streamChat(message, locale, {
        conversationId: requestedConversationId,
        clientRequestId,
        signal: controller.signal,
        onEvent: (streamEvent) => {
          if (!isCurrentRequest()) return;
          if (streamEvent.type === 'delta') sawDelta = true;
          if (streamEvent.type === 'done') sawDone = true;
          applyStreamEvent(streamEvent);
        },
      });
      if (!sawDone)
        throw new Error('assistant stream ended without a done event');
      terminalReconciled = true;
      reconcileHistory();
    } catch (error) {
      if (!isCurrentRequest()) return;
      if (controller.signal.aborted) {
        dispatch({
          type: 'complete',
          reply: {
            content: messages.assistant.cancelled,
            degraded: true,
            reasonCode: 'CANCELLED',
          },
        });
        retryRequestIdRef.current = undefined;
        retryConversationIdRef.current = undefined;
        return;
      }
      const streamErrorCode = error instanceof Error ? error.message : '';
      if (TRANSIENT_TERMINAL_CODES.has(streamErrorCode)) {
        // These terminal outcomes are not retryable with the same idempotency
        // key. Do not issue a JSON replay that could turn a cancellation race
        // into a misleading generic error; history remains unchanged.
        terminalReconciled = true;
        retryRequestIdRef.current = undefined;
        retryConversationIdRef.current = undefined;
        reconcileHistory();
        return;
      }
      // A stream can commit successfully and lose its final `done` frame. Always
      // reconcile through JSON with the same idempotency key before surfacing an
      // error, even when deltas were already rendered.
      // The two-argument thesisApi.chat(message, locale) compatibility contract
      // remains supported; reconciliation below supplies the conversation/key.
      try {
        const reply = await thesisApi.chat(
          message,
          locale,
          requestedConversationId,
          clientRequestId,
        );
        if (isCurrentRequest()) {
          dispatch({
            type: 'complete',
            reply: { ...reply, content: reply.answer },
          });
          terminalReconciled = true;
          retryRequestIdRef.current = undefined;
          reconcileHistory();
        }
      } catch (fallbackError) {
        if (!isCurrentRequest()) return;
        const status =
          (fallbackError as { response?: { status?: number }; status?: number })
            .response?.status ?? (fallbackError as { status?: number }).status;
        const kind =
          status === 429
            ? 'quota'
            : status === 401
              ? 'unauthorized'
              : status === 403
                ? 'forbidden'
                : typeof navigator !== 'undefined' && !navigator.onLine
                  ? 'offline'
                  : 'unavailable';
        dispatch({ type: 'error', kind });
        dispatch({
          type: 'complete',
          reply: {
            content:
              kind === 'quota'
                ? messages.assistant.quotaExceeded
                : messages.assistant.unavailable,
            degraded: true,
            reasonCode:
              kind === 'quota' ? 'QUOTA_EXCEEDED' : 'KNOWLEDGE_UNAVAILABLE',
          },
        });
      }
    } finally {
      if (isCurrentRequest()) {
        setIsSending(false);
        abortRef.current = null;
        activeRequestIdRef.current = undefined;
        activeConversationIdRef.current = undefined;
        activePromptRef.current = undefined;
        if (terminalReconciled) {
          retryRequestIdRef.current = undefined;
          retryConversationIdRef.current = undefined;
        }
      }
    }
  };

  const stopGeneration = async () => {
    const requestId = activeRequestIdRef.current;
    if (!requestId) {
      abortRef.current?.abort();
      return;
    }
    try {
      await thesisApi.cancelRequest(requestId);
      abortRef.current?.abort();
      retryRequestIdRef.current = undefined;
      retryConversationIdRef.current = undefined;
    } catch (error) {
      const status = (error as { response?: { status?: number } }).response
        ?.status;
      if (status === 409) {
        // Completion won the terminal CAS. Reconcile the committed replay before
        // aborting the reader so a late Stop click cannot erase the answer.
        try {
          const reply = await thesisApi.chat(
            activePromptRef.current ?? lastPrompt ?? '',
            locale,
            activeConversationIdRef.current,
            requestId,
          );
          dispatch({
            type: 'complete',
            reply: { ...reply, content: reply.answer },
          });
          retryRequestIdRef.current = undefined;
          retryConversationIdRef.current = undefined;
          abortRef.current?.abort();
        } catch {
          /* keep the stream alive long enough for its done frame */
        }
      } else {
        abortRef.current?.abort();
      }
    }
  };

  const setFeedback = async (messageId: string, rating: 'UP' | 'DOWN') => {
    dispatch({ type: 'feedback', messageId, rating });
    try {
      await thesisApi.setMessageFeedback(messageId, rating);
    } catch {
      /* feedback is best effort and never changes answer state */
    }
  };

  const handleComposerKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (
      event.key !== 'Enter' ||
      event.shiftKey ||
      event.nativeEvent.isComposing
    )
      return;
    event.preventDefault();
    event.currentTarget.form?.requestSubmit();
  };
  const reasonLabel = (message: ChatMessage) =>
    message.reasonCode === 'QUOTA_EXCEEDED'
      ? messages.assistant.quotaExceeded
      : message.degraded
        ? messages.assistant.degraded
        : message.reasonCode === 'NO_MATCH'
          ? messages.assistant.noMatch
          : message.reasonCode === 'ANSWERED'
            ? messages.assistant.answered
            : message.reasonCode;
  const errorLabel =
    state.error === 'quota'
      ? messages.assistant.quotaExceeded
      : state.error === 'offline'
        ? messages.assistant.offline
        : state.error === 'unauthorized'
          ? messages.assistant.sessionExpired
          : state.error === 'forbidden'
            ? messages.assistant.forbidden
            : messages.assistant.unavailable;
  return (
    <div className="fixed bottom-[calc(5.5rem+env(safe-area-inset-bottom))] right-4 z-50 w-[min(26rem,calc(100vw-2rem))] md:bottom-6 md:right-6">
      {!open ? (
        <Button
          ref={launcherRef}
          type="button"
          size="icon"
          data-assistant-launcher="thesis-mark"
          className="group ml-auto min-h-14 min-w-14 rounded-xl border border-primary-foreground/20 bg-primary text-primary-foreground shadow-xl transition-transform duration-200 ease-out hover:-translate-y-0.5 hover:bg-primary/90 active:translate-y-0 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 motion-reduce:transition-none"
          onClick={() => setOpen(true)}
          aria-label={messages.assistant.open}
          aria-expanded={open}
          title={messages.assistant.open}
        >
          <AssistantLauncherMark />
          <span
            aria-hidden="true"
            className={cn(
              'absolute right-1.5 top-1.5 size-2.5 rounded-full border-2 border-primary',
              isSending ? 'bg-[hsl(var(--accent-warm))]' : state.error ? 'bg-destructive' : 'bg-emerald-400',
            )}
          />
        </Button>
      ) : (
        <section
          ref={panelRef}
          role="dialog"
          aria-modal="true"
          aria-labelledby="assistant-panel-title"
          aria-describedby="assistant-panel-description"
          tabIndex={-1}
          className="flex h-[min(100dvh,42rem)] max-h-[min(42rem,calc(100dvh-6.5rem-env(safe-area-inset-bottom)))] flex-col overflow-hidden rounded-none border border-border/80 bg-card shadow-2xl sm:rounded-lg md:h-auto md:max-h-[min(42rem,calc(100dvh-2rem))]"
        >
          <header className="flex items-start justify-between gap-4 border-b border-border/70 bg-foreground px-4 py-4 text-background">
            <div className="flex min-w-0 items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-background/15">
                <Bot className="h-5 w-5" aria-hidden="true" />
              </div>
              <div className="min-w-0">
                <h2 id="assistant-panel-title" className="font-semibold">
                  {messages.assistant.title}
                </h2>
                <p
                  id="assistant-panel-description"
                  className="mt-1 text-xs leading-5 text-background/70"
                >
                  {messages.assistant.description}
                </p>
              </div>
            </div>
            <div className="flex shrink-0 items-center gap-1">
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="text-background hover:bg-white/10 hover:text-background"
                onClick={() => setShowHistory((current) => !current)}
                aria-label={messages.assistant.history}
                aria-expanded={showHistory}
              >
                <History className="h-4 w-4" aria-hidden="true" />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="text-background hover:bg-white/10 hover:text-background"
                onClick={closePanel}
                aria-label={messages.assistant.close}
                title={messages.assistant.close}
              >
                <X className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          </header>
          <p className="border-b border-border/70 bg-secondary/35 px-4 py-2 text-[11px] leading-5 text-muted-foreground">
            {locale === 'vi'
              ? 'Không nhập mã sinh viên, điểm hoặc thông tin cá nhân vào trợ lý.'
              : 'Do not enter student IDs, grades, or personal information into the assistant.'}
          </p>
          {showHistory ? (
            <div
              className="border-b border-border/70 bg-secondary/30 px-3 py-3"
              aria-label={messages.assistant.history}
            >
              <div className="mb-2 flex items-center justify-between gap-2">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  className="min-h-11 gap-1"
                  onClick={() => setShowHistory(false)}
                >
                  <ChevronLeft className="h-4 w-4" aria-hidden="true" />
                  {messages.assistant.backToChat}
                </Button>
                <Button
                  type="button"
                  size="sm"
                  className="min-h-11"
                  onClick={() => void createConversation()}
                >
                  {messages.assistant.newConversation}
                </Button>
              </div>
              {historyStatus === 'loading' ? (
                <p
                  className="px-2 py-4 text-sm text-muted-foreground"
                  role="status"
                >
                  {messages.assistant.historyLoading}
                </p>
              ) : null}
              {historyStatus === 'error' ? (
                <div
                  className="px-2 py-2 text-sm text-destructive"
                  role="alert"
                >
                  <p>{messages.assistant.historyUnavailable}</p>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="mt-1 min-h-11 px-0 text-destructive"
                    onClick={loadHistory}
                  >
                    <RotateCcw className="mr-1 h-4 w-4" />
                    {messages.assistant.retry}
                  </Button>
                </div>
              ) : null}
              {historyStatus !== 'loading' && !history.length ? (
                <p className="px-2 py-4 text-sm text-muted-foreground">
                  {messages.assistant.historyEmpty}
                </p>
              ) : null}
              <div className="max-h-48 space-y-1 overflow-y-auto">
                {history.map((item) => (
                  <div
                    key={item.id}
                    className="flex items-center gap-1 rounded-md border border-border/60 bg-card"
                  >
                    <button
                      type="button"
                      onClick={() => void selectConversation(item)}
                      className="min-h-11 min-w-0 flex-1 truncate px-3 text-left text-sm font-medium text-foreground hover:bg-secondary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    >
                      {item.title || messages.assistant.untitledConversation}
                    </button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      disabled={deletingConversationId === item.id}
                      className="mr-1 min-h-11 min-w-11 text-muted-foreground hover:text-destructive"
                      onClick={() => void deleteConversation(item.id)}
                      aria-label={messages.assistant.deleteConversation}
                    >
                      <Trash2 className="h-4 w-4" aria-hidden="true" />
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          ) : null}
          <div
            ref={logRef}
            onScroll={handleLogScroll}
            role="log"
            aria-live="polite"
            aria-relevant="additions text"
            aria-busy={isSending}
            className="min-h-48 flex-1 space-y-3 overflow-y-auto bg-background px-4 py-4"
          >
            {state.messages.length === 0 ? (
              <div className="flex min-h-40 flex-col items-center justify-center gap-3 text-center">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-secondary text-primary">
                  <Bot className="h-5 w-5" aria-hidden="true" />
                </div>
                <p className="max-w-xs text-sm leading-6 text-muted-foreground">
                  {messages.assistant.empty}
                </p>
              </div>
            ) : (
              state.messages.map((message) => (
                <div
                  key={message.id}
                  role="article"
                  aria-label={
                    message.role === 'user'
                      ? messages.assistant.you
                      : messages.assistant.label
                  }
                  className={cn(
                    'max-w-[90%] rounded-md px-3.5 py-2.5 text-sm leading-6',
                    message.role === 'user'
                      ? 'ml-auto bg-primary text-primary-foreground'
                      : 'border border-border/70 bg-card text-foreground',
                  )}
                >
                  <p className="whitespace-pre-wrap">
                    {message.content ||
                      (message.pending ? messages.assistant.thinking : '')}
                  </p>
                  {message.role === 'assistant' && message.reasonCode ? (
                    <p
                      className={cn(
                        'mt-2 text-[11px] uppercase tracking-wide',
                        message.degraded
                          ? 'text-amber-700'
                          : 'text-muted-foreground',
                      )}
                    >
                      {reasonLabel(message)}
                    </p>
                  ) : null}
                  {message.role === 'assistant' && message.model ? (
                    <p className="mt-1 text-[11px] text-muted-foreground">
                      {messages.assistant.model}: {message.model}
                    </p>
                  ) : null}
                  {message.citations?.length ? (
                    <div
                      className="mt-3 space-y-2 border-t border-border/70 pt-2"
                      aria-label={messages.assistant.sources}
                    >
                      {message.citations.map((citation) => (
                        <div
                          key={citation.id}
                          className="border-l-2 border-primary pl-2 text-xs leading-5"
                        >
                          <p className="font-semibold text-foreground">
                            {citation.title}
                          </p>
                          <p className="text-muted-foreground">
                            {citation.source} · {citation.locale.toUpperCase()}
                          </p>
                          <p className="mt-1 text-muted-foreground">
                            {citation.excerpt}
                          </p>
                        </div>
                      ))}
                    </div>
                  ) : null}
                  {message.role === 'assistant' &&
                  !message.pending &&
                  !message.id.startsWith('local-') ? (
                    <div
                      data-assistant-feedback={message.id}
                      className="mt-2 flex items-center justify-end gap-1 border-t border-border/50 pt-1"
                    >
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className={cn(
                          'min-h-10 min-w-10',
                          message.feedback === 'UP' &&
                            'bg-secondary text-primary',
                        )}
                        aria-label={messages.assistant.feedbackUp}
                        aria-pressed={message.feedback === 'UP'}
                        onClick={() => void setFeedback(message.id, 'UP')}
                      >
                        <ThumbsUp className="h-4 w-4" aria-hidden="true" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className={cn(
                          'min-h-10 min-w-10',
                          message.feedback === 'DOWN' &&
                            'bg-secondary text-destructive',
                        )}
                        aria-label={messages.assistant.feedbackDown}
                        aria-pressed={message.feedback === 'DOWN'}
                        onClick={() => void setFeedback(message.id, 'DOWN')}
                      >
                        <ThumbsDown className="h-4 w-4" aria-hidden="true" />
                      </Button>
                    </div>
                  ) : null}
                </div>
              ))
            )}
            {isSending ? (
              <div
                className="flex items-center gap-2 text-xs text-muted-foreground"
                role="status"
              >
                <LoaderCircle
                  className="h-4 w-4 animate-spin motion-reduce:animate-none"
                  aria-hidden="true"
                />
                {messages.assistant.thinking}
              </div>
            ) : null}
            {state.error ? (
              <div
                className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive"
                role="alert"
              >
                <p>{errorLabel}</p>
                {lastPrompt ? (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="mt-2 min-h-11 px-0 text-destructive hover:bg-transparent hover:underline"
                    onClick={(event) =>
                      void sendMessage(
                        event as unknown as FormEvent<HTMLFormElement>,
                        lastPrompt,
                      )
                    }
                  >
                    <RotateCcw className="mr-1 h-4 w-4" aria-hidden="true" />
                    {messages.assistant.retry}
                  </Button>
                ) : null}
              </div>
            ) : null}
          </div>
          <form
            onSubmit={(event) => void sendMessage(event)}
            className="border-t border-border/70 bg-card p-3"
          >
            <div className="flex items-end gap-2 rounded-md border border-border/80 bg-background p-2 focus-within:ring-2 focus-within:ring-ring">
              <textarea
                ref={inputRef}
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={handleComposerKeyDown}
                placeholder={messages.assistant.placeholder}
                rows={2}
                maxLength={2000}
                className="min-h-12 flex-1 resize-none border-0 bg-transparent px-1 py-1 text-sm leading-6 text-foreground outline-none placeholder:text-muted-foreground"
                aria-label={messages.assistant.placeholder}
              />
              {isSending ? (
                <Button
                  type="button"
                  variant="secondary"
                  size="icon"
                  className="min-h-11 min-w-11"
                  onClick={() => void stopGeneration()}
                  aria-label={messages.assistant.stop}
                >
                  <Square className="h-4 w-4" aria-hidden="true" />
                </Button>
              ) : (
                <Button
                  type="submit"
                  size="icon"
                  className="min-h-11 min-w-11"
                  disabled={!input.trim()}
                  aria-label={messages.assistant.send}
                >
                  <Send className="h-4 w-4" aria-hidden="true" />
                </Button>
              )}
            </div>
            <p className="mt-2 text-[11px] text-muted-foreground">
              {input.length}/2000
            </p>
          </form>
          {confirmationDialog}
        </section>
      )}
    </div>
  );
}
