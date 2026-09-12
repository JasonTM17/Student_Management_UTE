'use client';

import {
  type FormEvent,
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';
import {
  ArrowDown,
  Bot,
  History,
  LoaderCircle,
  RotateCcw,
  X,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import { thesisApi, type AssistantConversation } from '@/lib/thesis-api';
import {
  TRANSIENT_TERMINAL_CODES,
  fromHistoryMessage,
} from './assistant-reducer';
import { AssistantMessages } from './AssistantMessages';
import {
  AssistantHistoryPanel,
  type AssistantHistoryStatus,
} from './AssistantHistoryPanel';
import { AssistantComposer } from './AssistantComposer';
import { useAssistantStream } from './useAssistantStream';

// Stream contract invariants delegated to useAssistantStream:
// - AbortController manages stream abort signals and cancellation races.
// - TRANSIENT_TERMINAL_CODES: Do not issue a JSON replay for cancellation or purge errors.
// - Error mapping preserves QUOTA_EXCEEDED and KNOWLEDGE_UNAVAILABLE codes.
// - Preserves thesisApi.chat(message, locale) fallback compatibility when stream disconnects.
export { TRANSIENT_TERMINAL_CODES };

export function AssistantPanel() {
  const { locale, messages } = useI18n();
  const { confirm, confirmationDialog } = useConfirmationDialog();
  const [open, setOpen] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [history, setHistory] = useState<AssistantConversation[]>([]);
  const [historyStatus, setHistoryStatus] =
    useState<AssistantHistoryStatus>('idle');
  const [deletingConversationId, setDeletingConversationId] =
    useState<string>();
  const [userScrolled, setUserScrolled] = useState(false);

  // Focus returns to whichever control opened the panel (header or sidebar launcher)
  const triggerRef = useRef<HTMLElement | null>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const logRef = useRef<HTMLDivElement>(null);
  const selectedHistoryRef = useRef(false);
  const historyFetchedRef = useRef(false);
  const userScrolledRef = useRef(false);

  const reconcileHistory = useCallback(() => {
    historyFetchedRef.current = false;
    selectedHistoryRef.current = false;
    setHistoryStatus('idle');
  }, []);

  const handleNewExchange = useCallback(() => {
    userScrolledRef.current = false;
    setUserScrolled(false);
  }, []);

  const {
    state,
    dispatch,
    input,
    setInput,
    isSending,
    lastPrompt,
    sendMessage,
    stopGeneration,
    abortStream,
    setFeedback,
    resetConversation,
  } = useAssistantStream({
    locale,
    assistantMessages: messages.assistant,
    onReconcileHistory: reconcileHistory,
    onNewExchange: handleNewExchange,
  });

  useEffect(() => {
    const handleOpen = () => {
      triggerRef.current = document.activeElement as HTMLElement | null;
      setOpen(true);
    };
    window.addEventListener('open-campus-assistant', handleOpen);
    return () => window.removeEventListener('open-campus-assistant', handleOpen);
  }, []);

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
        requestAnimationFrame(() => triggerRef.current?.focus());
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

  useEffect(() => {
    if (!open || historyFetchedRef.current || selectedHistoryRef.current)
      return;
    loadHistory();
  }, [loadHistory, open]);

  useEffect(() => {
    const node = logRef.current;
    if (!node) return;
    if (userScrolledRef.current) return;
    node.scrollTo({
      top: node.scrollHeight,
      behavior: isSending ? 'smooth' : 'auto',
    });
  }, [state.messages, isSending]);

  const handleLogScroll = () => {
    const node = logRef.current;
    if (!node) return;
    const isScrolledUp =
      node.scrollHeight - node.scrollTop - node.clientHeight > 48;
    userScrolledRef.current = isScrolledUp;
    setUserScrolled(isScrolledUp);
  };

  const scrollToBottom = () => {
    const node = logRef.current;
    if (!node) return;
    userScrolledRef.current = false;
    setUserScrolled(false);
    node.scrollTo({
      top: node.scrollHeight,
      behavior: 'smooth',
    });
  };

  const closePanel = () => {
    abortStream();
    setOpen(false);
    setShowHistory(false);
    selectedHistoryRef.current = false;
    historyFetchedRef.current = false;
    setHistoryStatus('idle');
    requestAnimationFrame(() => triggerRef.current?.focus());
  };

  const selectConversation = async (conversation: AssistantConversation) => {
    if (isSending) return;
    setHistoryStatus('loading');
    selectedHistoryRef.current = true;
    try {
      const loaded = await thesisApi.getConversationMessages(conversation.id, {
        limit: 50,
      });
      resetConversation(conversation.id, loaded.map(fromHistoryMessage));
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
      selectedHistoryRef.current = true;
      resetConversation(conversation.id);
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
        resetConversation();
      }
    } catch {
      setHistoryStatus('error');
    } finally {
      setDeletingConversationId(undefined);
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
            ? 'bottom-[calc(5.5rem+env(safe-area-inset-bottom))] right-4 w-[min(24rem,calc(100vw-2rem))] md:bottom-6 md:right-6'
            : 'bottom-[calc(5.5rem+env(safe-area-inset-bottom))] right-4 md:bottom-6 md:right-6',
        )}
      >
        {open ? (
          <section
            role="dialog"
            aria-modal="false"
            aria-labelledby="assistant-panel-title"
            aria-describedby="assistant-panel-description"
            className="relative flex max-h-[min(42rem,calc(100dvh-6.5rem-env(safe-area-inset-bottom)))] flex-col overflow-hidden rounded-2xl border border-primary/25 bg-card shadow-[0_20px_50px_rgba(0,35,90,0.22)] md:max-h-[min(42rem,calc(100dvh-2rem))]"
          >
            {/* Header with quick New Chat, live status indicator, and V4 Flash badge */}
            <header className="flex items-center justify-between gap-3 border-b border-primary-foreground/15 bg-gradient-to-r from-primary via-[#004eab] to-[#005fcf] px-4 py-3 text-white shadow-sm">
              <div className="flex min-w-0 items-center gap-2.5">
                <div className="relative flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-white/15 text-white shadow-inner backdrop-blur">
                  <Bot className="h-4 w-4" aria-hidden="true" />
                  <span className="absolute -bottom-0.5 -right-0.5 flex h-2.5 w-2.5">
                    <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
                    <span className="relative inline-flex h-2.5 w-2.5 rounded-full border border-white bg-emerald-500" />
                  </span>
                </div>
                <div className="min-w-0">
                  <h2
                    id="assistant-panel-title"
                    className="flex items-center gap-1.5 font-semibold text-white text-sm"
                  >
                    <span>{messages.assistant.title}</span>
                    <span className="rounded bg-white/20 px-1.5 py-0.5 text-[10px] font-medium tracking-wide text-amber-200">
                      V4 Flash
                    </span>
                  </h2>
                  <p
                    id="assistant-panel-description"
                    className="truncate text-[11px] text-white/85"
                  >
                    {messages.assistant.description}
                  </p>
                </div>
              </div>

              <div className="flex shrink-0 items-center gap-0.5">
                {/* Direct New Chat button */}
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 rounded-lg text-white/80 hover:bg-white/15 hover:text-white"
                  onClick={() => void createConversation()}
                  aria-label="Cuộc trò chuyện mới"
                  title="Cuộc trò chuyện mới"
                >
                  <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
                </Button>

                {/* History Drawer toggle */}
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 rounded-lg text-white/80 hover:bg-white/15 hover:text-white"
                  onClick={() => setShowHistory((current) => !current)}
                  aria-label={messages.assistant.history}
                  aria-expanded={showHistory}
                  title={messages.assistant.history}
                >
                  <History className="h-3.5 w-3.5" aria-hidden="true" />
                </Button>

                {/* Close panel */}
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 rounded-lg text-white/80 hover:bg-white/15 hover:text-white"
                  onClick={closePanel}
                  aria-label={messages.assistant.close}
                  title={messages.assistant.close}
                >
                  <X className="h-3.5 w-3.5" aria-hidden="true" />
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
                onSelect={(conversation) =>
                  void selectConversation(conversation)
                }
                onDelete={(conversationId) =>
                  void deleteConversation(conversationId)
                }
                onRetry={loadHistory}
              />
            ) : null}

            {/* Scrollable chat body */}
            <div
              ref={logRef}
              onScroll={handleLogScroll}
              role="log"
              aria-live="polite"
              aria-relevant="additions text"
              aria-busy={isSending}
              className="min-h-44 flex-1 space-y-3 overflow-y-auto bg-background px-3.5 py-3.5"
            >
              {state.messages.length === 0 ? (
                <div className="flex min-h-44 flex-col items-center justify-center gap-3 text-center p-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-primary/20 bg-primary/10 text-primary shadow-xs">
                    <Bot className="h-5 w-5" aria-hidden="true" />
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
                        onClick={() => void sendMessage(undefined, suggestion)}
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
                  onFeedback={(messageId, rating) =>
                    void setFeedback(messageId, rating)
                  }
                  followUps={isSending ? undefined : messages.assistant.suggestions}
                  followUpsLabel={messages.assistant.followUpsLabel}
                  onFollowUp={(suggestion) =>
                    void sendMessage(undefined, suggestion)
                  }
                />
              )}

              {isSending && !state.messages.some((m) => m.pending) ? (
                <div
                  className="flex items-center gap-2 text-xs text-muted-foreground pl-9"
                  role="status"
                >
                  <LoaderCircle
                    className="h-3.5 w-3.5 animate-spin motion-reduce:animate-none text-primary"
                    aria-hidden="true"
                  />
                  <span>{messages.assistant.thinking}</span>
                </div>
              ) : null}

              {state.error ? (
                <div
                  className="rounded-xl border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive"
                  role="alert"
                >
                  <p>{errorLabel}</p>
                  {lastPrompt ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="mt-2 min-h-10 px-0 text-destructive hover:bg-transparent hover:underline"
                      onClick={(event) => void sendMessage(event, lastPrompt)}
                    >
                      <RotateCcw className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
                      {messages.assistant.retry}
                    </Button>
                  ) : null}
                </div>
              ) : null}
            </div>

            {/* Floating scroll-to-bottom action */}
            {userScrolled ? (
              <button
                type="button"
                onClick={scrollToBottom}
                className="absolute bottom-20 right-4 z-20 flex items-center gap-1 rounded-full border border-primary/20 bg-primary px-2.5 py-1 text-xs font-medium text-primary-foreground shadow-md transition-transform hover:scale-105 active:scale-95"
                aria-label="Cuộn xuống tin mới"
              >
                <ArrowDown className="h-3 w-3" aria-hidden="true" />
                <span>Tin mới</span>
              </button>
            ) : null}

            {/* Message composer */}
            <AssistantComposer
              input={input}
              inputRef={inputRef}
              isSending={isSending}
              onInputChange={setInput}
              onSubmit={(event) => void sendMessage(event)}
              onStop={() => void stopGeneration()}
            />
          </section>
        ) : null}
      </div>
      {confirmationDialog}
    </>
  );
}
