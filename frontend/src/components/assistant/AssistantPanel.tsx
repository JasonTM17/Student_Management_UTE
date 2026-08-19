'use client';

import { FormEvent, useState } from 'react';
import { Bot, LoaderCircle, MessageCircle, Send, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import { thesisApi } from '@/lib/thesis-api';

interface ChatMessage {
  id: string;
  role: 'assistant' | 'user';
  content: string;
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
        { id: `${Date.now()}-assistant`, role: 'assistant', content: reply.answer },
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
    <div className="fixed bottom-[5.75rem] right-4 z-50 w-[min(24rem,calc(100vw-2rem))] md:bottom-6 sm:right-6">
      {!open ? (
        <Button
          type="button"
          size="lg"
          className="ml-auto flex rounded-full border border-white/15 bg-[hsl(var(--foreground))] px-5 text-[hsl(var(--background))] shadow-2xl hover:bg-[hsl(var(--foreground))/0.9]"
          onClick={() => setOpen(true)}
          aria-label={messages.assistant.open}
        >
          <MessageCircle className="mr-2 h-5 w-5" />
          {messages.assistant.label}
        </Button>
      ) : (
        <section
          role="dialog"
          aria-modal="false"
          aria-labelledby="assistant-panel-title"
          className="overflow-hidden rounded-2xl border border-border/80 bg-card shadow-2xl"
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
                    'max-w-[88%] rounded-2xl px-3.5 py-2.5 text-sm leading-6',
                    message.role === 'user'
                      ? 'ml-auto rounded-br-md bg-primary text-primary-foreground'
                      : 'rounded-bl-md border border-border/70 bg-card text-foreground',
                  )}
                >
                  {message.content}
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
            <div className="flex items-end gap-2 rounded-xl border border-border/80 bg-background p-2 focus-within:ring-2 focus-within:ring-ring">
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
