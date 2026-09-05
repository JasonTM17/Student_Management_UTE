'use client';

import { ChevronLeft, RotateCcw, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useI18n } from '@/i18n';
import type { AssistantConversation } from '@/lib/thesis-api';

export type AssistantHistoryStatus = 'idle' | 'loading' | 'loaded' | 'error';

interface AssistantHistoryPanelProps {
  history: AssistantConversation[];
  historyStatus: AssistantHistoryStatus;
  deletingConversationId?: string;
  onBack: () => void;
  onCreate: () => void;
  onSelect: (conversation: AssistantConversation) => void;
  onDelete: (conversationId: string) => void;
  onRetry: () => void;
}

export function AssistantHistoryPanel({
  history,
  historyStatus,
  deletingConversationId,
  onBack,
  onCreate,
  onSelect,
  onDelete,
  onRetry,
}: AssistantHistoryPanelProps) {
  const { messages } = useI18n();

  return (
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
          onClick={onBack}
        >
          <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          {messages.assistant.backToChat}
        </Button>
        <Button
          type="button"
          size="sm"
          className="min-h-11"
          onClick={onCreate}
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
            onClick={onRetry}
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
              onClick={() => onSelect(item)}
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
              onClick={() => onDelete(item.id)}
              aria-label={messages.assistant.deleteConversation}
            >
              <Trash2 className="h-4 w-4" aria-hidden="true" />
            </Button>
          </div>
        ))}
      </div>
    </div>
  );
}
