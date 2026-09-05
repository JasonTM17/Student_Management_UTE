'use client';

import {
  FormEvent,
  useCallback,
  useEffect,
  useReducer,
  useRef,
  useState,
} from 'react';
import {
  Bookmark,
  Bot,
  History,
  LoaderCircle,
  MessageCircle,
  RotateCcw,
  X,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import {
  createAssistantRequestId,
  thesisApi,
  type AssistantConversation,
  type AssistantStreamEvent,
} from '@/lib/thesis-api';
import {
  TRANSIENT_TERMINAL_CODES,
  assistantReducer,
  fromHistoryMessage,
  initialState,
  type ChatMessage,
} from './assistant-reducer';
import { AssistantMessages } from './AssistantMessages';
import {
  AssistantHistoryPanel,
  type AssistantHistoryStatus,
} from './AssistantHistoryPanel';
import { AssistantComposer } from './AssistantComposer';

function AssistantLauncherMark() {
  return (
    <span
      className="relative inline-flex size-9 items-center justify-center"
      aria-hidden="true"
    >
      <MessageCircle className="size-8 stroke-[1.8] text-white transition-transform duration-200 ease-out group-hover:scale-105" />
      <Bookmark className="absolute left-1/2 top-1/2 size-3.5 -translate-x-1/2 -translate-y-[42%] fill-white/30 stroke-[2.2] text-white" />
      <span className="absolute -top-1 -right-1 flex h-3 w-3">
        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
        <span className="relative inline-flex rounded-full h-3 w-3 bg-emerald-500 border-2 border-white"></span>
      </span>
    </span>
  );
}

export function AssistantPanel() {
  const { locale, messages } = useI18n();
  const { confirm, confirmationDialog } = useConfirmationDialog();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const handleOpen = () => setOpen(true);
    window.addEventListener('open-campus-assistant', handleOpen);
    return () => window.removeEventListener('open-campus-assistant', handleOpen);
  }, []);
  const [showHistory, setShowHistory] = useState(false);
  const [input, setInput] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [history, setHistory] = useState<AssistantConversation[]>([]);
  const [historyStatus, setHistoryStatus] = useState<AssistantHistoryStatus>('idle');
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

  useEffect(() => {
    if (!open) return undefined;
    const handleEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      if (showHistory) {
        setShowHistory(false);
        requestAnimationFrame(() => inputRef.current?.focus());
      } else {
        setOpen(false);
        requestAnimationFrame(() => launcherRef.current?.focus());
      }
    };
    document.addEventListener('keydown', handleEscape);
    requestAnimationFrame(() => inputRef.current?.focus());
    return () => document.removeEventListener('keydown', handleEscape);
  }, [open, showHistory]);

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

  const closePanel = () => {
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
  };

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
    const shouldDelete = await confirm({
      title: messages.assistant.deleteConversation,
      message:
        messages.assistant.deleteConversationConfirm ??
        messages.assistant.deleteConversation,
      confirmText: messages.assistant.deleteConversation,
      cancelText: messages.common.actions.cancel,
      variant: 'destructive',
    });
    if (!shouldDelete) return;
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
    <>
    <div
      className={cn(
        'fixed z-50',
        open
          ? 'bottom-[calc(5.5rem+env(safe-area-inset-bottom))] right-4 w-[min(23rem,calc(100vw-2rem))] md:bottom-6 md:right-6'
          : 'bottom-[calc(5.5rem+env(safe-area-inset-bottom))] right-4 md:bottom-6 md:right-6',
      )}
    >
      {!open ? (
        <div className="flex items-center gap-2">
          <span className="hidden lg:inline-flex items-center gap-1.5 rounded-full border border-primary/20 bg-card/95 px-3 py-1.5 text-xs font-semibold text-primary shadow-lg backdrop-blur transition-all duration-200 group-hover:border-primary/40 pointer-events-none">
            ✨ {messages.assistant.launcherHint}
          </span>
          <Button
            ref={launcherRef}
            type="button"
            size="icon"
            data-assistant-launcher="campus-mark"
            className="group relative ml-auto min-h-12 min-w-12 rounded-2xl border-2 border-white/40 bg-gradient-to-tr from-primary via-[#004eab] to-[#0070f3] text-white shadow-[0_10px_30px_rgba(0,63,135,0.38)] transition-all duration-200 ease-out hover:-translate-y-0.5 hover:shadow-[0_14px_36px_rgba(0,63,135,0.48)] hover:scale-105 active:translate-y-0 active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 motion-reduce:transition-none"
            onClick={() => setOpen(true)}
            aria-label={messages.assistant.open}
            title={messages.assistant.open}
          >
            <AssistantLauncherMark />
          </Button>
        </div>
      ) : (
        <section
          role="dialog"
          aria-modal="false"
          aria-labelledby="assistant-panel-title"
          aria-describedby="assistant-panel-description"
          className="flex max-h-[min(42rem,calc(100dvh-6.5rem-env(safe-area-inset-bottom)))] flex-col overflow-hidden rounded-2xl border border-primary/25 bg-card shadow-[0_20px_50px_rgba(0,35,90,0.22)] md:max-h-[min(42rem,calc(100dvh-2rem))]"
        >
          <header className="flex items-start justify-between gap-4 border-b border-primary-foreground/15 bg-gradient-to-r from-primary via-[#004eab] to-[#005fcf] px-4 py-3 text-white shadow-sm">
            <div className="flex min-w-0 items-start gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-white/15 text-white shadow-inner backdrop-blur">
                <Bot className="h-5 w-5" aria-hidden="true" />
              </div>
              <div className="min-w-0">
                <h2 id="assistant-panel-title" className="font-semibold text-white text-base">
                  {messages.assistant.title}
                </h2>
                <p
                  id="assistant-panel-description"
                  className="mt-0.5 text-xs leading-5 text-white/85"
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
                className="text-white/80 hover:bg-white/15 hover:text-white rounded-lg h-9 w-9"
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
                className="text-white/80 hover:bg-white/15 hover:text-white rounded-lg h-9 w-9"
                onClick={closePanel}
                aria-label={messages.assistant.close}
                title={messages.assistant.close}
              >
                <X className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          </header>
          {showHistory ? (
            <AssistantHistoryPanel
              history={history}
              historyStatus={historyStatus}
              deletingConversationId={deletingConversationId}
              onBack={() => setShowHistory(false)}
              onCreate={() => void createConversation()}
              onSelect={(conversation) => void selectConversation(conversation)}
              onDelete={(conversationId) => void deleteConversation(conversationId)}
              onRetry={loadHistory}
            />
          ) : null}
          <div
            ref={logRef}
            onScroll={handleLogScroll}
            role="log"
            aria-live="polite"
            aria-relevant="additions text"
            aria-busy={isSending}
            className="min-h-44 flex-1 space-y-3 overflow-y-auto bg-background px-3 py-3"
          >
            {state.messages.length === 0 ? (
              <div className="flex min-h-44 flex-col items-center justify-center gap-3 text-center p-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary/10 text-primary shadow-inner">
                  <Bot className="h-5 w-5 animate-bounce" aria-hidden="true" />
                </div>
                <div className="space-y-1">
                  <p className="text-sm font-semibold text-foreground">
                    {messages.assistant.greeting}
                  </p>
                  <p className="max-w-xs text-xs text-muted-foreground leading-relaxed">
                    {messages.assistant.empty}
                  </p>
                </div>
                <div className="mt-1 flex flex-wrap justify-center gap-1.5 max-w-xs">
                  {messages.assistant.suggestions.map((suggestion) => (
                    <button
                      key={suggestion}
                      type="button"
                      onClick={() => setInput(suggestion)}
                      className="rounded-full border border-primary/20 bg-primary/5 px-2.5 py-1 text-[11px] font-medium text-primary hover:bg-primary/10 hover:border-primary/40 transition-colors"
                    >
                      {suggestion}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <AssistantMessages
                messageList={state.messages}
                onFeedback={(messageId, rating) => void setFeedback(messageId, rating)}
              />
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
          <AssistantComposer
            input={input}
            inputRef={inputRef}
            isSending={isSending}
            onInputChange={setInput}
            onSubmit={(event) => void sendMessage(event)}
            onStop={() => void stopGeneration()}
          />
        </section>
      )}
    </div>
    {confirmationDialog}
    </>
  );
}
