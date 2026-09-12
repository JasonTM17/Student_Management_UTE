'use client';

import type { FormEvent, KeyboardEvent, RefObject } from 'react';
import { Send, Square } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useI18n } from '@/i18n';

interface AssistantComposerProps {
  input: string;
  inputRef: RefObject<HTMLTextAreaElement>;
  isSending: boolean;
  onInputChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onStop: () => void;
}

function handleComposerSubmitKeyDown(
  event: KeyboardEvent<HTMLTextAreaElement>,
) {
  if (
    event.key !== 'Enter' ||
    event.shiftKey ||
    event.nativeEvent.isComposing
  )
    return false;
  const value = event.currentTarget.value.trim();
  event.preventDefault();
  return Boolean(value);
}

export function AssistantComposer({
  input,
  inputRef,
  isSending,
  onInputChange,
  onSubmit,
  onStop,
}: AssistantComposerProps) {
  const { messages } = useI18n();

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (!handleComposerSubmitKeyDown(event)) return;
    event.currentTarget.form?.requestSubmit();
  };

  return (
    <form
      onSubmit={onSubmit}
      className="border-t border-border/70 bg-card p-3"
    >
      <div className="flex items-end gap-2 rounded-xl border border-border/80 bg-background p-2 focus-within:ring-2 focus-within:ring-ring">
        <textarea
          ref={inputRef}
          value={input}
          onChange={(event) => onInputChange(event.target.value)}
          onKeyDown={handleKeyDown}
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
            className="min-h-11 min-w-11 rounded-xl"
            onClick={onStop}
            aria-label={messages.assistant.stop}
          >
            <Square className="h-4 w-4" aria-hidden="true" />
          </Button>
        ) : (
          <Button
            type="submit"
            size="icon"
            className="min-h-11 min-w-11 rounded-xl"
            disabled={!input.trim()}
            aria-label={messages.assistant.send}
          >
            <Send className="h-4 w-4" aria-hidden="true" />
          </Button>
        )}
      </div>
      <div className="mt-1.5 flex items-center justify-between text-[11px] text-muted-foreground px-1">
        <span>Enter để gửi · Shift+Enter xuống dòng</span>
        <span>{input.length}/2000</span>
      </div>
    </form>
  );
}
