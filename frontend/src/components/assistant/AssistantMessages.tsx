'use client';

import { ThumbsDown, ThumbsUp } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import type { AssistantCitation } from '@/lib/thesis-api';
import type { ChatMessage } from './assistant-reducer';

interface AssistantMessagesProps {
  messageList: ChatMessage[];
  onFeedback: (messageId: string, rating: 'UP' | 'DOWN') => void;
  /** Suggested follow-up questions shown under the latest answer. */
  followUps?: readonly string[];
  followUpsLabel?: string;
  onFollowUp?: (suggestion: string) => void;
}

export function reasonLabel(message: ChatMessage, messages: ReturnType<typeof useI18n>['messages']) {
  return message.reasonCode === 'QUOTA_EXCEEDED'
    ? messages.assistant.quotaExceeded
    : message.reasonCode === 'CANCELLED'
      ? messages.assistant.cancelled
      : message.degraded
        ? messages.assistant.degraded
        : message.reasonCode === 'NO_MATCH'
          ? messages.assistant.noMatch
          : message.reasonCode === 'ANSWERED'
            ? messages.assistant.answered
            : messages.assistant.answered;
}

function citationDomainLabel(
  citation: AssistantCitation,
  messages: ReturnType<typeof useI18n>['messages'],
) {
  const domain = citation.domain?.toUpperCase() as keyof typeof messages.assistant.domains | undefined;
  return (domain && messages.assistant.domains[domain]) || messages.assistant.domains.GENERAL_FAQ;
}

export function AssistantMessages({
  messageList,
  onFeedback,
  followUps,
  followUpsLabel,
  onFollowUp,
}: AssistantMessagesProps) {
  const { messages } = useI18n();
  const lastAssistantIndex = (() => {
    for (let index = messageList.length - 1; index >= 0; index -= 1) {
      const message = messageList[index];
      if (message.role === 'assistant' && !message.pending && message.content) {
        return index;
      }
    }
    return -1;
  })();

  return (
    <>
      {messageList.map((message, index) => (
        <div key={message.id}>
        <div
          key={message.id}
          role="article"
          aria-label={
            message.role === 'user'
              ? messages.assistant.you
              : messages.assistant.label
          }
          className={cn(
            'max-w-[92%] rounded-md px-3.5 py-2.5 text-sm leading-6',
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
                  ? 'text-status-warning-foreground'
                  : 'text-muted-foreground',
              )}
            >
              {reasonLabel(message, messages)}
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
                    {[citationDomainLabel(citation, messages), citation.locale?.toUpperCase()]
                      .filter(Boolean)
                      .join(' · ')}
                  </p>
                  <span className="sr-only">{citation.source}</span>
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
                  'min-h-9 min-w-9',
                  message.feedback === 'UP' && 'bg-secondary text-primary',
                )}
                aria-label={messages.assistant.feedbackUp}
                aria-pressed={message.feedback === 'UP'}
                onClick={() => onFeedback(message.id, 'UP')}
              >
                <ThumbsUp className="h-4 w-4" aria-hidden="true" />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className={cn(
                  'min-h-9 min-w-9',
                  message.feedback === 'DOWN' && 'bg-secondary text-destructive',
                )}
                aria-label={messages.assistant.feedbackDown}
                aria-pressed={message.feedback === 'DOWN'}
                onClick={() => onFeedback(message.id, 'DOWN')}
              >
                <ThumbsDown className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          ) : null}
        </div>
        {index === lastAssistantIndex && followUps?.length && onFollowUp ? (
          <div className="ml-auto mt-1 max-w-[92%]">
            {followUpsLabel ? (
              <p className="mb-1 text-right text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                {followUpsLabel}
              </p>
            ) : null}
            <div className="flex flex-wrap justify-end gap-1.5">
              {followUps.map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  onClick={() => onFollowUp(suggestion)}
                  className="rounded-full border border-primary/20 bg-primary/5 px-2.5 py-1 text-[11px] font-medium text-primary transition-colors hover:border-primary/40 hover:bg-primary/10"
                >
                  {suggestion}
                </button>
              ))}
            </div>
          </div>
        ) : null}
        </div>
      ))}
    </>
  );
}
