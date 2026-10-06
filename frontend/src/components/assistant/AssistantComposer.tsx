'use client';

import {
  type FormEvent,
  type KeyboardEvent,
  type RefObject,
  useEffect,
  useId,
  useRef,
} from 'react';
import { Send, Square } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useI18n } from '@/i18n';
import { AssistantMascot } from './AssistantMascot';

interface AssistantComposerProps {
  input: string;
  inputRef: RefObject<HTMLTextAreaElement>;
  isSending: boolean;
  onInputChange: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onStop: () => void;
  scope?: 'academic' | 'specialized';
  /** Session ended: asks are refused until the user signs in again. */
  authLocked?: boolean;
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
  scope = 'academic',
  authLocked = false,
}: AssistantComposerProps) {
  const { messages } = useI18n();
  const composerId = useId();
  const placeholder = scope === 'specialized'
    ? messages.assistant.specializedPlaceholder
    : messages.assistant.placeholder;

  // While a turn is in flight the textarea is disabled (locked panel), which
  // also drops focus; hand focus back the moment the turn finishes so the
  // student can keep typing without an extra click.
  const wasSendingRef = useRef(false);
  useEffect(() => {
    if (wasSendingRef.current && !isSending) inputRef.current?.focus();
    wasSendingRef.current = isSending;
  }, [inputRef, isSending]);

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
          placeholder={placeholder}
          name="assistant-message"
          autoComplete="off"
          rows={2}
          maxLength={2000}
          disabled={isSending || authLocked}
          className="min-h-12 min-w-0 flex-1 resize-none border-0 bg-transparent px-1 py-1 text-base leading-6 text-foreground outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-70 md:text-sm"
          aria-label={placeholder}
          aria-describedby={`${composerId}-hint ${composerId}-count`}
        />
        {isSending ? (
          <Button
            type="button"
            variant="secondary"
            className="min-h-11 shrink-0 gap-1.5 rounded-xl px-3"
            onClick={onStop}
            aria-label={messages.assistant.stop}
          >
            <Square className="h-4 w-4" aria-hidden="true" />
            <span>{messages.assistant.stopLabel}</span>
          </Button>
        ) : (
          <Button
            type="submit"
            size="icon"
            className="min-h-11 min-w-11 rounded-xl bg-gradient-to-br from-primary via-[#004eab] to-[#005fcf] shadow-sm transition-transform hover:scale-105 active:scale-95 motion-reduce:transform-none motion-reduce:transition-none"
            disabled={!input.trim() || authLocked}
            aria-label={messages.assistant.send}
          >
            <Send className="h-4 w-4" aria-hidden="true" />
          </Button>
        )}
      </div>
      <div className="mt-1.5 flex items-center justify-between text-[11px] text-muted-foreground px-1">
        <span className="flex items-center gap-1.5">
          {/* The cute mascot lives right under the input box: it greets without
              crowding the empty state above, and gently pulses while thinking. */}
          <span className="inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
            <AssistantMascot className="h-3.5 w-3.5" active={isSending} />
          </span>
          {/* aria-live announces the lock: users hear why the composer stopped
              accepting input instead of a silent dead panel. */}
          <span id={`${composerId}-hint`} aria-live="polite">
            {authLocked
              ? messages.assistant.signInRequired
              : isSending
                ? messages.assistant.respondingHint
                : messages.assistant.composerHint}
          </span>
        </span>
        <span id={`${composerId}-count`} className="ml-2 shrink-0">{input.length}/2000</span>
      </div>
    </form>
  );
}
