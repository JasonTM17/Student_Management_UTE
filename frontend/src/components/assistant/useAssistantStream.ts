'use client';

import {
  type FormEvent,
  useCallback,
  useEffect,
  useReducer,
  useRef,
  useState,
} from 'react';
import type { Locale } from '@/i18n/config';
import {
  createAssistantRequestId,
  thesisApi,
  type AssistantStreamEvent,
} from '@/lib/thesis-api';
import {
  GUARD_BLOCKED_CODES,
  TRANSIENT_TERMINAL_CODES,
  assistantReducer,
  initialState,
  type AssistantFeedbackReason,
  type ChatMessage,
} from './assistant-reducer';
import {
  isStudentAssistantQuery,
  resolveStudentAssistantQuery,
} from '@/lib/assistant-student-resolver';
import { inspectAssistantInput, isSensitiveGuardReason } from '@/lib/assistant-input-guard';

export interface UseAssistantStreamOptions {
  locale: Locale;
  /**
   * Retrieval scope. Undefined/'academic' keeps the default curated corpus;
   * 'specialized' narrows retrieval to the professional SPECIALIZED domain.
   * The scope can only narrow the corpus, never widen access.
   */
  scope?: 'academic' | 'specialized';
  assistantMessages: {
    cancelled: string;
    unavailable: string;
    quotaExceeded: string;
    blocked: string;
    sensitiveBlocked: string;
    technicalBlocked: string;
    turnInProgress: string;
    forbidden?: string;
    sessionExpired?: string;
    offline?: string;
  };
  onReconcileHistory?: () => void;
  onNewExchange?: () => void;
}

interface SendMessageOptions {
  retry?: boolean;
}

function apiErrorStatus(error: unknown): number | undefined {
  const value = error as {
    response?: { status?: unknown };
    status?: unknown;
  };
  const status = value.response?.status ?? value.status;
  return typeof status === 'number' ? status : undefined;
}

function apiErrorCode(error: unknown): string | undefined {
  const value = error as {
    response?: { data?: { code?: unknown } };
  };
  const code = value.response?.data?.code;
  return typeof code === 'string' ? code : undefined;
}

type AssistantFailureKind =
  | 'quota'
  | 'unauthorized'
  | 'forbidden'
  | 'offline'
  | 'turn-in-progress'
  | 'unavailable';

interface AssistantFailure {
  kind: AssistantFailureKind;
  retryable: boolean;
}

function classifyAssistantFailure(error: unknown): AssistantFailure {
  const code = apiErrorCode(error);
  if (code === 'TURN_IN_PROGRESS') {
    return { kind: 'turn-in-progress', retryable: true };
  }

  const status = apiErrorStatus(error);
  if (status === 429) return { kind: 'quota', retryable: false };
  if (status === 401) return { kind: 'unauthorized', retryable: false };
  if (status === 403) return { kind: 'forbidden', retryable: false };

  const message = error instanceof Error ? error.message : '';
  if (/assistant stream unauthorized/i.test(message)) {
    return { kind: 'unauthorized', retryable: false };
  }

  // Known client responses will not improve by replaying the same request.
  // Only the explicit TURN_IN_PROGRESS code above is an exception.
  if (status !== undefined && status >= 400 && status < 500) {
    return { kind: 'unavailable', retryable: false };
  }

  if (typeof navigator !== 'undefined' && !navigator.onLine) {
    return { kind: 'offline', retryable: true };
  }

  return { kind: 'unavailable', retryable: true };
}

export function useAssistantStream({
  locale,
  scope,
  assistantMessages,
  onReconcileHistory,
  onNewExchange,
}: UseAssistantStreamOptions) {
  const [input, setInput] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [lastPrompt, setLastPrompt] = useState<string>();
  const [state, dispatch] = useReducer(assistantReducer, initialState);

  const isSendingRef = useRef(false);
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
  const feedbackRequestsRef = useRef(new Set<string>());
  // Guard against 409 CAS terminal race overwriting recovered answer with CANCELLED
  const casResolvedRef = useRef(false);

  // Abort any ongoing stream when the hook unmounts
  useEffect(() => {
    return () => {
      const requestId = activeRequestIdRef.current;
      if (requestId) {
        // Give the server a bounded, authenticated cancellation signal before
        // the browser tears down the stream so provider work does not continue
        // silently after navigation or panel unmount.
        void thesisApi.cancelRequest(requestId).catch(() => undefined);
      }
      requestGenerationRef.current += 1;
      abortRef.current?.abort();
      isSendingRef.current = false;
    };
  }, []);

  const applyStreamEvent = useCallback(
    (event: AssistantStreamEvent) => {
      if (event.type === 'meta') {
        dispatch({
          type: 'meta',
          model: event.model,
          conversationId: event.conversationId,
        });
      } else if (event.type === 'delta') {
        dispatch({ type: 'delta', text: event.text });
      } else if (event.type === 'replace') {
        dispatch({ type: 'replace', text: event.text, reasonCode: event.reasonCode });
      } else if (event.type === 'citation') {
        dispatch({ type: 'citation', citation: event.citation });
      } else if (event.type === 'done') {
        dispatch({
          type: 'complete',
          reply: {
            messageId: event.messageId,
            reasonCode: event.reasonCode,
            degraded: event.degraded,
          },
        });
      } else if (event.type === 'error') {
        if (GUARD_BLOCKED_CODES.has(event.code ?? '')) {
          // Guard blocks are deterministic: the requested input will be
          // refused again, so a JSON replay would only add latency. Surface
          // the localized blocked copy and stop with the guard reason code.
          dispatch({ type: 'replace', text: '' });
          dispatch({
            type: 'complete',
            reply: {
              content:
                event.code === 'TECHNICAL_REQUEST_BLOCKED'
                  ? assistantMessages.technicalBlocked
                  : isSensitiveGuardReason(event.code)
                    ? assistantMessages.sensitiveBlocked
                    : assistantMessages.blocked,
              degraded: true,
              reasonCode: event.code,
            },
          });
          return;
        }
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
                  ? assistantMessages.cancelled
                  : assistantMessages.unavailable,
              degraded: true,
              reasonCode: event.code,
            },
          });
        }
        throw new Error(event.code ?? 'assistant stream error');
      }
    },
    [
      assistantMessages.blocked,
      assistantMessages.cancelled,
      assistantMessages.sensitiveBlocked,
      assistantMessages.technicalBlocked,
      assistantMessages.unavailable,
    ],
  );

  const sendMessage = useCallback(
    async (
      event?: FormEvent<HTMLFormElement> | React.SyntheticEvent,
      retryPrompt?: string,
      options?: SendMessageOptions,
    ) => {
      event?.preventDefault();
      const message = (retryPrompt ?? input).trim();
      if (!message || isSending || isSendingRef.current) return;
      const isRetry = options?.retry === true;
      isSendingRef.current = true;
      setInput('');
      setLastPrompt(message);
      dispatch({ type: 'clear-error' });
      if (isRetry) {
        dispatch({ type: 'retry-start', prompt: message });
      } else {
        dispatch({
          type: 'user',
          message: {
            id: `${Date.now()}-user`,
            role: 'user',
            content: message,
            createdAt: new Date().toISOString(),
          },
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
      // A fresh send always follows the new exchange, even if the reader was
      // scrolled up reviewing history when they hit send.
      onNewExchange?.();
      const controller = new AbortController();
      const generation = ++requestGenerationRef.current;
      casResolvedRef.current = false;
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
        // The student resolver answers locally, so the server guard never
        // sees those turns. Run the same deterministic checks first so a
        // blocked or sensitive message cannot reach a canned answer.
        const localGuard = inspectAssistantInput(message);
        if (!localGuard.allowed) {
          dispatch({ type: 'replace', text: '' });
          dispatch({
            type: 'complete',
            reply: {
              content: isSensitiveGuardReason(localGuard.reasonCode)
                ? assistantMessages.sensitiveBlocked
                : localGuard.reasonCode === 'TECHNICAL_REQUEST_BLOCKED'
                  ? assistantMessages.technicalBlocked
                  : assistantMessages.blocked,
              degraded: true,
              reasonCode: localGuard.reasonCode,
            },
          });
          return;
        }
        if (isStudentAssistantQuery(message)) {
          try {
            const resolution = await resolveStudentAssistantQuery(message, locale);
            if (resolution && isCurrentRequest() && !controller.signal.aborted) {
              // Only trivial smalltalk resolves locally now (greeting,
              // capabilities, thanks, goodbye). It is static copy with no
              // citation and no claim on the asker's records, so it never
              // reuses the server's personal-context badge.
              applyStreamEvent({
                type: 'meta',
                conversationId: requestedConversationId,
                model: 'CampusUTE Student Assistant',
              });
              applyStreamEvent({
                type: 'delta',
                text: resolution.answer,
              });
              applyStreamEvent({
                type: 'done',
                messageId: `local-resolved-${Date.now()}`,
                reasonCode: 'LOCAL_ASSIST',
                degraded: false,
              });
              terminalReconciled = true;
              onReconcileHistory?.();
              return;
            }
          } catch {
            // Fallback to thesisApi.streamChat if local resolution encounters error
          }
        }

        await thesisApi.streamChat(message, locale, {
          conversationId: requestedConversationId,
          clientRequestId,
          scope,
          signal: controller.signal,
          onEvent: (streamEvent) => {
            if (!isCurrentRequest()) return;
            if (streamEvent.type === 'delta') sawDelta = true;
            if (streamEvent.type === 'done') sawDone = true;
            if (streamEvent.type === 'error' && GUARD_BLOCKED_CODES.has(streamEvent.code ?? '')) {
              // A guard block ends the turn locally; no done frame follows.
              sawDone = true;
            }
            applyStreamEvent(streamEvent);
          },
        });
        if (!sawDone) {
          throw new Error('assistant stream ended without a done event');
        }
        terminalReconciled = true;
        onReconcileHistory?.();
      } catch (error) {
        if (!isCurrentRequest()) return;
        if (controller.signal.aborted) {
          // If 409 CAS resolution already recovered and committed the terminal answer,
          // do not overwrite the answer with a CANCELLED degraded message. The same
          // applies once the final done frame was applied: a Stop click racing the
          // stream close must not erase a fully received answer.
          if (casResolvedRef.current || sawDone) return;
          dispatch({
            type: 'complete',
            reply: {
              content: assistantMessages.cancelled,
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
          onReconcileHistory?.();
          return;
        }
        // A stream can commit successfully and lose its final `done` frame. Always
        // reconcile through JSON with the same idempotency key before surfacing an
        // error, even when deltas were already rendered.
        // The two-argument thesisApi.chat(message, locale) compatibility contract
        // remains supported; reconciliation below supplies the conversation/key.
        let reply: Awaited<ReturnType<typeof thesisApi.chat>> | undefined;
        let fallbackError: unknown;
        for (let attempt = 0; attempt < 4; attempt += 1) {
          try {
            reply = await thesisApi.chat(
              message,
              locale,
              requestedConversationId,
              clientRequestId,
              scope,
            );
            break;
          } catch (error) {
            fallbackError = error;
            if (apiErrorCode(error) !== 'TURN_IN_PROGRESS' || attempt === 3) break;
            // The original stream may still be committing the same key. Keep
            // the idempotency key and briefly poll for its replayable result
            // instead of minting a second turn.
            await new Promise((resolve) =>
              globalThis.setTimeout(resolve, 250 * (attempt + 1)),
            );
          }
        }
        if (reply) {
          if (isCurrentRequest()) {
            dispatch({
              type: 'complete',
              reply: { ...reply, content: reply.answer },
            });
            terminalReconciled = true;
            retryRequestIdRef.current = undefined;
            onReconcileHistory?.();
          }
        } else {
          if (!isCurrentRequest()) return;
          const streamFailure = classifyAssistantFailure(error);
          const reconciliationFailure = classifyAssistantFailure(fallbackError);
          // The stream transport exposes only the 409 status, not its error
          // envelope. Treat that response as the known TURN_IN_PROGRESS case
          // only when JSON reconciliation confirms the exact server code.
          const reconciliationConfirmsActiveTurn =
            apiErrorStatus(error) === 409 &&
            reconciliationFailure.kind === 'turn-in-progress';
          const terminalFailure = (reconciliationConfirmsActiveTurn
            ? [reconciliationFailure]
            : [streamFailure, reconciliationFailure]
          ).find(
            (failure) => !failure.retryable,
          );

          // Preserve a known terminal result from either request. In
          // particular, a transient reconciliation outage must not turn a
          // stream quota/auth response into a retryable error. Conversely,
          // reconciliation 4xx responses are not retried just because the
          // original stream failed transiently.
          if (!terminalFailure) {
            const kind =
              reconciliationFailure.kind === 'turn-in-progress'
                ? 'turn-in-progress'
                : streamFailure.kind === 'offline' || reconciliationFailure.kind === 'offline'
                  ? 'offline'
                  : 'unavailable';
            dispatch({
              type: 'stream-failed',
              kind,
            });
            // The result may have committed even though both the stream and
            // reconciliation request failed. Keep the same idempotency key so
            // an explicit retry replays the original turn instead of creating
            // a duplicate.
          } else {
            const kind = terminalFailure.kind;
            dispatch({
              type: 'complete',
              reply: {
                content:
                  kind === 'quota'
                    ? assistantMessages.quotaExceeded
                    : kind === 'unavailable'
                      ? assistantMessages.unavailable
                    : kind === 'forbidden'
                      ? assistantMessages.forbidden
                      : assistantMessages.sessionExpired,
                degraded: true,
                reasonCode: kind === 'quota' ? 'QUOTA_EXCEEDED' : 'KNOWLEDGE_UNAVAILABLE',
              },
            });
            // Quota and authorization failures are terminal for this turn.
            retryRequestIdRef.current = undefined;
            retryConversationIdRef.current = undefined;
          }
        }
      } finally {
        if (isCurrentRequest()) {
          isSendingRef.current = false;
          setIsSending(false);
          abortRef.current = null;
          activeRequestIdRef.current = undefined;
          activeConversationIdRef.current = undefined;
          activePromptRef.current = undefined;
          casResolvedRef.current = false;
          if (terminalReconciled) {
            retryRequestIdRef.current = undefined;
            retryConversationIdRef.current = undefined;
          }
        }
      }
    },
    [
      applyStreamEvent,
      assistantMessages.blocked,
      assistantMessages.cancelled,
      assistantMessages.forbidden,
      assistantMessages.unavailable,
      assistantMessages.quotaExceeded,
      assistantMessages.sensitiveBlocked,
      assistantMessages.sessionExpired,
      assistantMessages.technicalBlocked,
      input,
      isSending,
      locale,
      scope,
      onNewExchange,
      onReconcileHistory,
      state.conversationId,
    ],
  );

  const stopGeneration = useCallback(async () => {
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
            scope,
          );
          casResolvedRef.current = true;
          dispatch({
            type: 'complete',
            reply: { ...reply, content: reply.answer },
          });
          retryRequestIdRef.current = undefined;
          retryConversationIdRef.current = undefined;
          onReconcileHistory?.();
          abortRef.current?.abort();
        } catch {
          /* keep the stream alive long enough for its done frame */
        }
      } else {
        abortRef.current?.abort();
      }
    }
  }, [lastPrompt, locale, scope, onReconcileHistory]);

  const abortStream = useCallback(() => {
    requestGenerationRef.current += 1;
    abortRef.current?.abort();
    isSendingRef.current = false;
    setIsSending(false);
    activeRequestIdRef.current = undefined;
    retryRequestIdRef.current = undefined;
    activeConversationIdRef.current = undefined;
    retryConversationIdRef.current = undefined;
    activePromptRef.current = undefined;
    casResolvedRef.current = false;
  }, []);

  const setFeedback = useCallback(
    async (
      messageId: string,
      rating: 'UP' | 'DOWN' | null,
      reason?: AssistantFeedbackReason,
    ) => {
      if (feedbackRequestsRef.current.has(messageId)) return;
      feedbackRequestsRef.current.add(messageId);
      dispatch({ type: 'feedback-start', messageId });
      try {
        if (rating === null) {
          await thesisApi.deleteMessageFeedback(messageId);
        } else {
          await thesisApi.setMessageFeedback(messageId, rating, reason);
        }
        dispatch({ type: 'feedback-saved', messageId, rating, reason });
      } catch {
        dispatch({ type: 'feedback-failed', messageId, rating, reason });
      } finally {
        feedbackRequestsRef.current.delete(messageId);
      }
    },
    [],
  );

  const resetConversation = useCallback(
    (conversationId?: string, messages?: ChatMessage[]) => {
      requestGenerationRef.current += 1;
      abortRef.current?.abort();
      isSendingRef.current = false;
      setIsSending(false);
      setLastPrompt(undefined);
      retryRequestIdRef.current = undefined;
      retryConversationIdRef.current = undefined;
      activeRequestIdRef.current = undefined;
      activeConversationIdRef.current = undefined;
      activePromptRef.current = undefined;
      casResolvedRef.current = false;
      dispatch({ type: 'reset', conversationId, messages });
    },
    [],
  );

  return {
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
  };
}
