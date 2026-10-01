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
/**
 * Leak markers that stay banned in EVERY scope: they name the machinery, never
 * a lesson. The technical-phrasing half below is separate because the
 * specialized corpus legitimately TEACHES it (Docker Compose, pipelines,
 * EXPLAIN plans) — mirroring the server, where scope='specialized' skips the
 * technical-request gate (round-3 chat-8).
 */
const INTERNAL_DETAIL_SECRET = /\b(?:system\s+prompt|developer\s+message|stack\s+trace|traceback|deepseek(?:[- ]v?\d+)?|provider\s+(?:error|response|model)|api\s+key|jwt\s+secret|bearer\s+token|v4\s+flash)\b/i;
const INTERNAL_DETAIL_TECH = /\b(?:retrieved\s+context|api\s+endpoints?|curl\s+commands?|docker\s+compose(?:\s+instructions?)?)\b/i;
const STACK_TRACE = /(?:exception\s+in\s+thread|traceback\s*\(most\s+recent\s+call\s+last\)|\bat\s+[\w.$]+\([^\n)]*:\d+[:)]|caused\s+by:)/i;

/**
 * Answer scope. 'specialized' is the curated professional corpus (DevOps,
 * databases, testing…), whose answers legitimately contain command-style
 * study content — the technical phrasing checks are skipped there while every
 * secret/endpoint/stack-trace check still applies. 'academic' (default) keeps
 * the full strict set.
 */
export type AssistantScope = 'academic' | 'specialized';
// English function words the provider glues to a following number. The list
// stays narrow and case-sensitive on purpose: a case-insensitive `is` used to
// rewrite the course code "IS101" into "IS 101".
const ENGLISH_NUMBER_GLUE_AFTER_WORD = /(?<![\p{L}\p{N}_])(?:of|from|to|up to|at least|at most|minimum|maximum)(?=\d)/gu;
const ENGLISH_NUMBER_GLUE_BEFORE_WORD = /(?<=\d)(?:credits?|courses?|weeks?|days?|students?|members?|terms?|months?)(?![\p{L}])/gu;
const NUMBER_GLUE_AFTER_LABEL_COLON = /(?<=[\p{L}])(:)(?=\d)/gu;
/** Grade-table glue "B+:3.5" — colon after a sign; times keep digit-before-colon. */
const NUMBER_GLUE_AFTER_SIGN_COLON = /(?<=[A-Za-z][+\-])(:)(?=\d)/gu;
/** Characters that make up an identifier, URL, email address or anchor. */
const TOKEN_CHARS = /[A-Za-z0-9._+#?&=~/@%-]/;
/** A run carrying one of these is a link, address or anchor, not prose. */
const TOKEN_MARKERS = /[@?#&=]|:\/\//;
/**
 * Vietnamese prose always separates a number from the preceding word, but
 * provider output glues them ("trong4 tuần", "thang10", "đủ100%"). A word
 * allowlist kept missing cases, so the rule is inverted: a letter run of two or
 * more followed directly by a digit gains a space.
 *
 * Uppercase ASCII runs are identifiers (SE101, V38, TOEIC), and a run carrying
 * a URL, email or anchor marker is left alone so "hoten2020@…", "?nam2026=1"
 * and "#muc4" are never split.
 */
const WORD_GLUE_BEFORE_DIGIT = /(?<![\p{L}\p{N}_\/.-])(\p{L}{2,})(\p{N})/gu;
const ASCII_IDENTIFIER_WORD = /^[A-Z]+$/;

function isTokenLike(text: string, start: number, end: number): boolean {
  let left = start;
  while (left > 0 && TOKEN_CHARS.test(text[left - 1])) left -= 1;
  let right = end;
  while (right < text.length && TOKEN_CHARS.test(text[right])) right += 1;
  const run = text.slice(left, right);
  return TOKEN_MARKERS.test(run) || run.startsWith('#');
}

export function separateWordsFromNumbers(value: string): string {
  return value.replace(
    WORD_GLUE_BEFORE_DIGIT,
    (match, word: string, digit: string, offset: number, whole: string) => {
      if (ASCII_IDENTIFIER_WORD.test(word)) return match;
      if (isTokenLike(whole, offset, offset + match.length)) return match;
      return `${word} ${digit}`;
    },
  );
}
/**
 * Mid-word lowercase→uppercase jump: the remote provider concatenates
 * Vietnamese word pairs without a space ("Xây dựngRiêng", "hoãn thiSinh viên").
 * Vietnamese prose never legitimately runs a lowercase letter straight into an
 * uppercase-starting syllable, so a space is restored at each jump — URL, email
 * and anchor tokens are skipped via isTokenLike. Mirrors the server
 * WORD_CAP_GLUE pass in ThesisAssistantService.normalizeAssistantCopy.
 */
const WORD_CAP_GLUE = /(?<=[\p{Ll}])(?=[\p{Lu}][\p{Ll}])/gu;

export function separateGluedWords(value: string): string {
  return value.replace(WORD_CAP_GLUE, (_match, offset: number, whole: string) => {
    // The jump sits inside a letter run; expand it over every Unicode letter.
    let start = offset;
    while (start > 0 && /[\p{L}]/u.test(whole[start - 1])) start -= 1;
    let end = offset;
    while (end < whole.length && /[\p{L}]/u.test(whole[end])) end += 1;
    const run = whole.slice(start, end);
    // The decision rests on the LOWERCASE side of the jump: Vietnamese glue
    // carries diacritics on its own word ("dựngRiêng"). A pure-ASCII left
    // side is a camelCase identifier ("CampusCoreMở") — never split there;
    // that join is covered by the heading allowlist instead.
    if (start === end || !/[^\x00-\x7F]/.test(whole.slice(start, offset))) return '';
    return ' ';
  });
}
const MARKDOWN_HEADING_SENTENCE_GLUE = /^(#{1,6}[ \t]+(?:Cách đăng ký học phần trên CampusCore|Cách đăng ký học phần|Đăng ký học phần trên CampusCore|Đăng ký học phần|Các bước đăng ký|Khi gặp thông báo từ hệ thống|Khi gặp thông báo|Lưu ý về điều kiện học phần|Lưu ý về học phần điều kiện|Thời gian đăng ký|Thủ tục xin hoãn thi|Thủ tục hoãn thi|Quy định hoãn thi|Chính sách miễn giảm học phí|How to register for a course in CampusCore|Course registration on CampusCore|When you see a system message|If Registration Is Blocked|What Happens During Add\/Drop|Add\/Drop Period|Credit Load Rules|Related Rules to Keep in Mind|Prerequisites and Limits|Prerequisites and Retakes|Course requirements|Registration timing|Registering for a Course|Prerequisites and Related Requirements|During Add\/Drop|Withdrawal and Credit Workload|Retakes and Grade Improvement|Prerequisites and Related Courses|Credit Limits))[ \t]*(?=[\p{Lu}\p{N}•*-])/gimu;
const MARKDOWN_HEADING_DASH_SENTENCE_GLUE = /^(#{1,6}[ \t]+[^\r\n]*?\S)[ \t]*(?=-[ \t]+(?:A|An|Before|Check|Each|If|Open|Prerequisite|Prerequisites|Review|Sections|The|This|To|Use|While|When|You|Bạn|Các|Cần|Chọn|Hãy|Khi|Kiểm|Lớp|Mở|Nếu|Xem|Để|Đợt)\b)/gimu;
const EMPHASIZED_HEADING_SENTENCE_GLUE = /^((?:\*\*|__)(?:Cách đăng ký học phần trên CampusCore|Cách đăng ký học phần|Đăng ký học phần trên CampusCore|Đăng ký học phần|Các bước đăng ký|Khi gặp thông báo từ hệ thống|Khi gặp thông báo|Lưu ý về điều kiện học phần|Lưu ý về học phần điều kiện|Thời gian đăng ký|How to register for a course in CampusCore|Course registration on CampusCore|When you see a system message|If Registration Is Blocked|What Happens During Add\/Drop|Add\/Drop Period|Credit Load Rules|Related Rules to Keep in Mind|Prerequisites and Limits|Prerequisites and Retakes|Course requirements|Registration timing|Registering for a Course|Prerequisites and Related Requirements|During Add\/Drop|Withdrawal and Credit Workload|Retakes and Grade Improvement|Prerequisites and Related Courses|Credit Limits)(?:\*\*|__))[ \t]*(?=[\p{Lu}\p{N}•*-])/gimu;
const EMPHASIZED_HEADING_DASH_SENTENCE_GLUE = /^((?:\*\*|__)[^\r\n]*?\S)[ \t]*(?=-[ \t]+(?:A|An|Before|Check|Each|If|Open|Prerequisite|Prerequisites|Review|Sections|The|This|To|Use|While|When|You|Bạn|Các|Cần|Chọn|Hãy|Khi|Kiểm|Lớp|Mở|Nếu|Xem|Để|Đợt)\b)/gimu;

/**
 * Markdown emphasis and list markers require preceding whitespace or a line
 * start, so a marker abutting a word means the provider concatenated two blocks.
 *
 * Only an OPENING `**` is repaired. Markers are counted per line, which leaves
 * the closing marker of a bold run intact — "**Điểm**4.0" is not touched, while
 * "học phí**Hạn nộp học phí**" gains the missing block boundary.
 */
const EMPHASIS_MARKER = '**';

export function repairEmphasisGlue(value: string): string {
  return value
    .split('\n')
    .map((line) => {
      let out = '';
      let markers = 0;
      let openingAtLineStart = false;
      let index = 0;
      while (index < line.length) {
        if (line.startsWith(EMPHASIS_MARKER, index)) {
          const before = index > 0 ? line[index - 1] : '';
          const after = line[index + EMPHASIS_MARKER.length] ?? '';
          if (markers % 2 === 0) {
            openingAtLineStart = index === 0;
            if (/\p{L}/u.test(before) && /[\p{L}\p{N}]/u.test(after)) out += '\n\n';
            out += EMPHASIS_MARKER;
            markers += 1;
            index += EMPHASIS_MARKER.length;
            continue;
          }
          // Closing marker: text glued straight after it is a concatenated block
          // ("**Hậu quả…**Sinh viên không đóng…"). A bold heading opens at the
          // line start, so it regains a block boundary; an inline run only gains
          // the missing space. A digit after the marker means the run is inline
          // emphasis over a number ("**Điểm**4.0"), which stays as written.
          out += EMPHASIS_MARKER;
          markers += 1;
          index += EMPHASIS_MARKER.length;
          if (/\p{L}/u.test(after)) out += openingAtLineStart ? '\n\n' : ' ';
          continue;
        }
        out += line[index];
        index += 1;
      }
      return out;
    })
    .join('\n');
}

/**
 * A heading marker glued directly after a word loses its block boundary
 * ("…gia hạn học phí## Thời hạn nộp học phí"). Requiring a lowercase letter
 * before the marker keeps "C# là…" intact, and requiring whitespace after it
 * keeps anchors such as "#muc4" intact.
 */
const HEADING_GLUE = /(?<=[\p{Ll}])(#{1,6})[ \t]+/gu;

/**
 * A list marker glued to the end of a word is concatenated output
 * ("học lại- Điểm F"). Abbreviated labels ("Nhóm SV- K20") and ordinary
 * hyphenated prose ("self- study", "5 - 7 ngày") stay untouched.
 */
export function repairBulletGlue(value: string): string {
  return value.replace(/(?<=[\p{L}])-[ \t]/gu, (match, offset: number, whole: string) => {
    let start = offset;
    // The `u` flag is required: without it `\p{L}` is a literal sequence, not a
    // Unicode property escape, and the scan would never walk back over a word.
    while (start > 0 && /[\p{L}\p{N}.]/u.test(whole[start - 1])) start -= 1;
    if (/^[A-Z]{2,}$/.test(whole.slice(start, offset))) return match;
    if (!/^[\p{Lu}\p{N}•*]/u.test(whole.slice(offset + match.length))) return match;
    return '\n\n- ';
  });
}

/** An indented list marker mid-line is glued output, but only after a clause
 * terminator, so ranges such as "5 - 7 ngày" stay intact. */
const INLINE_LIST_GAP = /(?<=[.:;!?*])[ \t]+(?=-[ \t])/gu;
/**
 * Two or more spaces before a list marker is also concatenation rather than
 * intentional spacing: provider output produces "## Điều kiện tốt nghiệp  -
 * Tích lũy…", which would otherwise pull the first bullet into the heading.
 * A heading that legitimately uses " - " as a separator has single spaces and
 * is left alone.
 */
const DOUBLE_SPACE_LIST_GAP = /[ \t]{2,}(?=-[ \t])/gu;

function normalizeAssistantOutput(value: string): string {
  return value.replace(INVISIBLE, '').normalize('NFKC').toLowerCase();
}

/**
 * Mirrors the server's bounded formatting repair for old history rows and
 * remote answers that predate the latest provider boundary.
 */
export function normalizeAssistantCopy(value: string, locale: string = 'vi'): string {
  const repaired = separateGluedWords(separateWordsFromNumbers(value))
    .replace(ENGLISH_NUMBER_GLUE_AFTER_WORD, '$& ')
    .replace(ENGLISH_NUMBER_GLUE_BEFORE_WORD, ' $&')
    .replace(NUMBER_GLUE_AFTER_LABEL_COLON, '$1 ')
    .replace(NUMBER_GLUE_AFTER_SIGN_COLON, '$1 ')
    .replace(MARKDOWN_HEADING_SENTENCE_GLUE, '$1\n\n')
    .replace(MARKDOWN_HEADING_DASH_SENTENCE_GLUE, '$1\n\n')
    .replace(EMPHASIZED_HEADING_SENTENCE_GLUE, '$1\n\n')
    .replace(EMPHASIZED_HEADING_DASH_SENTENCE_GLUE, '$1\n\n')
    .replace(INLINE_LIST_GAP, '\n')
    .replace(DOUBLE_SPACE_LIST_GAP, '\n')
    .replace(HEADING_GLUE, '\n\n$1 ');
  // Marker-aware passes run after the regex chain: they need the emphasis
  // state of the whole line, not a single match window.
  const glued = repairBulletGlue(repairEmphasisGlue(repaired));
  if (locale === 'en') {
    return glued
      // Meta-voice backstop mirroring the server normalizeAssistantCopy: the
      // model refers to its own information set ("not in the available
      // information"); the answer must speak as the portal (audit kien-thuc).
      .replace(
        /not (?:available|contained|present|found|listed) (?:in|from) the (?:available|provided|published)(?: academic)? (?:information|data|context|sources)/gi,
        'not published by the portal',
      )
      .replace(/\bin the (?:available|provided)(?: academic)? (?:information|data|sources)\b/gi,
        'in the published academic information')
      .replace(/\bthe\s+ADD_DROP_OPEN\b/gi, 'the open add/drop period')
      .replace(/\bADD_DROP_OPEN\b/g, 'the open add/drop period')
      .replace(/\bthe\s+REGISTRATION_OPEN\b/gi, 'the open registration period')
      .replace(/\bREGISTRATION_OPEN\b/g, 'the open registration period')
      .replace(/\bthe\s+ADD_DROP\b/gi, 'the add/drop period')
      .replace(/\bADD_DROP\b/g, 'add/drop period')
      .replace(/\bthe\s+REGISTRATION\b/gi, 'the registration period')
      .replace(/\bREGISTRATION\b/g, 'registration period')
      .replace(/\bperiod(?:\s+period)+\b/gi, 'period');
  }
  return glued
    .replace(/Đợt\s+ADD_DROP_OPEN\b/g, 'Đợt bổ sung/rút học phần đang mở')
    .replace(/đợt\s+ADD_DROP_OPEN\b/gi, 'đợt bổ sung/rút học phần đang mở')
    .replace(/\bADD_DROP_OPEN\b/g, 'đợt bổ sung/rút học phần đang mở')
    .replace(/Đợt\s+REGISTRATION_OPEN\b/g, 'Đợt đăng ký đang mở')
    .replace(/đợt\s+REGISTRATION_OPEN\b/gi, 'đợt đăng ký đang mở')
    .replace(/\bREGISTRATION_OPEN\b/g, 'đợt đăng ký đang mở')
    .replace(/Đợt\s+ADD_DROP\b/g, 'Đợt bổ sung/rút học phần')
    .replace(/đợt\s+ADD_DROP\b/gi, 'đợt bổ sung/rút học phần')
    .replace(/\bADD_DROP\b/g, 'đợt bổ sung/rút học phần')
    .replace(/Đợt\s+REGISTRATION\b/g, 'Đợt đăng ký')
    .replace(/đợt\s+REGISTRATION\b/gi, 'đợt đăng ký')
    .replace(/\bREGISTRATION\b/g, 'đợt đăng ký')
    // Meta-voice backstop mirroring the server normalizeAssistantCopy (audit
    // kien-thuc): the model hedged with "chưa có trong thông tin được công bố
    // ở đây" / "dữ liệu học vụ mà tôi hỗ trợ" / switching to "Tôi" — the answer
    // must speak as the portal, in "mình". Ordered specific-to-general, same
    // as the server chain.
    .replace(/chưa(?: được)? quy định trong thông tin(?: hiện có| được cung cấp| được công bố)?/gi, 'chưa được quy định')
    .replace(/chưa có trong thông tin(?: được công bố)?(?: ở đây| tại đây)?/gi, 'chưa được công bố')
    .replace(/(?:không có|chưa có) trong (?:dữ liệu|nội dung) học vụ(?: mà tôi hỗ trợ)?/gi, 'chưa được Cổng học vụ công bố')
    .replace(/(?:dữ liệu|nội dung) học vụ(?: của bạn)? mà tôi hỗ trợ/gi, 'dữ liệu học vụ công khai')
    .replace(/\bchưa được nêu\b/gi, 'chưa công bố')
    .replace(/\bTôi chỉ có thể giúp(?: về)?/g, 'Mình chỉ hỗ trợ');
}

/** Returns true when text is safe to show as an assistant answer. */
export function isAssistantOutputSafe(
  value: string | null | undefined,
  scope: AssistantScope = 'academic',
): boolean {
  if (!value?.trim()) return true;
  const normalized = normalizeAssistantOutput(value);
  if (INTERNAL_ENDPOINT.test(normalized)
      || INTERNAL_DETAIL_SECRET.test(normalized)
      || STACK_TRACE.test(normalized)) {
    return false;
  }
  // Round-5: the specialized corpus teaches command-style content; its answers
  // were replaced wholesale by the technical-refusal copy ("Docker compose up"
  // in a DevOps lesson tripped INLINE_COMMAND/INTERNAL_DETAIL_TECH).
  if (scope === 'specialized') return true;
  return !CODE_FENCE.test(normalized)
    && !SHELL_COMMAND.test(normalized)
    && !INLINE_COMMAND.test(normalized)
    && !SQL_COMMAND.test(normalized)
    && !INTERNAL_DETAIL_TECH.test(normalized);
}

/** Replace the complete answer so no safe-looking prefix can accompany a leak. */
export function sanitizeAssistantOutput(
  value: string,
  replacement: string,
  locale: string = 'vi',
  scope: AssistantScope = 'academic',
): string {
  return isAssistantOutputSafe(value, scope) ? normalizeAssistantCopy(value, locale) : replacement;
}
