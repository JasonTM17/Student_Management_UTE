'use client';

import {
  FormEvent,
  KeyboardEvent,
  useEffect,
  useRef,
  useState,
} from 'react';
import { Bot, LoaderCircle, MessageCircle, Send, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import { thesisApi, type AssistantCitation } from '@/lib/thesis-api';

interface ChatMessage {
  id: string;
  role: 'assistant' | 'user';
  content: string;
  citations?: AssistantCitation[];
  degraded?: boolean;
  reasonCode?: string;
}

export function AssistantPanel() {
  const { locale, messages } = useI18n();
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [conversation, setConversation] = useState<ChatMessage[]>([]);
  const launcherRef = useRef<HTMLButtonElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!open) return undefined;

    const handleEscape = (event: globalThis.KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      setOpen(false);
      requestAnimationFrame(() => launcherRef.current?.focus());
    };

    document.addEventListener('keydown', handleEscape);
    requestAnimationFrame(() => inputRef.current?.focus());
    return () => document.removeEventListener('keydown', handleEscape);
  }, [open]);

  const closePanel = () => {
    setOpen(false);
    requestAnimationFrame(() => launcherRef.current?.focus());
  };

  const sendMessage = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const message = input.trim();
    if (!message || isSending) return;

    setInput('');
    setConversation((current) => [
      ...current,
      { id: `${Date.now()}-user`, role: 'user', content: message },
    ]);
    setIsSending(true);

    try {
      const reply = await thesisApi.chat(message, locale);
      setConversation((current) => [
        ...current,
        {
          id: `${Date.now()}-assistant`,
          role: 'assistant',
          content: reply.answer,
          citations: reply.citations,
          degraded: reply.degraded,
          reasonCode: reply.reasonCode,
        },
      ]);
    } catch {
      setConversation((current) => [
        ...current,
        {
          id: `${Date.now()}-error`,
          role: 'assistant',
          content: messages.assistant.unavailable,
          degraded: true,
          reasonCode: 'KNOWLEDGE_UNAVAILABLE',
        },
      ]);
    } finally {
      setIsSending(false);
    }
  };

  const handleComposerKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return;
    event.preventDefault();
    event.currentTarget.form?.requestSubmit();
  };

  const reasonLabel = (message: ChatMessage) => {
    if (message.degraded) return messages.assistant.degraded;
    if (message.reasonCode === 'NO_MATCH') return messages.assistant.noMatch;
    if (message.reasonCode === 'ANSWERED') return messages.assistant.answered;
    return message.reasonCode;
  };

  return (
    <div className="fixed bottom-[calc(1rem+env(safe-area-inset-bottom))] right-4 z-50 w-[min(24rem,calc(100vw-2rem))] sm:bottom-6 sm:right-6">
      {!open ? (
        <Button
          ref={launcherRef}
          type="button"
          size="icon"
          className="ml-auto rounded-full border border-white/15 bg-primary text-primary-foreground shadow-xl hover:bg-primary/90"
          onClick={() => setOpen(true)}
          aria-label={messages.assistant.open}
          title={messages.assistant.open}
        >
          <MessageCircle className="h-5 w-5" aria-hidden="true" />
        </Button>
      ) : (
        <section
          role="dialog"
          aria-modal="false"
          aria-labelledby="assistant-panel-title"
          aria-describedby="assistant-panel-description"
          className="flex max-h-[min(42rem,calc(100vh-2rem-env(safe-area-inset-bottom)))] flex-col overflow-hidden rounded-md border border-border/80 bg-card shadow-2xl"
        >
          <header className="flex items-start justify-between gap-4 border-b border-border/70 bg-[hsl(var(--foreground))] px-4 py-4 text-[hsl(var(--background))]">
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[hsl(var(--background))/0.14]">
                <Bot className="h-5 w-5" />
              </div>
              <div>
                <h2 id="assistant-panel-title" className="font-semibold">
                  {messages.assistant.title}
                </h2>
                <p id="assistant-panel-description" className="mt-1 text-xs leading-5 text-[hsl(var(--background))/0.7]">
                  {messages.assistant.description}
                </p>
              </div>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="text-[hsl(var(--background))] hover:bg-white/10 hover:text-[hsl(var(--background))]"
              onClick={closePanel}
              aria-label={messages.assistant.close}
              title={messages.assistant.close}
            >
              <X className="h-4 w-4" aria-hidden="true" />
            </Button>
          </header>

          <div
            role="log"
            aria-live="polite"
            aria-relevant="additions"
            aria-busy={isSending}
            className="min-h-48 flex-1 space-y-3 overflow-y-auto bg-background px-4 py-4"
          >
            {conversation.length === 0 ? (
              <div className="flex min-h-40 flex-col items-center justify-center gap-3 text-center">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-secondary text-primary">
                  <Bot className="h-5 w-5" aria-hidden="true" />
                </div>
                <p className="max-w-xs text-sm leading-6 text-muted-foreground">{messages.assistant.empty}</p>
              </div>
            ) : (
              conversation.map((message) => (
                <div
                  key={message.id}
                  role="article"
                  aria-label={message.role === 'user' ? messages.assistant.you : messages.assistant.label}
                  className={cn(
                    'max-w-[88%] rounded-md px-3.5 py-2.5 text-sm leading-6',
                    message.role === 'user'
                      ? 'ml-auto bg-primary text-primary-foreground'
                      : 'border border-border/70 bg-card text-foreground',
                  )}
                >
                  <p>{message.content}</p>
                  {message.role === 'assistant' && message.reasonCode ? (
                    <p className={cn('mt-2 text-[11px] uppercase', message.degraded ? 'text-amber-700' : 'text-muted-foreground')}>
                      {reasonLabel(message)}
                    </p>
                  ) : null}
                  {message.citations?.length ? (
                    <div className="mt-3 space-y-2 border-t border-border/70 pt-2" aria-label={messages.assistant.sources}>
                      {message.citations.map((citation) => (
                        <div key={citation.id} className="border-l-2 border-primary pl-2 text-xs leading-5">
                          <p className="font-semibold text-foreground">{citation.title}</p>
                          <p className="text-muted-foreground">{citation.source} · {citation.locale.toUpperCase()}</p>
                          <p className="mt-1 text-muted-foreground">{citation.excerpt}</p>
                        </div>
                      ))}
                    </div>
                  ) : null}
                </div>
              ))
            )}
            {isSending ? (
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
                {messages.assistant.thinking}
              </div>
            ) : null}
          </div>

          <form onSubmit={sendMessage} className="border-t border-border/70 bg-card p-3">
            <div className="flex items-end gap-2 rounded-md border border-border/80 bg-background p-2 focus-within:ring-2 focus-within:ring-ring">
              <textarea
                ref={inputRef}
                value={input}
                onChange={(event) => setInput(event.target.value)}
                onKeyDown={handleComposerKeyDown}
                placeholder={messages.assistant.placeholder}
                rows={2}
                maxLength={2000}
                className="min-h-12 flex-1 resize-none border-0 bg-transparent px-1 py-1 text-sm leading-6 text-foreground outline-none placeholder:text-muted-foreground"
                aria-label={messages.assistant.placeholder}
              />
              <Button type="submit" size="icon" disabled={!input.trim() || isSending} aria-label={messages.assistant.send}>
                <Send className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          </form>
        </section>
      )}
    </div>
  );
}
