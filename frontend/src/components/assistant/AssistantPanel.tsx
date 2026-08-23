'use client';

import { FormEvent, useState } from 'react';
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
        { id: `${Date.now()}-error`, role: 'assistant', content: messages.assistant.unavailable },
      ]);
    } finally {
      setIsSending(false);
    }
  };

  return (
    <div className="relative z-20 mx-3 mt-4 w-auto pb-[calc(1rem+env(safe-area-inset-bottom))] md:fixed md:bottom-6 md:right-6 md:mx-0 md:mt-0 md:w-[min(24rem,calc(100vw-3rem))] md:pb-0">
      {!open ? (
        <Button
          type="button"
          size="lg"
          className="ml-auto flex h-11 w-11 justify-center rounded-full border border-white/15 bg-[hsl(var(--foreground))] px-0 text-[hsl(var(--background))] shadow-lg hover:bg-[hsl(var(--foreground))/0.9] md:w-auto md:justify-start md:rounded-md md:px-4"
          onClick={() => setOpen(true)}
          aria-label={messages.assistant.open}
        >
          <MessageCircle className="mr-0 h-5 w-5 md:mr-2" />
          <span className="hidden md:inline">{messages.assistant.label}</span>
        </Button>
      ) : (
        <section
          role="dialog"
          aria-modal="false"
          aria-labelledby="assistant-panel-title"
          className="overflow-hidden rounded-md border border-border/80 bg-card shadow-2xl"
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
                <p className="mt-1 text-xs leading-5 text-[hsl(var(--background))/0.7]">
                  {messages.assistant.description}
                </p>
              </div>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="text-[hsl(var(--background))] hover:bg-white/10 hover:text-[hsl(var(--background))]"
              onClick={() => setOpen(false)}
              aria-label={messages.assistant.close}
            >
              <X className="h-4 w-4" />
            </Button>
          </header>

          <div className="max-h-80 min-h-48 space-y-3 overflow-y-auto bg-background px-4 py-4">
            {conversation.length === 0 ? (
              <div className="flex min-h-40 flex-col items-center justify-center gap-3 text-center">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-secondary text-primary">
                  <Bot className="h-5 w-5" />
                </div>
                <p className="max-w-xs text-sm leading-6 text-muted-foreground">{messages.assistant.empty}</p>
              </div>
            ) : (
              conversation.map((message) => (
                <div
                  key={message.id}
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
                      {message.degraded ? 'DEGRADED' : message.reasonCode}
                    </p>
                  ) : null}
                  {message.citations?.length ? (
                    <div className="mt-3 space-y-2 border-t border-border/70 pt-2">
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
                <LoaderCircle className="h-4 w-4 animate-spin" />
                {messages.assistant.thinking}
              </div>
            ) : null}
          </div>

          <form onSubmit={sendMessage} className="border-t border-border/70 bg-card p-3">
            <div className="flex items-end gap-2 rounded-md border border-border/80 bg-background p-2 focus-within:ring-2 focus-within:ring-ring">
              <textarea
                value={input}
                onChange={(event) => setInput(event.target.value)}
                placeholder={messages.assistant.placeholder}
                rows={2}
                maxLength={2000}
                className="min-h-12 flex-1 resize-none border-0 bg-transparent px-1 py-1 text-sm leading-6 text-foreground outline-none placeholder:text-muted-foreground"
                aria-label={messages.assistant.placeholder}
              />
              <Button type="submit" size="icon" disabled={!input.trim() || isSending} aria-label={messages.assistant.send}>
                <Send className="h-4 w-4" />
              </Button>
            </div>
          </form>
        </section>
      )}
    </div>
  );
}
