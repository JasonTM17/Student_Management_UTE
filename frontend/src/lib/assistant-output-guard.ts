/**
 * Client-side mirror of the server AssistantOutputGuard.
 *
 * The server is authoritative for new answers. This copy protects the UI and
 * copied text when an old history row or a remote RAG deployment predates the
 * server guard.
 */

const INVISIBLE = /[\u00AD\u200B-\u200F\u2060-\u2064\u206A-\u206F\uFEFF]/g;

const CODE_FENCE = /```|~~~\s*(?:\w+)?(?:\n|$)/;
const SHELL_COMMAND = /(?:^|\n)\s*(?:[-•*]\s*)?(?:[$>#]\s*)?(?:curl|wget|invoke-webrequest|iwr|docker(?:\s+compose)?|docker-compose|npm|pnpm|yarn|bun|npx|mvnw?|gradlew?|git|kubectl|helm|psql|mysql|redis-cli|python3?|node|powershell|pwsh|bash|sh)\b/im;
const INLINE_COMMAND = /\b(?:curl|wget|invoke-webrequest|docker(?:\s+compose)?|docker-compose|kubectl|psql|mysql|redis-cli)\s+(?:https?:\/\/|[/-]|(?:compose|run|up|down|build|command|commands|example|instructions?)\b)/i;
const SQL_COMMAND = /(?:^|\n)\s*(?:select|insert|update|delete|drop|alter|create)\s+(?:from|into|table|database|schema|index|view|users?|assistant|chat|\*)/im;
const INTERNAL_ENDPOINT = /(?:https?:\/\/[^\s)]+\/api\/v\d(?:\/|\b)|(?<![\p{L}\p{N}_])\/api\/v\d(?:\/|\b)|\b(?:localhost|127\.0\.0\.1)\s*:\s*\d{2,5})/iu;
const INTERNAL_DETAIL = /\b(?:system\s+prompt|developer\s+message|retrieved\s+context|api\s+endpoints?|curl\s+commands?|docker\s+compose(?:\s+instructions?)?|stack\s+trace|traceback|deepseek(?:[- ]v?\d+)?|provider\s+(?:error|response|model)|api\s+key|jwt\s+secret|bearer\s+token|v4\s+flash)\b/i;
const STACK_TRACE = /(?:exception\s+in\s+thread|traceback\s*\(most\s+recent\s+call\s+last\)|\bat\s+[\w.$]+\([^\n)]*:\d+[:)]|caused\s+by:)/i;

function normalizeAssistantOutput(value: string): string {
  return value.replace(INVISIBLE, '').normalize('NFKC').toLowerCase();
}

/** Returns true when text is safe to show as an assistant answer. */
export function isAssistantOutputSafe(value: string | null | undefined): boolean {
  if (!value?.trim()) return true;
  const normalized = normalizeAssistantOutput(value);
  return !CODE_FENCE.test(normalized)
    && !SHELL_COMMAND.test(normalized)
    && !INLINE_COMMAND.test(normalized)
    && !SQL_COMMAND.test(normalized)
    && !INTERNAL_ENDPOINT.test(normalized)
    && !INTERNAL_DETAIL.test(normalized)
    && !STACK_TRACE.test(normalized);
}

/** Replace the complete answer so no safe-looking prefix can accompany a leak. */
export function sanitizeAssistantOutput(
  value: string,
  replacement: string,
): string {
  return isAssistantOutputSafe(value) ? value : replacement;
}
