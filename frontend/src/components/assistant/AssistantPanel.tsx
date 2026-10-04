'use client';

import {
  type FormEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { usePathname, useRouter } from 'next/navigation';
import {
  ArrowDown,
  History,
  LoaderCircle,
  RotateCcw,
  X,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import { stripLocaleFromPathname } from '@/i18n/paths';
import { useAuth } from '@/context/AuthContext';
import { thesisApi, type AssistantConversation } from '@/lib/thesis-api';
import {
  TRANSIENT_TERMINAL_CODES,
  fromHistoryMessage,
} from './assistant-reducer';
import { AssistantMascot } from './AssistantMascot';
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
  const { locale, messages, href } = useI18n();
  const router = useRouter();
  const { isLecturer, isAdmin, isSuperAdmin } = useAuth();
  const academicSuggestions =
    isAdmin || isSuperAdmin
      ? messages.assistant.adminSuggestions
      : isLecturer
        ? messages.assistant.lecturerSuggestions
        : messages.assistant.suggestions;
  const { confirm, confirmationDialog } = useConfirmationDialog();
  // Portal header/sidebar entries stay mounted when the dialog opens. The
  // floating duplicate covers mobile timetable rows and the chat composer,
  // and cannot restore focus because opening the dialog unmounts its trigger.
  const visiblePathname = usePathname();
  const pathname = stripLocaleFromPathname(visiblePathname ?? '/').pathname;
  const suppressLauncher = pathname.startsWith('/dashboard') || pathname.startsWith('/admin');
  const [open, setOpen] = useState(false);
  // Assistant retrieval scope. The academic launcher opens the default corpus;
  // the specialized launcher (Trợ lý chuyên sâu) narrows retrieval to the
  // professional SPECIALIZED domain. Scope can only narrow, never widen.
  const [mode, setMode] = useState<'academic' | 'specialized'>('academic');
  const [showHistory, setShowHistory] = useState(false);
  const [history, setHistory] = useState<AssistantConversation[]>([]);
  const [historyStatus, setHistoryStatus] =
    useState<AssistantHistoryStatus>('idle');
  const [historyCursor, setHistoryCursor] = useState<string>();
  const [loadingMoreHistory, setLoadingMoreHistory] = useState(false);
  const [messageCursor, setMessageCursor] = useState<string>();
  const [loadingMoreMessages, setLoadingMoreMessages] = useState(false);
  const [deletingConversationId, setDeletingConversationId] =
    useState<string>();
  const [userScrolled, setUserScrolled] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const [announcement, setAnnouncement] = useState('');

  // Focus returns to whichever control opened the panel (header or sidebar launcher)
  const triggerRef = useRef<HTMLElement | null>(null);
  const floatingLauncherRef = useRef<HTMLButtonElement>(null);
  const assistantDialogRef = useRef<HTMLElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const logRef = useRef<HTMLDivElement>(null);
  const selectedHistoryRef = useRef(false);
  const historyFetchedRef = useRef(false);
  const userScrolledRef = useRef(false);
  const announcedMessageRef = useRef<string>();

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
    scope: mode === 'specialized' ? 'specialized' : undefined,
    assistantMessages: messages.assistant,
    onReconcileHistory: reconcileHistory,
    onNewExchange: handleNewExchange,
  });

  const closePanel = useCallback(() => {
    if (isSending) void stopGeneration();
    else abortStream();
    setOpen(false);
    setShowHistory(false);
    selectedHistoryRef.current = false;
    historyFetchedRef.current = false;
    setHistoryStatus('idle');
    setHistoryCursor(undefined);
    requestAnimationFrame(() => {
      const trigger = triggerRef.current;
      (trigger?.isConnected ? trigger : floatingLauncherRef.current)?.focus();
    });
  }, [abortStream, isSending, stopGeneration]);

  // Follow-up chips follow the domain of the last grounded answer instead of
  // repeating the empty-state suggestions after every reply.
  const followUps = useMemo(() => {
    if (isSending) return undefined;
    // Specialized mode always suggests specialized follow-ups: the corpus
    // scope is narrower, so academic-domain chips would mislead.
    if (mode === 'specialized') return messages.assistant.specializedSuggestions;
    const lastGrounded = [...state.messages]
      .reverse()
      .find(
        (message) =>
          message.role === 'assistant' && !message.pending && message.citations?.length,
      );
    const rawDomain = lastGrounded?.citations?.[0]?.domain?.toUpperCase();
    const domain = rawDomain === 'ANNOUNCEMENTS' ? 'ANNOUNCEMENT' : rawDomain;
    const byDomain = (isLecturer && !(isAdmin || isSuperAdmin)
      ? messages.assistant.lecturerFollowUpsByDomain
      : messages.assistant.followUpsByDomain) as Record<
      string,
      readonly string[]
    >;
    return (domain && byDomain[domain]) || academicSuggestions;
  }, [state.messages, isSending, messages, mode, isLecturer, isAdmin, isSuperAdmin, academicSuggestions]);

  const latestSettledAssistant = [...state.messages]
    .reverse()
    .find((message) => message.role === 'assistant' && !message.pending);
  const latestSettledAssistantId = latestSettledAssistant?.id;

  useEffect(() => {
    if (isSending) {
      setAnnouncement(messages.assistant.thinking);
      return;
    }
    if (
      latestSettledAssistantId &&
      latestSettledAssistantId !== announcedMessageRef.current
    ) {
      announcedMessageRef.current = latestSettledAssistantId;
      setAnnouncement(messages.assistant.answerReady);
    } else {
      setAnnouncement('');
    }
  }, [
    isSending,
    latestSettledAssistantId,
    messages.assistant.answerReady,
    messages.assistant.thinking,
  ]);

  useEffect(() => {
    const handleOpen = (event: Event) => {
      triggerRef.current = document.activeElement as HTMLElement | null;
      const requestedMode = (event as CustomEvent<{ mode?: 'academic' | 'specialized' }>)
        .detail?.mode;
      setMode(requestedMode === 'specialized' ? 'specialized' : 'academic');
      setOpen(true);
    };
    window.addEventListener('open-campus-assistant', handleOpen);
    return () => window.removeEventListener('open-campus-assistant', handleOpen);
  }, []);

  useEffect(() => {
    const media = window.matchMedia('(max-width: 767px)');
    const syncMobileState = () => setIsMobile(media.matches);

    syncMobileState();
    media.addEventListener('change', syncMobileState);
    return () => media.removeEventListener('change', syncMobileState);
  }, []);

  useEffect(() => {
    if (!open) return undefined;
    const handleEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      const activeModal = document.activeElement?.closest(
        '[role="dialog"][aria-modal="true"], [role="alertdialog"][aria-modal="true"]',
      );
      if (activeModal && activeModal !== assistantDialogRef.current) return;
      event.preventDefault();
      if (showHistory) {
        setShowHistory(false);
        requestAnimationFrame(() => inputRef.current?.focus());
      } else {
        closePanel();
      }
    };
    document.addEventListener('keydown', handleEscape);
    requestAnimationFrame(() => inputRef.current?.focus());
    return () => document.removeEventListener('keydown', handleEscape);
  }, [closePanel, open, showHistory]);

  // Clicking anywhere outside the panel dismisses it, the way any floating
  // chat window behaves. Clicks inside a portal-rendered dialog (the confirm
  // modal) must not count as "outside".
  useEffect(() => {
    if (!open) return undefined;
    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target;
      if (!(target instanceof Node)) return;
      if (assistantDialogRef.current?.contains(target)) return;
      if (
        target instanceof Element &&
        target.closest('[role="dialog"], [role="alertdialog"]')
      ) {
        return;
      }
      closePanel();
    };
    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, [closePanel, open]);

  useEffect(() => {
    if (!open || !isMobile) return undefined;

    const getFocusable = () => {
      const root = assistantDialogRef.current;
      if (!root) return [];
      return Array.from(
        root.querySelectorAll<HTMLElement>(
          'button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ),
      ).filter((element) => element.getClientRects().length > 0);
    };

    const handleTab = (event: globalThis.KeyboardEvent) => {
      if (event.key !== 'Tab') return;
      const root = assistantDialogRef.current;
      if (!root) return;

      const activeModal = document.activeElement?.closest(
        '[role="dialog"][aria-modal="true"]',
      );
      if (activeModal && activeModal !== root) return;

      const focusable = getFocusable();
      if (focusable.length === 0) {
        event.preventDefault();
        root.focus();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement as HTMLElement | null;
      const activeIndex = active ? focusable.indexOf(active) : -1;

      if (!root.contains(active) || activeIndex === -1) {
        event.preventDefault();
        (event.shiftKey ? last : first).focus();
      } else if (event.shiftKey && activeIndex === 0) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && activeIndex === focusable.length - 1) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleTab);
    return () => document.removeEventListener('keydown', handleTab);
  }, [isMobile, open]);

  const loadHistory = useCallback(() => {
    if (historyStatus === 'loading') return;
    setHistoryStatus('loading');
    void thesisApi
      .listConversationsPage({ limit: 20 })
      .then(({ items, nextCursor }) => {
        setHistory(items);
        setHistoryCursor(nextCursor);
        setHistoryStatus('loaded');
        historyFetchedRef.current = true;
      })
      .catch(() => setHistoryStatus('error'));
  }, [historyStatus]);

  const loadMoreHistory = useCallback(async () => {
    if (!historyCursor || loadingMoreHistory) return;
    setLoadingMoreHistory(true);
    try {
      const { items, nextCursor } = await thesisApi.listConversationsPage({
        limit: 20,
        cursor: historyCursor,
      });
      setHistory((current) => [...current, ...items]);
      setHistoryCursor(nextCursor);
    } catch {
      setHistoryStatus('error');
    } finally {
      setLoadingMoreHistory(false);
    }
  }, [historyCursor, loadingMoreHistory]);

  const loadMoreMessages = useCallback(async () => {
    if (!state.conversationId || !messageCursor || loadingMoreMessages) return;
    setLoadingMoreMessages(true);
    try {
      const page = await thesisApi.getConversationMessages(state.conversationId, {
        limit: 50,
        cursor: messageCursor,
      });
      dispatch({
        type: 'prepend',
        messages: page.items.map(fromHistoryMessage),
      });
      setMessageCursor(page.nextCursor);
    } catch {
      dispatch({ type: 'error', kind: 'unavailable' });
    } finally {
      setLoadingMoreMessages(false);
    }
  }, [dispatch, loadingMoreMessages, messageCursor, state.conversationId]);

  useEffect(() => {
    if (
      !open ||
      historyStatus !== 'idle' ||
      historyFetchedRef.current ||
      selectedHistoryRef.current
    )
      return;
    loadHistory();
  }, [historyStatus, loadHistory, open]);

  useEffect(() => {
    const node = logRef.current;
    if (!node) return;
    if (userScrolledRef.current) return;
    if (isSending) {
      // While the answer is streaming, follow the growing tail so the user
      // sees live progress — unless they scrolled up to re-read.
      node.scrollTo({
        top: node.scrollHeight,
        behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
          ? 'auto'
          : 'smooth',
      });
      return;
    }
    // When an answer settles, pin the TOP of the newest message instead of
    // jumping past it: the opening lines are what the reader wants, and the
    // old scroll-to-bottom parked the view past the text (production
    // feedback: "mỗi lần trả lời là bị dịch xuống cuối đoạn chat").
    const articles = node.querySelectorAll('[role="article"]');
    const newest = articles[articles.length - 1] as HTMLElement | undefined;
    if (newest) {
      node.scrollTo({
        top: node.scrollTop + newest.getBoundingClientRect().top - node.getBoundingClientRect().top,
        behavior: 'auto',
      });
    } else {
      node.scrollTo({ top: node.scrollHeight });
    }
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
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
        ? 'auto'
        : 'smooth',
    });
  };

  const selectConversation = async (conversation: AssistantConversation) => {
    if (isSending) {
      setAnnouncement(messages.assistant.busyNotice);
      return;
    }
    setHistoryStatus('loading');
    selectedHistoryRef.current = true;
    try {
      const loaded = await thesisApi.getConversationMessages(conversation.id, {
        limit: 50,
      });
      resetConversation(conversation.id, loaded.items.map(fromHistoryMessage));
      setMessageCursor(loaded.nextCursor);
      setShowHistory(false);
      setHistoryStatus('loaded');
    } catch {
      setHistoryStatus('error');
    }
  };

  const createConversation = async () => {
    if (isSending) {
      setAnnouncement(messages.assistant.busyNotice);
      return;
    }
    if (state.messages.length === 0) {
      selectedHistoryRef.current = true;
      resetConversation(state.conversationId);
      setMessageCursor(undefined);
      setShowHistory(false);
      return;
    }
    try {
      const conversation = await thesisApi.createConversation(locale);
      selectedHistoryRef.current = true;
      resetConversation(conversation.id);
      setMessageCursor(undefined);
      setShowHistory(false);
    } catch {
      dispatch({ type: 'error', kind: 'unavailable' });
    }
  };

  const deleteConversation = async (conversationId: string) => {
    if (isSending) {
      setAnnouncement(messages.assistant.busyNotice);
      return;
    }
    if (deletingConversationId) return;
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
        setMessageCursor(undefined);
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
            : state.error === 'turn-in-progress'
              ? messages.assistant.turnInProgress
              : messages.assistant.unavailable;

  // Asking requires a live session: once the backend refuses with 401 the
  // composer locks and the only offered action is signing in again — a retry
  // here could never succeed against the same expired token.
  const authRequired = state.error === 'unauthorized';
  const goSignIn = () =>
    router.push(`${href('/login')}?reason=session-expired`);

  return (
    <>
      {open || !suppressLauncher ? (
        <div
          className={cn(
            'fixed z-50',
            open
              ? // Mobile: full-screen sheet; desktop: floating card bottom-right.
                'inset-0 md:inset-auto md:bottom-6 md:right-6 md:w-[min(24rem,calc(100vw-2rem))]'
              : 'bottom-[calc(5.5rem+env(safe-area-inset-bottom))] right-4 md:bottom-6 md:right-6',
          )}
        >
        {open ? (
          <section
            ref={assistantDialogRef}
            role="dialog"
            aria-modal={isMobile}
            tabIndex={-1}
            aria-labelledby="assistant-panel-title"
            aria-describedby="assistant-panel-description"
            className="relative flex h-full supports-[height:100dvh]:h-[100dvh] flex-col overscroll-contain overflow-hidden border border-primary/25 bg-card shadow-[0_20px_50px_rgba(0,35,90,0.22)] pb-[env(safe-area-inset-bottom)] md:h-[min(42rem,calc(100dvh-2rem))] md:max-h-[min(42rem,calc(100dvh-2rem))] md:rounded-2xl md:pb-0"
          >
            {/* Header with quick New Chat and a neutral assistant identity indicator */}
            <header className="flex items-center justify-between gap-3 border-b border-primary-foreground/15 bg-gradient-to-r from-primary via-[#004eab] to-[#005fcf] px-4 py-3 text-white shadow-sm dark:from-[#0b3a70] dark:via-[#004eab] dark:to-[#005fcf]">
              <div className="flex min-w-0 items-center gap-2.5">
                <div className="relative flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-white/15 text-white shadow-inner backdrop-blur">
                  <AssistantMascot
                    className="h-5 w-5"
                    state={isSending ? 'thinking' : 'idle'}
                    variant="detailed"
                  />
                  <span
                    className="absolute -bottom-0.5 -right-0.5 inline-flex h-2.5 w-2.5 rounded-full border border-white bg-white/75"
                    aria-hidden="true"
                  />
                </div>
                <div className="min-w-0">
                  <h2
                    id="assistant-panel-title"
                    className="flex min-w-0 items-center gap-1.5 text-sm font-semibold text-white"
                  >
                    <span className="truncate">
                      {mode === 'specialized'
                        ? messages.assistant.specializedTitle
                        : messages.assistant.title}
                    </span>
                  </h2>
                  <p
                    id="assistant-panel-description"
                    className="truncate text-[11px] text-white/85"
                  >
                    {mode === 'specialized'
                      ? messages.assistant.specializedTagline
                      : messages.assistant.description}
                  </p>
                </div>
              </div>

              <div className="flex shrink-0 items-center gap-0.5">
                {/* Direct New Chat button */}
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="min-h-11 min-w-11 rounded-lg text-white/80 hover:bg-white/15 hover:text-white"
                  // An expired session must not offer New Chat: its 401 would
                  // replace the unauthorized state with a generic error and
                  // visually unlock a composer that still cannot send.
                  disabled={authRequired}
                  onClick={() => void createConversation()}
                  aria-label={messages.assistant.newConversation}
                  title={messages.assistant.newConversation}
                >
                  <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
                </Button>

                {/* History Drawer toggle */}
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="min-h-11 min-w-11 rounded-lg text-white/80 hover:bg-white/15 hover:text-white"
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
                  className="min-h-11 min-w-11 rounded-lg text-white/80 hover:bg-white/15 hover:text-white"
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
                nextCursor={historyCursor}
                loadingMore={loadingMoreHistory}
                onLoadMore={() => void loadMoreHistory()}
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
            <div className="sr-only" role="status" aria-live="polite" aria-atomic="true">
              {announcement}
            </div>
            <div className="relative min-h-0 flex-1 overflow-hidden">
              <div
                ref={logRef}
                onScroll={handleLogScroll}
                role="log"
                // Announce only settled answers; streaming deltas would flood
                // screen readers with partial tokens.
                aria-live={isSending ? 'off' : 'polite'}
                aria-relevant="additions text"
                aria-busy={isSending}
                className="absolute inset-0 space-y-3 overflow-y-auto bg-background px-3.5 py-3.5 pb-20 md:pb-4"
              >
              {messageCursor && state.messages.length > 0 ? (
                <div className="flex justify-center">
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    disabled={loadingMoreMessages}
                    className="min-h-11 text-xs text-muted-foreground"
                    onClick={() => void loadMoreMessages()}
                  >
                    {loadingMoreMessages
                      ? messages.assistant.historyLoading
                      : messages.assistant.loadMoreMessages}
                  </Button>
                </div>
              ) : null}
              {state.messages.length === 0 ? (
                <div className="flex min-h-44 flex-col items-center justify-center gap-3 text-center p-3">
                  <div className="space-y-1">
                    <p className="text-sm font-semibold text-foreground">
                      {messages.assistant.greeting}
                    </p>
                    <p className="max-w-xs text-xs text-muted-foreground leading-relaxed">
                      {mode === 'specialized'
                        ? messages.assistant.specializedEmpty
                        : messages.assistant.empty}
                    </p>
                  </div>
                  <div className="mt-1 flex flex-wrap justify-center gap-1.5 max-w-xs">
                    {(mode === 'specialized'
                      ? messages.assistant.specializedSuggestions
                      : academicSuggestions
                    ).map((suggestion) => (
                      <button
                        key={suggestion}
                        type="button"
                        onClick={() => void sendMessage(undefined, suggestion)}
                        disabled={isSending || authRequired}
                        className="min-h-11 rounded-md border border-primary/20 bg-primary/5 px-3 py-2 text-[11px] font-medium text-primary transition-colors hover:border-primary/40 hover:bg-primary/10"
                      >
                        {suggestion}
                      </button>
                    ))}
                  </div>
                </div>
              ) : (
                <AssistantMessages
                  messageList={state.messages}
                  onFeedback={(messageId, rating, reason) =>
                    void setFeedback(messageId, rating, reason)
                  }
                  followUps={followUps}
                  followUpsDisabled={authRequired}
                  followUpsLabel={messages.assistant.followUpsLabel}
                  onFollowUp={(suggestion) =>
                    void sendMessage(undefined, suggestion)
                  }
                  scope={mode === 'specialized' ? 'specialized' : 'academic'}
                />
              )}

              {isSending && !state.messages.some((m) => m.pending) ? (
                <div
                  className="flex items-center gap-2 text-xs text-muted-foreground pl-9"
                  aria-hidden="true"
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
                  {authRequired ? (
                    <Button
                      type="button"
                      size="sm"
                      className="mt-2 min-h-10 px-3"
                      onClick={goSignIn}
                    >
                      {messages.assistant.signInAction}
                    </Button>
                  ) : lastPrompt ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="mt-2 min-h-10 px-0 text-destructive hover:bg-transparent hover:underline"
                      onClick={(event) =>
                        void sendMessage(event, lastPrompt, { retry: true })
                      }
                    >
                      <RotateCcw className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
                      {messages.assistant.retry}
                    </Button>
                  ) : null}
                </div>
              ) : null}

              {/* A degraded answer without citations answered from the
                  fallback chain, not the reviewed corpus; offer the same
                  retry affordance as a hard error so the user can re-ask
                  once the provider recovers instead of staring at a
                  dead-end badge.
                  Documented exclusion list — a retry here can never succeed
                  or would violate the turn fence:
                  - QUOTA_EXCEEDED: retrying a daily-quota refusal cannot
                    succeed before the reset time, so that state keeps its
                    own recovery message.
                  - terminalFence messages: locally synthesized
                    reconciliation-terminal outcomes (404 turn-not-found, a
                    409 with any code other than TURN_IN_PROGRESS —
                    IDEMPOTENCY_CONFLICT and friends — plus auth and quota).
                    The idempotency key was retired server-side, and a
                    409-with-another-code means another owner/window holds
                    the turn; offering retry could double-commit or fight
                    the fence. Genuine transient outages (degraded answers
                    that completed through the fallback chain) stay
                    retryable. */}
              {!state.error &&
              lastPrompt &&
              (() => {
                const last = state.messages[state.messages.length - 1];
                return (
                  last &&
                  last.role === 'assistant' &&
                  !last.pending &&
                  last.degraded === true &&
                  last.terminalFence !== true &&
                  last.reasonCode !== 'QUOTA_EXCEEDED' &&
                  !(last.citations && last.citations.length > 0)
                );
              })() ? (
                <div className="pl-9">
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="min-h-10 px-0 text-muted-foreground hover:bg-transparent hover:underline"
                    onClick={(event) =>
                      void sendMessage(event, lastPrompt, { retry: true })
                    }
                  >
                    <RotateCcw className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
                    {messages.assistant.retry}
                  </Button>
                </div>
              ) : null}
              </div>

              {/* Keep the scroll-to-bottom action inside the log viewport so
                  it never covers the composer when the input wraps. */}
              {userScrolled ? (
                <button
                  type="button"
                  onClick={scrollToBottom}
                  className="absolute bottom-3 right-4 z-20 flex min-h-11 items-center gap-1 rounded-md border border-primary/20 bg-primary px-3 py-2 text-xs font-medium text-primary-foreground shadow-md transition-transform hover:scale-105 active:scale-95 motion-reduce:transform-none motion-reduce:transition-none"
                  aria-label={messages.assistant.scrollToLatest}
                >
                  <ArrowDown className="h-3 w-3" aria-hidden="true" />
                  <span>{messages.assistant.scrollToLatest}</span>
                </button>
              ) : null}
            </div>

            {/* Message composer */}
            <AssistantComposer
              input={input}
              inputRef={inputRef}
              isSending={isSending}
              onInputChange={setInput}
              onSubmit={(event) => void sendMessage(event)}
              onStop={() => void stopGeneration()}
              scope={mode}
              authLocked={authRequired}
            />
          </section>
        ) : (
          /* Floating chat launcher: one round mascot button in the bottom-right
             corner whenever the panel is closed. */
          <button
            ref={floatingLauncherRef}
            type="button"
            onClick={() => {
              triggerRef.current = document.activeElement as HTMLElement | null;
              setMode('academic');
              setOpen(true);
            }}
            aria-label={messages.assistant.open}
            title={messages.assistant.open}
            className="group relative flex h-14 w-14 items-center justify-center rounded-full bg-gradient-to-br from-primary via-[#004eab] to-[#005fcf] text-white shadow-[0_12px_28px_rgba(0,35,90,0.35)] ring-1 ring-black/5 transition-transform duration-150 hover:scale-105 active:scale-95 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 motion-reduce:transform-none motion-reduce:transition-none"
          >
            <AssistantMascot className="h-7 w-7 transition-transform duration-200 group-hover:scale-110" variant="detailed" />
            <span
              className="absolute -right-0.5 -top-0.5 inline-flex h-3.5 w-3.5 rounded-full bg-[var(--portal-yellow)] ring-2 ring-card shadow-xs"
              aria-hidden="true"
            />
          </button>
        )}
        </div>
      ) : null}
      {confirmationDialog}
    </>
  );
}
