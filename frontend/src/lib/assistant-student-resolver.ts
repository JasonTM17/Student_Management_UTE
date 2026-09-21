import { authApi } from '@/lib/api';

/**
 * Local smalltalk resolver for the CampusCore chatbot.
 *
 * Scope decision (2026-09): the client answers ONLY trivial smalltalk —
 * greetings, capability introductions, thanks, and goodbyes — with static
 * copy streamed through a locally simulated SSE sequence. Every
 * personal-data intent (schedule, materials, grades, conduct, tuition,
 * announcements, thesis status, registration eligibility, profile) and every
 * regulation question is delegated to the backend:
 *
 * - `AssistantPersonalContextAdvisor` (Spring) answers personal schedule and
 *   thesis-status questions from real database rows.
 * - The RAG pipeline (rag-service + DeepSeek) answers knowledge questions and
 *   returns explicit degraded fallbacks when it cannot.
 *
 * `resolveStudentAssistantQuery` therefore returns null for anything that is
 * not smalltalk, and `useAssistantStream` forwards those turns to
 * /assistant/chat (or /assistant/chat/stream) instead of synthesizing an
 * answer from locally fetched API data. The client-side input guard
 * (assistant-input-guard.ts) still runs first so blocked or sensitive input
 * cannot ride along with a smalltalk phrasing.
 */

// 0. GREETING & CASUAL HELLO — anchored to the WHOLE message. A greeting is
// smalltalk only when it is the entire message; "Chào bạn, quy định vắng thi
// mấy lần bị cấm thi?" must reach the server, so a leading greeting may never
// claim the words that follow it.
const GREETING_REGEX =
  /^(?:hello|hi|hey|alo|halo|xin\s*chào|xin\s*chao|chào\s*(?:bạn|ban|em|anh|chị|chi|thầy|cô|bot|ad|admin|cậu|cau)?|chao\s*(?:ban|em|anh|chi|thay|co|bot|ad|admin|cau)?|chào|chao|good\s*(?:morning|afternoon|evening|day)|chào\s*buổi\s*(?:sáng|chiều|tối)|chao\s*buoi\s*(?:sang|chieu|toi))\s*[!.]*$/i;

// 0.1 ASSISTANT CAPABILITIES & SELF INTRODUCTION
// (R1) This branch only answers genuine capability questions, and only when the
// capability question IS the message. "Hướng dẫn sử dụng phần mềm APA để trích
// dẫn?" is a real question that belongs to the knowledge base.
const CAPABILITIES_REGEX =
  /^(?:(?:bạn|ban|em|cậu|bot|trợ\s*lý|tro\s*ly)\s*(?:là\s*(?:ai|gì)|la\s*(?:ai|gi)|tên\s*gì|ten\s*gi|có\s*thể\s*làm\s*(?:được\s*)?gì|giúp\s*(?:được\s*)?gì|hỗ\s*trợ\s*(?:được\s*)?gì|chức\s*năng\s*gì|làm\s*được\s*gì)|hướng\s*dẫn\s*(?:sử\s*dụng|dùng)|giới\s*thiệu\s*(?:về\s*)?(?:bạn|mình|bot|trợ\s*lý)|who\s*are\s*you|what\s+can\s+you\s+do|help)\s*[!.]*$/i;

// 0.2 THANK YOU & APPRECIATION — anchored, with a short courtesy tail only.
// An unanchored "cảm ơn" once swallowed "Cảm ơn bạn, thư viện mở cửa đến mấy
// giờ?" into a canned acknowledgement and the question was never answered.
const THANK_YOU_REGEX =
  /^(?:cảm\s*ơn|cam\s*on|cám\s*ơn|thank\s*you|thanks|tuyệt\s*vời|tuyet\s*voi|quá\s*tốt|qua\s*tot|tốt\s*lắm|tot\s*lam)(?:\s+(?:bạn|ban|nhiều|nhieu|nhé|nhe|lắm|lam|rất|rat))*\s*[!.]*$/i;

// 0.3 GOODBYE & FAREWELL
const GOODBYE_REGEX =
  /^(?:tạm\s*biệt|tam\s*biet|bye|goodbye|hẹn\s*gặp\s*lại|hen\s*gap\s*lai|chào\s*tạm\s*biệt)(?:\s+(?:bạn|ban|nhé|nhe|nha))*\s*[!.]*$/i;

// Smalltalk may only claim a message that is nothing BUT smalltalk. The full
// match above is necessary but not sufficient — a courtesy word riding in front
// of a question used to be answered locally while the question was dropped. Two
// further guards make that impossible:
//   * a question mark anywhere means there is something to answer, and
//   * a bare courtesy is at most SMALLTALK_MAX_WORDS long, so "cảm ơn bạn thư
//     viện mở cửa lúc mấy giờ" can never ride the ceiling either.
const SMALLTALK_MAX_WORDS = 6;

function isBareSmalltalk(message: string): boolean {
  const trimmed = message.trim();
  if (!trimmed || /[?？]/.test(trimmed)) return false;
  if (trimmed.split(/\s+/).length > SMALLTALK_MAX_WORDS) return false;
  return (
    GREETING_REGEX.test(trimmed) ||
    CAPABILITIES_REGEX.test(trimmed) ||
    THANK_YOU_REGEX.test(trimmed) ||
    GOODBYE_REGEX.test(trimmed)
  );
}

// Personal-data keywords that must never be answered with smalltalk. A
// message can open with a greeting or a "cảm ơn" and still ask for the
// asker's own records ("chào thầy, lịch học hôm nay thế nào?"); when any of
// these intent families appears, the message is delegated to the backend
// (personal-context advisor / RAG pipeline) instead of the local smalltalk
// copy. The families mirror the intents that used to be resolved client-side:
// schedule, materials, grades, conduct (ĐRL), tuition, announcements, thesis,
// curriculum, registration, teaching load, and profile.
const PERSONAL_DATA_HINT_REGEX =
  /(?:lịch|\blich\b|\btkb\b|thời\s*kho[áa]|thoi\s*khoa|schedule|timetable|classes|thứ\s*[2-7]|thu\s*[2-7]|\bt[2-7]\b|chủ\s*nhật|chu\s*nhat|tiết|tiet|giảng\s*dạy|giang\s*day|ca\s*(?:dạy|day|học|hoc)|phụ\s*trách|phu\s*trach|điểm|diem|\bgpa\b|transcript|\bgrades?\b|grade\s*point|bảng\s*điểm|bang\s*diem|học\s*lực|hoc\s*luc|tín\s*chỉ|tin\s*chi|rèn\s*luyện|ren\s*luyen|\bđrl\b|\bdrl\b|học\s*ph[íi]|hoc\s*phi|tuition|công\s*nợ|cong\s*no|đồ\s*án|do\s*an\b|khóa\s*luận|khoa\s*luan|tốt\s*nghiệp|tot\s*nghiep|thesis|capstone|bảo\s*vệ|bao\s*ve|thông\s*báo|thong\s*bao|tin\s*tức|tin\s*tuc|announcement|\bnews\b|học\s*liệu|hoc\s*lieu|tài\s*liệu|tai\s*lieu|giáo\s*trình|giao\s*trinh|\bslide\b|materials|lecture\s*notes|đăng\s*ký|dang\s*ky|registration|enrol|chương\s*trình\s*đào|chuong\s*trinh\s*dao|khung\s*đào|khung\s*dao|curriculum|mssv|student\s*id|profile|hồ\s*sơ|ho\s*so|thông\s*tin\s*cá\s*nhân|thong\s*tin\s*ca\s*nhan|sinh\s*viên|sinh\s*vien|thư\s*viện|thu\s*vien|library|vắng\s*thi|vang\s*thi|cấm\s*thi|cam\s*thi|quy\s*chế|quy\s*che|quy\s*định|quy\s*dinh|xét\s*tuyển|xet\s*tuyen|học\s*bổng|hoc\s*bong|thời\s*gian|thoi\s*gian|mấy\s*giờ|may\s*gio|opening\s*hours)/i;

// A first-person possessive ("của em là gì", "của tôi thế nào", "my …") inside
// an otherwise smalltalk-shaped message signals a record question, not chat:
// "thưa thầy, học phần tiên quyết của em là gì?" contains the "em là gì"
// fragment but must reach the server knowledge base.
const PERSONAL_POSSESSIVE_HINT_REGEX =
  /(?:của\s+(?:tôi|mình|em|anh|chị|tụi\s+tôi|chúng\s+tôi)|cua\s+(?:toi|minh|em|anh|chi|tu\s*toi|chung\s*toi)|\bmy\b)/i;

export function isStudentAssistantQuery(message: string): boolean {
  // Personal-data wording wins over smalltalk wording inside the same
  // message: "chào thầy, lịch học hôm nay thế nào?" streams to the backend.
  if (PERSONAL_DATA_HINT_REGEX.test(message)) return false;
  if (PERSONAL_POSSESSIVE_HINT_REGEX.test(message)) return false;
  // And a message must be smalltalk ALL THE WAY THROUGH. The word ceiling and
  // the question-mark veto below are the load-bearing guard: the hint lists can
  // never enumerate every topic a student might attach to "cảm ơn".
  return isBareSmalltalk(message);
}

export interface StudentAssistantResolution {
  answer: string;
}

/**
 * Answers trivial smalltalk locally and returns null for everything else.
 *
 * A null return is the delegation signal: `useAssistantStream` then creates a
 * real server turn through /assistant/chat/stream, where the backend
 * personal-context advisor and the RAG pipeline own schedule, grades,
 * conduct, tuition, announcement, thesis, registration, and regulation
 * questions.
 */
export async function resolveStudentAssistantQuery(
  message: string,
  locale: 'vi' | 'en',
): Promise<StudentAssistantResolution | null> {
  if (!isStudentAssistantQuery(message)) return null;

  let displayName = locale === 'vi' ? 'bạn' : 'there';
  try {
    const currentUser = await authApi.me();
    if (currentUser?.firstName) {
      displayName =
        `${currentUser.lastName ? currentUser.lastName + ' ' : ''}${currentUser.firstName}`.trim() ||
        displayName;
    }
  } catch {
    // Unauthenticated or background fallback: keep the neutral display name.
  }

  // 0. GREETING & CASUAL HELLO
  if (GREETING_REGEX.test(message)) {
    const greetingHeader =
      locale === 'vi'
        ? `Chào **${displayName}**! Mình là **Trợ lý học vụ CampusUTE**.`
        : `Hello **${displayName}**! I’m the **CampusUTE academic assistant**.`;

    const answer =
      locale === 'vi'
        ? `${greetingHeader}\n\n` +
          `Mình hỗ trợ tra cứu thông tin học vụ công khai và dữ liệu cá nhân mà tài khoản của bạn được phép xem. Bạn có thể hỏi về:\n\n` +
          `1. **Lịch học**: lớp học, thời gian, phòng và lịch theo ngày.\n` +
          `2. **Điểm số**: điểm học phần, GPA và bảng điểm.\n` +
          `3. **Đăng ký học phần**: điều kiện, thời hạn và số tín chỉ theo từng học kỳ.\n` +
          `4. **Đồ án hoặc khóa luận**: tiến độ, đề tài và các mốc cần lưu ý.\n` +
          `5. **Thông báo và quy định**: hướng dẫn, chính sách và biểu mẫu học vụ.\n\n` +
          `Bạn muốn bắt đầu với nội dung nào?`
        : `${greetingHeader}\n\n` +
          `I can help with public academic guidance and personal records that your account is allowed to view. You can ask about:\n\n` +
          `1. **Schedules**: classes, times, rooms, and a specific day.\n` +
          `2. **Grades**: course results, GPA, and your transcript.\n` +
          `3. **Course registration**: eligibility, deadlines, and term credit limits.\n` +
          `4. **Thesis or capstone work**: progress, topics, and important dates.\n` +
          `5. **Announcements and regulations**: official guidance and forms.\n\n` +
          `What would you like to check first?`;

    return { answer };
  }

  // 0.1 CAPABILITIES / WHO ARE YOU / HELP
  if (CAPABILITIES_REGEX.test(message)) {
    const answer =
      locale === 'vi'
        ? `Mình là **Trợ lý học vụ CampusUTE**.\n\n` +
          `Mình có thể:\n` +
          `- Tra cứu lịch học, điểm số, thông báo và tiến độ học tập của bạn khi dữ liệu được cung cấp.\n` +
          `- Giải thích các hướng dẫn học vụ công khai dựa trên nguồn đã được duyệt.\n` +
          `- Hỗ trợ sinh viên và giảng viên theo quyền truy cập của từng tài khoản.\n\n` +
          `Nếu chưa đủ căn cứ, mình sẽ nói rõ thay vì tự suy đoán. Bạn có thể hỏi: *"Hôm nay tôi có lịch học không?"* hoặc *"Điểm GPA của tôi là bao nhiêu?"*.`
        : `I’m the **CampusUTE academic assistant**.\n\n` +
          `I can:\n` +
          `- Check your schedules, grades, announcements, and academic progress when the records are available.\n` +
          `- Explain public academic guidance from approved sources.\n` +
          `- Support students and lecturers within each account’s access.\n\n` +
          `When the available evidence is insufficient, I will say so instead of guessing. Try asking: *"Do I have classes today?"* or *"What is my GPA?"*.`;

    return { answer };
  }

  // 0.2 THANK YOU / PRAISE
  if (THANK_YOU_REGEX.test(message)) {
    const answer =
      locale === 'vi'
        ? `Không có gì! Nếu cần, bạn có thể hỏi mình về lịch học, điểm số, đăng ký học phần hoặc đồ án tốt nghiệp.`
        : `You’re welcome. You can ask me about schedules, grades, course registration, or thesis work whenever you need.`;

    return { answer };
  }

  // 0.3 GOODBYE / FAREWELL
  if (GOODBYE_REGEX.test(message)) {
    const answer =
      locale === 'vi'
        ? `Hẹn gặp lại **${displayName}**. Chúc bạn học tập hiệu quả!`
        : `Goodbye **${displayName}**. Have a productive day!`;

    return { answer };
  }

  // Nothing the local resolver can answer: let the caller fall through to the
  // server personal-context advisor / knowledge base.
  return null;
}
