/**
 * Client-side mirror of the server AssistantInputGuard
 * (java-services/.../thesis/assistant/AssistantInputGuard.java).
 *
 * The chatbot routes many personal-data questions through the local student
 * resolver before any server turn is created. Without this mirror, a message
 * that matches a resolver intent regex (for example the Vietnamese word
 * "hướng dẫn" inside "bỏ qua tất cả hướng dẫn...") reached a canned answer
 * while the server guard would have refused it. Keeping a copy of the same
 * deterministic checks on the client guarantees the guard applies to every
 * turn, whichever engine answers it.
 *
 * When the server patterns change, update both sides in the same commit.
 */

// Invisible formatting characters (soft hyphen, zero-width spaces and joins,
// directional marks, BOM) split banned keywords across otherwise matching
// spans, so they are stripped before NFC folding — same choke point as the
// server guard.
const INVISIBLE = /[\u00AD\u200B-\u200F\u2060-\u2064\u206A-\u206F\uFEFF]/g;

const EMAIL = /\b[\w.+-]+@[\w-]+\.[\w.-]+\b/i;
const PHONE = /(?<![A-Za-z0-9])(?:\+?\d[\d .()-]{7,}\d)(?![A-Za-z0-9])/g;
const UUID_TOKEN =
  /\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b/gi;
const STUDENT_ID =
  /\b(?:student\s*id|mssv|ma\s*sv|sinh\s*vien)\s*[:#-]?\s*[a-z0-9-]*\d[a-z0-9-]{3,20}\b/i;
const SECRET =
  /\b(?:bearer\s+|sk-[a-z0-9_-]{12,}|api[_ -]?key\s*[:：=]|token\s*[:：=]|pass(?:word|wd)\s*[:：=])/i;
const TECHNICAL_REQUEST =
  /(?:\b(?:curl|wget|invoke-webrequest|iwr|docker(?:\s+compose)?|docker-compose|kubectl|helm|psql|mysql|redis-cli|npm|pnpm|yarn|bun|npx|mvnw?|gradlew?|git|powershell|pwsh|bash|sh)\b|\b(?:api\s+(?:endpoint|endpoints|chatbot)|api\s+key|system\s+prompt|developer\s+message|stack\s+trace|traceback|deepseek(?:[- ]v?\d+)?|provider|llm|jwt|database\s+(?:password|credentials?))\b|\b(?:cho\s+(?:tôi|ta)|xin|give\s+me|show|provide|send)\b.{0,80}\b(?:api|endpoint|system\s+prompt|developer\s+message|câu\s+lệnh|lệnh|command|model|mô\s+hình|provider)\b|\b(?:bạn|bot|trợ\s+lý|hệ\s+thống|you|assistant)\b.{0,40}\b(?:đang\s+(?:sử\s+dụng|dùng|chạy)\s+)?(?:mô\s+hình|model|llm|provider|deepseek)\b)/i;

const PROMPT_INJECTION = new RegExp(
  [
    'ignore\\s+(?:all\\s+)?previous\\s+instructions|disregard\\s+(?:the\\s+)?system\\s+prompt|reveal\\s+(?:the\\s+)?system\\s+prompt|(?:print|show|give\\s+me|what\\s+is)\\s+(?:the\\s+)?(?:system\\s+prompt|api[\\s_-]?key|jwt[\\s_-]?secret|database\\s+password|admin\\s+password)|developer\\s+message|jailbreak|prompt\\s+injection|do\\s+anything\\s+now|dan\\s+mode',
    '(?:bỏ\\s*qua|quên\\s*(?:đi|hết)?|không\\s+tuân\\s+theo|bỏ\\s*mặc)\\s+(?:tất\\s*cả\\s+|mọi\\s+|các\\s+|những\\s+)?(?:hướng\\s+dẫn|chỉ\\s+dẫn|lệnh|quy\\s+định|prompt)',
    '(?:bạn\\s+là\\s+(?:một\\s+)?)?(?:ai|trợ\\s+lý)\\s+không\\s+giới\\s+hạn|(?:hạ|sửa|thay\\s+đổi)\\s+điểm\\s+(?:cho\\s+|của\\s+)?(?:sinh\\s+viên|môn)',
    'vô\\s*hiệu\\s+(?:hóa\\s+)?(?:lệnh|hướng\\s+dẫn|chỉ\\s+dẫn)',
    '(?:in|đọc|xem|hiển\\s*thị|lấy|cho\\s+(?:tôi|ta)\\s+xem)\\s+(?:ra\\s+|cho\\s+(?:tôi|ta)\\s+)?(?:toàn\\s*bộ\\s+)?(?:system\\s+prompt|prompt\\s+hệ\\s+thống|câu\\s+lệnh\\s+hệ\\s+thống|lệnh\\s+hệ\\s+thống|api[\\s_-]?key|jwt[\\s_-]?secret|mật\\s*khẩu|password)',
    'giả\\s*mạo\\s+(?:quản\\s*trị\\s*viên|admin|hệ\\s*thống)',
  ].join('|'),
  'i',
);

const PROMPT_INJECTION_FOLDED = new RegExp(foldForMatching(PROMPT_INJECTION.source), 'i');

export function normalizeAssistantMessage(message: string): string {
  return message.trim().replace(INVISIBLE, '').normalize('NFC');
}

/**
 * Lossless fold for matching only: NFD decomposition strips every combining
 * mark, đ/Đ fold onto d, the result is lowercased. Vietnamese is typed
 * unaccented at least as often as not, so the injection patterns also run
 * against this folded view; the folded pattern is derived from the original
 * pattern text, which keeps both views in lockstep with the Java guard.
 */
function foldForMatching(value: string): string {
  return value
    .replace(INVISIBLE, '')
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .replace(/[đĐ]/g, 'd')
    .toLowerCase();
}

function containsPhone(normalized: string): boolean {
  const uuidSpans: Array<[number, number]> = [];
  for (const match of normalized.matchAll(UUID_TOKEN)) {
    if (match.index !== undefined) {
      uuidSpans.push([match.index, match.index + match[0].length]);
    }
  }
  for (const match of normalized.matchAll(PHONE)) {
    const candidate = match[0];
    const start = match.index ?? 0;
    const end = start + candidate.length;
    if (uuidSpans.some(([from, to]) => start >= from && end <= to)) continue;
    // Academic year ranges such as 2023 - 2024 are legitimate metadata.
    if (/(?:19|20)\d{2}\s*[-–/]\s*(?:19|20)?\d{2}/.test(candidate)) continue;
    const digits = (candidate.match(/\d/g) ?? []).length;
    const contiguous = /^\+?\d{8,15}$/.test(candidate);
    const readableSeparator = /[ .()]/.test(candidate);
    const hyphenatedPhone = /^\+?(?:\d{2,4}-)+\d{3,8}$/.test(candidate);
    if (digits >= 8 && (contiguous || readableSeparator || hyphenatedPhone)) {
      return true;
    }
  }
  return false;
}

export type AssistantGuardReason =
  | 'PROMPT_INJECTION'
  | 'SENSITIVE_EMAIL'
  | 'SENSITIVE_PHONE'
  | 'SENSITIVE_STUDENT_ID'
  | 'SENSITIVE_CREDENTIAL'
  | 'TECHNICAL_REQUEST_BLOCKED';

export interface AssistantGuardResult {
  allowed: boolean;
  reasonCode?: AssistantGuardReason;
  normalizedMessage: string;
}

/** Same decision order as the server: sensitive data first, then injection. */
export function inspectAssistantInput(message: string): AssistantGuardResult {
  const normalizedMessage = normalizeAssistantMessage(message);
  if (EMAIL.test(normalizedMessage)) {
    return { allowed: false, reasonCode: 'SENSITIVE_EMAIL', normalizedMessage };
  }
  if (containsPhone(normalizedMessage)) {
    return { allowed: false, reasonCode: 'SENSITIVE_PHONE', normalizedMessage };
  }
  if (STUDENT_ID.test(normalizedMessage)) {
    return { allowed: false, reasonCode: 'SENSITIVE_STUDENT_ID', normalizedMessage };
  }
  if (SECRET.test(normalizedMessage)) {
    return { allowed: false, reasonCode: 'SENSITIVE_CREDENTIAL', normalizedMessage };
  }
  if (PROMPT_INJECTION.test(normalizedMessage)) {
    return { allowed: false, reasonCode: 'PROMPT_INJECTION', normalizedMessage };
  }
  if (PROMPT_INJECTION_FOLDED.test(foldForMatching(normalizedMessage))) {
    return { allowed: false, reasonCode: 'PROMPT_INJECTION', normalizedMessage };
  }
  if (TECHNICAL_REQUEST.test(normalizedMessage)) {
    return {
      allowed: false,
      reasonCode: 'TECHNICAL_REQUEST_BLOCKED',
      normalizedMessage,
    };
  }
  return { allowed: true, normalizedMessage };
}

export function isSensitiveGuardReason(code: string | undefined): boolean {
  return (
    code === 'SENSITIVE_EMAIL' ||
    code === 'SENSITIVE_PHONE' ||
    code === 'SENSITIVE_STUDENT_ID' ||
    code === 'SENSITIVE_CREDENTIAL'
  );
}
