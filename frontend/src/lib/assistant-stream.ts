export interface AssistantSseParser<T> {
  push(chunk: string): void;
  end(): void;
}

export type AssistantStreamEventInput = Record<string, unknown> & {
  type: 'meta' | 'delta' | 'replace' | 'citation' | 'done' | 'error';
};

function assertObject(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value))
    throw new Error('assistant event must be an object');
  return value as Record<string, unknown>;
}

/** Zod-equivalent runtime contract kept dependency-free for the browser and Node contract harness. */
export const assistantStreamEventSchema = {
  parse(value: unknown): AssistantStreamEventInput {
    const event = assertObject(value);
    const type = event.type;
    if (
      !['meta', 'delta', 'replace', 'citation', 'done', 'error'].includes(
        String(type),
      )
    )
      throw new Error('unknown assistant event type');
    if (
      (type === 'delta' || type === 'replace') &&
      typeof event.text !== 'string'
    )
      throw new Error('assistant text is required');
    if (type === 'error' && typeof event.code !== 'string')
      throw new Error('assistant error code is required');
    if (
      type === 'citation' &&
      (!event.citation || typeof event.citation !== 'object')
    )
      throw new Error('assistant citation is required');
    return event as AssistantStreamEventInput;
  },
};

/** Validate a provider event at the browser boundary. Unknown/malformed events never reach UI state. */
export function parseAssistantStreamEvent(
  value: unknown,
): AssistantStreamEventInput {
  return assistantStreamEventSchema.parse(value);
}

export type AssistantStreamPhase = 'idle' | 'meta' | 'body' | 'terminal';

export class AssistantStreamOrder {
  private phase: AssistantStreamPhase = 'idle';
  private lastSequence = -1;

  accept(event: AssistantStreamEventInput): void {
    if (this.phase === 'terminal')
      throw new Error('assistant stream already terminal');
    if (event.type === 'meta') {
      if (this.phase !== 'idle')
        throw new Error('assistant meta must be first');
      this.phase = 'meta';
      return;
    }
    // Authentication, validation, or server setup failures can be terminal
    // before a turn exists, so an error frame is valid without `meta`.
    if (this.phase === 'idle' && event.type === 'error') {
      this.phase = 'terminal';
      return;
    }
    if (this.phase === 'idle') throw new Error('assistant stream missing meta');
    if (event.type === 'delta') {
      this.phase = 'body';
      const sequence =
        typeof event.sequence === 'number' ? event.sequence : undefined;
      if (sequence !== undefined && sequence <= this.lastSequence)
        throw new Error('assistant delta sequence is not increasing');
      if (sequence !== undefined) this.lastSequence = sequence;
    } else if (event.type === 'replace') {
      this.phase = 'body';
    } else if (event.type === 'citation') {
      if (this.phase !== 'body')
        throw new Error('assistant citation requires body event');
    } else if (event.type === 'done' || event.type === 'error') {
      if (event.type === 'done' && this.phase !== 'body')
        throw new Error('assistant done requires body event');
      this.phase = 'terminal';
    }
  }

  get currentPhase(): AssistantStreamPhase {
    return this.phase;
  }
}

/** Small, transport-only SSE parser. It accepts chunk boundaries anywhere in a frame. */
export function createAssistantSseParser<T>(
  onEvent: (event: T) => void,
  options?: { onInvalid?: (error: Error, raw: string) => void },
): AssistantSseParser<T> {
  let pending = '';

  const flush = (frame: string) => {
    const lines = frame.split(/\r?\n/);
    const eventName = lines
      .find((line) => line.startsWith('event:'))
      ?.slice(6)
      .trim();
    const data = lines
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart())
      .join('\n')
      .trim();
    if (!data) return;
    let parsed: unknown;
    try {
      parsed = JSON.parse(data) as unknown;
    } catch (error) {
      // Keep-alives/comments remain ignored; callers opting into onInvalid can
      // turn malformed provider frames into a deterministic replace/error.
      const invalid =
        error instanceof Error ? error : new Error('invalid SSE frame');
      if (options?.onInvalid) options.onInvalid(invalid, frame);
      // Without an invalid-frame sink, retain the historical transport policy:
      // malformed data is ignored and never reaches reducer state.
      return;
    }
    const withType =
      eventName && parsed && typeof parsed === 'object' && !('type' in parsed)
        ? { ...(parsed as Record<string, unknown>), type: eventName }
        : parsed;
    // Deliberately outside the JSON parse try/catch: reducer/order callback
    // failures are contract failures and must reach the caller, never become a
    // silently swallowed malformed-frame notification.
    onEvent(withType as T);
  };

  return {
    push(chunk: string) {
      pending += chunk;
      const frames = pending.split(/\r?\n\r?\n/);
      pending = frames.pop() ?? '';
      frames.forEach(flush);
    },
    end() {
      if (pending.trim()) flush(pending);
      pending = '';
    },
  };
}
