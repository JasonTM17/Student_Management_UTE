'use client';

import {
  type FormEvent,
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';
import {
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

  // Focus returns to whichever control opened the panel (header or sidebar launcher)
  const triggerRef = useRef<HTMLElement | null>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const logRef = useRef<HTMLDivElement>(null);
  const selectedHistoryRef = useRef(false);
  const historyFetchedRef = useRef(false);
  const userScrolledRef = useRef(false);

  const reconcileHistory = useCallback(() => {
    // A server-created conversation remains hidden until its terminal commit;
    // clear the one-fetch latch so the next render observes the committed row.
    historyFetchedRef.current = false;
    selectedHistoryRef.current = false;
    setHistoryStatus('idle');
  }, []);

  const handleNewExchange = useCallback(() => {
    userScrolledRef.current = false;
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
    // Respect the reader's position while streaming: never yank them back to
    // the bottom once they scrolled up to reread earlier messages.
    if (userScrolledRef.current) return;
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
            ? 'bottom-[calc(5.5rem+env(safe-area-inset-bottom))] right-4 w-[min(23rem,calc(100vw-2rem))] md:bottom-6 md:right-6'
            : 'bottom-[calc(5.5rem+env(safe-area-inset-bottom))] right-4 md:bottom-6 md:right-6',
        )}
      >
        {open ? (
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
                  followUps={isSending ? undefined : messages.assistant.suggestions}
                  followUpsLabel={messages.assistant.followUpsLabel}
                  onFollowUp={(suggestion) => setInput(suggestion)}
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
                      onClick={(event) => void sendMessage(event, lastPrompt)}
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
        ) : null}
      </div>
      {confirmationDialog}
    </>
  );
}
