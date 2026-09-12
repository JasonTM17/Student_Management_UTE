'use client';

import { useState } from 'react';
import {
  Bot,
  ChevronDown,
  ChevronUp,
  FileText,
  ShieldCheck,
  Sparkles,
  ThumbsDown,
  ThumbsUp,
  User,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import type { AssistantCitation } from '@/lib/thesis-api';
import type { ChatMessage } from './assistant-reducer';
import { AssistantMarkdownContent } from './AssistantMarkdownContent';

interface AssistantMessagesProps {
  messageList: ChatMessage[];
  onFeedback: (messageId: string, rating: 'UP' | 'DOWN') => void;
  /** Suggested follow-up questions shown under the latest answer. */
  followUps?: readonly string[];
  followUpsLabel?: string;
  onFollowUp?: (suggestion: string) => void;
}

export function reasonLabel(
  message: ChatMessage,
  messages: ReturnType<typeof useI18n>['messages'],
) {
  return message.reasonCode === 'QUOTA_EXCEEDED'
    ? messages.assistant.quotaExceeded
    : message.reasonCode === 'CANCELLED'
      ? messages.assistant.cancelled
      : message.reasonCode === 'PROMPT_INJECTION'
        ? messages.assistant.blockedLabel
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
  const domain = citation.domain?.toUpperCase() as
    | keyof typeof messages.assistant.domains
    | undefined;
  return (
    (domain && messages.assistant.domains[domain]) ||
    messages.assistant.domains.GENERAL_FAQ
  );
}

export function AssistantMessages({
  messageList,
  onFeedback,
  followUps,
  followUpsLabel,
  onFollowUp,
}: AssistantMessagesProps) {
  const { messages } = useI18n();
  const [openCitations, setOpenCitations] = useState<Record<string, boolean>>({});

  const toggleCitation = (messageId: string) => {
    setOpenCitations((prev) => ({ ...prev, [messageId]: !prev[messageId] }));
  };

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
    <div className="space-y-4">
      {messageList.map((message, index) => {
        const isUser = message.role === 'user';
        const isCitationOpen = Boolean(openCitations[message.id]);

        return (
          <div key={message.id} className="space-y-2">
            <div
              className={cn(
                'flex items-start gap-2.5',
                isUser ? 'flex-row-reverse' : 'flex-row',
              )}
            >
              {/* Role Avatar */}
              <div
                className={cn(
                  'flex h-7 w-7 shrink-0 select-none items-center justify-center rounded-xl text-xs shadow-xs',
                  isUser
                    ? 'bg-primary text-primary-foreground font-semibold ring-1 ring-primary/30'
                    : 'bg-gradient-to-br from-primary via-[#004eab] to-[#005fcf] text-white ring-1 ring-primary/25',
                )}
                aria-hidden="true"
              >
                {isUser ? (
                  <User className="h-3.5 w-3.5" />
                ) : (
                  <Bot className="h-3.5 w-3.5" />
                )}
              </div>

              {/* Message Bubble Container */}
              <div
                className={cn(
                  'flex flex-col',
                  isUser ? 'items-end max-w-[84%]' : 'items-start max-w-[88%]',
                )}
              >
                <div
                  role="article"
                  aria-label={
                    isUser
                      ? messages.assistant.you
                      : messages.assistant.label
                  }
                  className={cn(
                    'relative px-3.5 py-2.5 text-sm leading-6 shadow-xs transition-shadow break-words [overflow-wrap:anywhere]',
                    isUser
                      ? 'rounded-2xl rounded-tr-xs bg-primary text-primary-foreground font-medium'
                      : 'rounded-2xl rounded-tl-xs border border-border/80 bg-card text-foreground',
                  )}
                >
                  {message.pending && !message.content ? (
                    /* 3-dot wave pulse typing indicator */
                    <div
                      className="flex items-center gap-1.5 py-1 text-muted-foreground"
                      aria-label={messages.assistant.thinking}
                    >
                      <span className="h-2 w-2 animate-bounce rounded-full bg-primary/60 [animation-delay:-0.3s]" />
                      <span className="h-2 w-2 animate-bounce rounded-full bg-primary/80 [animation-delay:-0.15s]" />
                      <span className="h-2 w-2 animate-bounce rounded-full bg-primary" />
                      <span className="ml-2 text-xs font-normal text-muted-foreground">
                        {messages.assistant.thinking}
                      </span>
                    </div>
                  ) : (
                    <AssistantMarkdownContent content={message.content} />
                  )}

                  {/* Reason Code Badge */}
                  {!isUser && message.reasonCode && (
                    <div className="mt-2 flex items-center gap-1.5 border-t border-border/60 pt-1.5 text-[11px] font-medium text-muted-foreground">
                      {message.reasonCode === 'ANSWERED' ? (
                        <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/10 px-2 py-0.5 text-emerald-600 dark:text-emerald-400">
                          <Sparkles className="h-3 w-3" />
                          {messages.assistant.answered}
                        </span>
                      ) : message.reasonCode === 'RAG_GROUNDED' ? (
                        <span className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 text-primary">
                          <ShieldCheck className="h-3 w-3" />
                          {messages.assistant.domains.POLICY}
                        </span>
                      ) : (
                        <span
                          className={cn(
                            'uppercase tracking-wide',
                            message.degraded
                              ? 'text-status-warning-foreground font-semibold'
                              : 'text-muted-foreground',
                          )}
                        >
                          {reasonLabel(message, messages)}
                        </span>
                      )}
                    </div>
                  )}

                  {/* Collapsible Citations Card */}
                  {!isUser && message.citations?.length ? (
                    <div className="mt-2.5 border-t border-border/60 pt-1.5">
                      <button
                        type="button"
                        onClick={() => toggleCitation(message.id)}
                        className="flex w-full items-center justify-between py-1 text-xs font-semibold text-primary transition-opacity hover:opacity-80"
                      >
                        <span className="flex items-center gap-1.5">
                          <FileText className="h-3.5 w-3.5" />
                          {messages.assistant.sources} ({message.citations.length})
                        </span>
                        {isCitationOpen ? (
                          <ChevronUp className="h-3.5 w-3.5" />
                        ) : (
                          <ChevronDown className="h-3.5 w-3.5" />
                        )}
                      </button>

                      {isCitationOpen && (
                        <div className="mt-2 space-y-2 rounded-xl bg-secondary/50 p-2.5 text-xs">
                          {message.citations.map((citation) => (
                            <div
                              key={citation.id}
                              className="border-l-2 border-primary pl-2 leading-5"
                            >
                              <p className="font-semibold text-foreground">
                                {citation.title}
                              </p>
                              <p className="text-[11px] text-muted-foreground">
                                {[
                                  citationDomainLabel(citation, messages),
                                  citation.locale?.toUpperCase(),
                                ]
                                  .filter(Boolean)
                                  .join(' · ')}
                              </p>
                              <span className="sr-only">{citation.source}</span>
                              <p className="mt-1 text-muted-foreground italic">
                                &ldquo;{citation.excerpt}&rdquo;
                              </p>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  ) : null}

                  {/* Feedback Buttons */}
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
                          'min-h-8 min-w-8 rounded-lg text-muted-foreground hover:text-primary',
                          message.feedback === 'UP' &&
                            'bg-secondary text-primary',
                        )}
                        aria-label={messages.assistant.feedbackUp}
                        aria-pressed={message.feedback === 'UP'}
                        onClick={() => onFeedback(message.id, 'UP')}
                      >
                        <ThumbsUp className="h-3.5 w-3.5" aria-hidden="true" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className={cn(
                          'min-h-8 min-w-8 rounded-lg text-muted-foreground hover:text-destructive',
                          message.feedback === 'DOWN' &&
                            'bg-secondary text-destructive',
                        )}
                        aria-label={messages.assistant.feedbackDown}
                        aria-pressed={message.feedback === 'DOWN'}
                        onClick={() => onFeedback(message.id, 'DOWN')}
                      >
                        <ThumbsDown
                          className="h-3.5 w-3.5"
                          aria-hidden="true"
                        />
                      </Button>
                    </div>
                  ) : null}
                </div>
              </div>
            </div>

            {/* Follow-up Suggestions under the latest assistant response */}
            {index === lastAssistantIndex &&
            followUps?.length &&
            onFollowUp ? (
              <div className="ml-9 max-w-[88%] pt-1">
                {followUpsLabel ? (
                  <p className="mb-1.5 text-[11px] font-medium uppercase tracking-wider text-muted-foreground">
                    {followUpsLabel}
                  </p>
                ) : null}
                <div className="flex flex-wrap gap-1.5">
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
        );
      })}
    </div>
  );
}
