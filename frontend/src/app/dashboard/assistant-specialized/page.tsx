'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { LoaderCircle, RotateCcw } from 'lucide-react';
import { useI18n } from '@/i18n';
import { useAssistantStream } from '@/components/assistant/useAssistantStream';
import { AssistantMessages } from '@/components/assistant/AssistantMessages';
import { AssistantComposer } from '@/components/assistant/AssistantComposer';
import { AssistantMascot } from '@/components/assistant/AssistantMascot';

/**
 * Dedicated full-page workspace for the SPECIALIZED assistant ("Trợ lý AI
 * chuyên sâu"). The sidebar launcher used to dispatch an open-event for the
 * floating drawer, which read as "trang này chưa có" — the launcher now
 * routes here: a real route, a real conversation pinned to
 * scope='specialized', with its own history and follow-up chips.
 */
export default function AssistantSpecializedPage() {
  const { locale, messages, href } = useI18n();
  const router = useRouter();
  const vi = locale === 'vi';
  const logRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const userScrolledRef = useRef(false);

  const handleNewExchange = useCallback(() => {
    userScrolledRef.current = false;
  }, []);

  const {
    state,
    input,
    setInput,
    isSending,
    lastPrompt,
    sendMessage,
    stopGeneration,
    setFeedback,
    resetConversation,
  } = useAssistantStream({
    locale,
    scope: 'specialized',
    assistantMessages: messages.assistant,
    onNewExchange: handleNewExchange,
  });

  // Anchor the view at the TOP of the newest answer when it settles (same
  // contract as the drawer panel), and follow the tail only while streaming
  // and the user has not scrolled up to re-read.
  useEffect(() => {
    const node = logRef.current;
    if (!node) return;
    if (userScrolledRef.current) return;
    if (isSending) {
      node.scrollTo({
        top: node.scrollHeight,
        behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches
          ? 'auto'
          : 'smooth',
      });
      return;
    }
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
    userScrolledRef.current =
      node.scrollHeight - node.scrollTop - node.clientHeight > 48;
  };

  const errorLabel = state.error === 'offline'
    ? messages.assistant.offline
    : state.error === 'turn-in-progress'
      ? messages.assistant.turnInProgress
      : state.error === 'quota'
        ? messages.assistant.quotaExceeded
        : state.error === 'unauthorized'
          ? messages.assistant.sessionExpired
          : state.error === 'forbidden'
            ? messages.assistant.forbidden
            : messages.assistant.unavailable;

  // Same gate as the floating panel: a 401 locks asks behind sign-in again.
  const authRequired = state.error === 'unauthorized';
  const goSignIn = () =>
    router.push(`${href('/login')}?reason=session-expired`);

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col px-4 py-4">
      <div className="flex h-[calc(100dvh-11rem)] min-h-[320px] flex-col overflow-hidden rounded-xl border border-border/70 bg-card shadow-xs md:h-[calc(100dvh-9.5rem)]">
        <header className="flex items-center gap-3 border-b border-border/70 bg-[var(--portal-sidebar-strong)] px-4 py-3 text-[var(--portal-sidebar-text)]">
          <span className="flex h-9 w-9 items-center justify-center rounded-md bg-[var(--portal-yellow)]/15 text-[var(--portal-chrome-accent)]">
            <AssistantMascot className="h-5 w-5" />
          </span>
          <div className="min-w-0 flex-1">
            <h1 className="truncate text-sm font-semibold">
              {messages.assistant.specializedTitle}
            </h1>
            <p className="truncate text-xs text-[var(--portal-sidebar-muted)]">
              {messages.assistant.specializedTagline}
            </p>
          </div>
          <button
            type="button"
            onClick={() => resetConversation()}
            disabled={isSending || authRequired}
            aria-label={messages.assistant.newConversation}
            title={messages.assistant.newConversation}
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md text-[var(--portal-sidebar-muted)] transition-colors hover:bg-white/10 hover:text-[var(--portal-sidebar-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
          >
            <RotateCcw className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>

        <div
          ref={logRef}
          onScroll={handleLogScroll}
          role="log"
          aria-label={messages.assistant.specializedLabel}
          aria-live="off"
          tabIndex={0}
          className="min-h-0 flex-1 space-y-4 overflow-y-auto px-3 py-4 outline-none focus-visible:ring-2 focus-visible:ring-ring md:px-4"
        >
          {state.messages.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center gap-3 text-center text-muted-foreground">
              <span className="flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 text-primary">
                <AssistantMascot className="h-6 w-6" />
              </span>
              <p className="max-w-md text-sm">{messages.assistant.specializedEmpty}</p>
            </div>
          ) : (
            <AssistantMessages
              messageList={state.messages}
              onFeedback={(messageId, rating, reason) =>
                void setFeedback(messageId, rating, reason)
              }
              followUps={isSending ? undefined : messages.assistant.specializedSuggestions}
              followUpsDisabled={authRequired}
              followUpsLabel={messages.assistant.followUpsLabel}
              onFollowUp={(suggestion) => void sendMessage(undefined, suggestion)}
              scope="specialized"
            />
          )}

          {isSending && !state.messages.some((m) => m.pending) ? (
            <div
              className="flex items-center gap-2 pl-9 text-xs text-muted-foreground"
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
                <button
                  type="button"
                  className="mt-2 min-h-10 rounded-md bg-primary px-3 font-medium text-primary-foreground hover:bg-primary/90"
                  onClick={goSignIn}
                >
                  {messages.assistant.signInAction}
                </button>
              ) : lastPrompt ? (
                <button
                  type="button"
                  className="mt-2 rounded-md px-0 text-destructive hover:underline"
                  onClick={(event) => void sendMessage(event, lastPrompt, { retry: true })}
                >
                  {vi ? 'Gửi lại câu hỏi' : 'Retry the question'}
                </button>
              ) : null}
            </div>
          ) : null}
        </div>

        <div className="shrink-0">
          <AssistantComposer
            input={input}
            inputRef={inputRef}
            isSending={isSending}
            onInputChange={setInput}
            onSubmit={(event) => void sendMessage(event)}
            onStop={() => void stopGeneration()}
            scope="specialized"
            authLocked={authRequired}
          />
        </div>
      </div>
    </div>
  );
}
