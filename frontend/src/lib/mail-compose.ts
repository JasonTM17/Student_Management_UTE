/**
 * Pure helpers for the lecturer mail-compose workspace
 * (/dashboard/lecturer/mail). Everything here is side-effect free so the
 * node --test suite can transpile and execute it directly — no React, no
 * axios, no browser globals.
 *
 * Wire contract source of truth: java-services/.../mail/web/MailDtos.java.
 */

export type MailComposeTemplate = 'notice' | 'registration' | 'grade-alert';

export const MAIL_COMPOSE_TEMPLATES: readonly MailComposeTemplate[] = [
  'notice',
  'registration',
  'grade-alert',
];

/** Limits mirrored from the Java mail request DTOs. */
export const MAIL_COMPOSE_LIMITS = {
  common: {
    email: 255,
    recipientName: 255,
    studentName: 255,
    studentId: 255,
    semester: 255,
    courseCode: 255,
    courseName: 255,
  },
  testEmail: {
    recipientName: 255,
    introMessage: 20_000,
  },
  notice: {
    email: 255,
    recipientName: 255,
    category: 255,
    title: 300,
    author: 255,
    content: 20_000,
    highlights: 20,
    highlight: 500,
    actionUrl: 2_048,
    actionText: 500,
  },
  registration: {
    email: 255,
    studentName: 255,
    studentId: 255,
    department: 255,
    semester: 255,
    courses: 30,
    courseCode: 255,
    courseName: 255,
    credits: 30,
    lecturer: 255,
    schedule: 255,
    totalCredits: 60,
  },
  gradeAlert: {
    email: 255,
    studentName: 255,
    studentId: 255,
    semester: 255,
    gpa4: 4,
    gpa10: 10,
    academicStanding: 100,
    conductScore: 100,
    conductRank: 100,
    grades: 60,
    courseCode: 255,
    courseName: 255,
    credits: 30,
    score10: 10,
    scoreLetter: 100,
  },
} as const;

export function isMailComposeTemplate(value: unknown): value is MailComposeTemplate {
  return (
    value === 'notice' || value === 'registration' || value === 'grade-alert'
  );
}

/** Backend preview slugs served by GET /mail/preview/{templateName}. */
const PREVIEW_TEMPLATE_SLUGS: Record<MailComposeTemplate, string> = {
  notice: 'academic-announcement',
  registration: 'course-registration',
  'grade-alert': 'grade-alert',
};

export function mailPreviewSlug(template: MailComposeTemplate): string {
  return PREVIEW_TEMPLATE_SLUGS[template];
}

/**
 * Builds the absolute preview URL the page opens in a new tab. The base must
 * be the same axios base (resolvePublicApiBaseUrl) so same-origin proxies keep
 * carrying the session cookie; template slugs are pre-validated, so the
 * path is injection-safe by construction.
 */
export function mailPreviewUrl(baseUrl: string, template: MailComposeTemplate): string {
  const trimmed = baseUrl.replace(/\/+$/, '');
  return `${trimmed}/mail/preview/${mailPreviewSlug(template)}`;
}

export interface MailCreditRow {
  credits: number | string;
}

/**
 * totalCredits auto-sum for the registration email. Rows are the live form
 * state, so credits may still be a raw string ('' or '3'); non-numeric or
 * negative values simply contribute 0 instead of poisoning the sum.
 */
export function sumCourseCredits(rows: ReadonlyArray<MailCreditRow>): number {
  return rows.reduce((total, row) => {
    const credits = parseNumericInput(row.credits);
    if (credits === null || credits < 0) {
      return total;
    }
    return total + Math.round(credits);
  }, 0);
}

/**
 * Parses a form numeric input. Accepts the Vietnamese decimal comma ("8,5")
 * in addition to the dot form; anything not parseable returns null so callers
 * can treat it as "field not filled correctly" instead of a silent 0.
 */
export function parseNumericInput(value: number | string | null | undefined): number | null {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : null;
  }
  const normalized = (value ?? '').trim().replace(',', '.');
  if (!normalized) {
    return null;
  }
  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : null;
}

/**
 * Coerces a parsed form value into the int the wire contract expects.
 * The backend DTOs type conductScore and credits as integers, so a value
 * like "87,5" must round (to 88) before it reaches the payload — sending the
 * raw fraction is a guaranteed 400. Null (field left empty) falls back.
 */
export function toWireInt(value: number | null | undefined, fallback = 0): number {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return fallback;
  }
  return Math.round(value);
}

/**
 * Whether a course/grade table credits cell is acceptable for the payload.
 * An empty cell is allowed (it sends 0); anything present must parse to a
 * number in the DTO's 0–30 range. Negative, out-of-range, or unparseable input
 * must surface as a validation issue instead of being silently clamped or zeroed.
 */
export function rowCreditsValid(credits: number | string): boolean {
  if (String(credits).trim() === '') {
    return true;
  }
  const parsed = parseNumericInput(credits);
  if (parsed === null || parsed < 0) {
    return false;
  }
  const wireCredits = toWireInt(parsed);
  return wireCredits >= 0 && wireCredits <= MAIL_COMPOSE_LIMITS.registration.credits;
}

export interface MailGradeRow {
  credits: number | string;
  score10: number | string;
}

/**
 * Credit-weighted average on the 10-point scale across the grade rows.
 * Rows without a valid score in [0, 10] are skipped; returns null when no row
 * qualifies so the UI can show "not computable" instead of a fake 0.
 */
export function creditWeightedAverage10(rows: ReadonlyArray<MailGradeRow>): number | null {
  let weightedSum = 0;
  let creditSum = 0;
  for (const row of rows) {
    const score = parseNumericInput(row.score10);
    if (score === null || score < 0 || score > 10) {
      continue;
    }
    const credits = parseNumericInput(row.credits);
    const rowCredits = credits !== null && credits > 0 ? credits : 0;
    weightedSum += score * rowCredits;
    creditSum += rowCredits;
  }
  if (creditSum <= 0) {
    return null;
  }
  return Math.round((weightedSum / creditSum) * 100) / 100;
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function isValidEmail(value: string): boolean {
  return EMAIL_PATTERN.test(value.trim());
}

export type MailFieldIssue =
  | 'emailRequired'
  | 'emailInvalid'
  | 'emailTooLong'
  | 'recipientNameTooLong'
  | 'categoryTooLong'
  | 'titleRequired'
  | 'titleTooLong'
  | 'authorTooLong'
  | 'contentRequired'
  | 'contentTooLong'
  | 'highlightsTooMany'
  | 'highlightTooLong'
  | 'actionUrlTooLong'
  | 'actionTextTooLong'
  | 'introMessageTooLong'
  | 'studentNameRequired'
  | 'studentNameTooLong'
  | 'studentIdRequired'
  | 'studentIdTooLong'
  | 'departmentTooLong'
  | 'semesterTooLong'
  | 'coursesRequired'
  | 'coursesTooMany'
  | 'courseRowsInvalid'
  | 'courseCodeTooLong'
  | 'courseNameTooLong'
  | 'lecturerTooLong'
  | 'scheduleTooLong'
  | 'creditsInvalid'
  | 'totalCreditsInvalid'
  | 'gradesRequired'
  | 'gradesTooMany'
  | 'gradeRowsInvalid'
  | 'gpa4Invalid'
  | 'gpa10Invalid'
  | 'academicStandingTooLong'
  | 'conductInvalid'
  | 'conductRankTooLong'
  | 'scoreLetterTooLong';

export interface MailTestEmailFormInput {
  to?: string;
  recipientName?: string;
  introMessage?: string;
}

/** Length-only checks for the admin-only TestEmailRequest DTO. */
export function testEmailFormIssues(form: MailTestEmailFormInput): MailFieldIssue[] {
  const issues: MailFieldIssue[] = [];
  if (rawTextTooLong(form.to, MAIL_COMPOSE_LIMITS.common.email)) {
    issues.push('emailTooLong');
  }
  if (rawTextTooLong(form.recipientName, MAIL_COMPOSE_LIMITS.testEmail.recipientName)) {
    issues.push('recipientNameTooLong');
  }
  if (rawTextTooLong(form.introMessage, MAIL_COMPOSE_LIMITS.testEmail.introMessage)) {
    issues.push('introMessageTooLong');
  }
  return issues;
}

export interface MailNoticeFormInput {
  to: string;
  title: string;
  content: string;
  recipientName?: string;
  category?: string;
  author?: string;
  highlights?: ReadonlyArray<string>;
  actionUrl?: string;
  actionText?: string;
}

export function noticeFormIssues(form: MailNoticeFormInput): MailFieldIssue[] {
  const issues: MailFieldIssue[] = [];
  const email = form.to.trim();
  if (email.length > MAIL_COMPOSE_LIMITS.notice.email) {
    issues.push('emailTooLong');
  }
  if (!email) {
    issues.push('emailRequired');
  } else if (!isValidEmail(email)) {
    issues.push('emailInvalid');
  }
  if (textTooLong(form.recipientName, MAIL_COMPOSE_LIMITS.notice.recipientName)) {
    issues.push('recipientNameTooLong');
  }
  if (textTooLong(form.category, MAIL_COMPOSE_LIMITS.notice.category)) {
    issues.push('categoryTooLong');
  }
  if (!form.title.trim()) {
    issues.push('titleRequired');
  }
  if (textTooLong(form.title, MAIL_COMPOSE_LIMITS.notice.title)) {
    issues.push('titleTooLong');
  }
  if (textTooLong(form.author, MAIL_COMPOSE_LIMITS.notice.author)) {
    issues.push('authorTooLong');
  }
  if (!form.content.trim()) {
    issues.push('contentRequired');
  }
  if (textTooLong(form.content, MAIL_COMPOSE_LIMITS.notice.content)) {
    issues.push('contentTooLong');
  }
  const highlights = form.highlights ?? [];
  if (highlights.length > MAIL_COMPOSE_LIMITS.notice.highlights) {
    issues.push('highlightsTooMany');
  }
  if (highlights.some((line) => textTooLong(line, MAIL_COMPOSE_LIMITS.notice.highlight))) {
    issues.push('highlightTooLong');
  }
  if (textTooLong(form.actionUrl, MAIL_COMPOSE_LIMITS.notice.actionUrl)) {
    issues.push('actionUrlTooLong');
  }
  if (textTooLong(form.actionText, MAIL_COMPOSE_LIMITS.notice.actionText)) {
    issues.push('actionTextTooLong');
  }
  return [...new Set(issues)];
}

export interface MailRegistrationFormInput {
  to: string;
  studentName: string;
  studentId: string;
  department?: string;
  semester?: string;
  courses: ReadonlyArray<{
    code: string;
    name: string;
    credits?: number | string;
    lecturer?: string;
    schedule?: string;
  }>;
  totalCredits?: number;
}

export function registrationFormIssues(form: MailRegistrationFormInput): MailFieldIssue[] {
  const issues: MailFieldIssue[] = [];
  const email = form.to.trim();
  if (email.length > MAIL_COMPOSE_LIMITS.registration.email) {
    issues.push('emailTooLong');
  }
  if (!email) {
    issues.push('emailRequired');
  } else if (!isValidEmail(email)) {
    issues.push('emailInvalid');
  }
  if (!form.studentName.trim()) {
    issues.push('studentNameRequired');
  }
  if (textTooLong(form.studentName, MAIL_COMPOSE_LIMITS.registration.studentName)) {
    issues.push('studentNameTooLong');
  }
  if (!form.studentId.trim()) {
    issues.push('studentIdRequired');
  }
  if (textTooLong(form.studentId, MAIL_COMPOSE_LIMITS.registration.studentId)) {
    issues.push('studentIdTooLong');
  }
  if (textTooLong(form.department, MAIL_COMPOSE_LIMITS.registration.department)) {
    issues.push('departmentTooLong');
  }
  if (textTooLong(form.semester, MAIL_COMPOSE_LIMITS.registration.semester)) {
    issues.push('semesterTooLong');
  }
  if (form.courses.length > MAIL_COMPOSE_LIMITS.registration.courses) {
    issues.push('coursesTooMany');
  }
  if (form.courses.length === 0) {
    issues.push('coursesRequired');
  }
  if (form.courses.some((course) => !course.code.trim() || !course.name.trim())) {
    issues.push('courseRowsInvalid');
  }
  if (form.courses.some((course) => textTooLong(course.code, MAIL_COMPOSE_LIMITS.registration.courseCode))) {
    issues.push('courseCodeTooLong');
  }
  if (form.courses.some((course) => textTooLong(course.name, MAIL_COMPOSE_LIMITS.registration.courseName))) {
    issues.push('courseNameTooLong');
  }
  if (form.courses.some((course) => textTooLong(course.lecturer, MAIL_COMPOSE_LIMITS.registration.lecturer))) {
    issues.push('lecturerTooLong');
  }
  if (form.courses.some((course) => textTooLong(course.schedule, MAIL_COMPOSE_LIMITS.registration.schedule))) {
    issues.push('scheduleTooLong');
  }
  // Invalid credits must block the send instead of being silently clamped or
  // rounded into a payload the DTO will reject.
  if (form.courses.some((course) => !rowCreditsValid(course.credits ?? ''))) {
    issues.push('creditsInvalid');
  }
  const totalCredits = form.totalCredits ?? sumCourseCredits(
    form.courses.map(({ credits }) => ({ credits: credits ?? '' })),
  );
  if (!Number.isInteger(totalCredits) || totalCredits < 0 || totalCredits > MAIL_COMPOSE_LIMITS.registration.totalCredits) {
    issues.push('totalCreditsInvalid');
  }
  return [...new Set(issues)];
}

export interface MailGradeAlertFormInput {
  to: string;
  studentName: string;
  studentId: string;
  gpa4: string;
  gpa10: string;
  conductScore: string;
  semester?: string;
  academicStanding?: string;
  conductRank?: string;
  grades: ReadonlyArray<{
    courseCode: string;
    courseName: string;
    credits: number | string;
    score10: number | string;
    scoreLetter?: string;
  }>;
}

export function gradeAlertFormIssues(form: MailGradeAlertFormInput): MailFieldIssue[] {
  const found: MailFieldIssue[] = [];
  const email = form.to.trim();
  if (email.length > MAIL_COMPOSE_LIMITS.gradeAlert.email) {
    found.push('emailTooLong');
  }
  if (!email) {
    found.push('emailRequired');
  } else if (!isValidEmail(email)) {
    found.push('emailInvalid');
  }
  if (!form.studentName.trim()) {
    found.push('studentNameRequired');
  }
  if (textTooLong(form.studentName, MAIL_COMPOSE_LIMITS.gradeAlert.studentName)) {
    found.push('studentNameTooLong');
  }
  if (!form.studentId.trim()) {
    found.push('studentIdRequired');
  }
  if (textTooLong(form.studentId, MAIL_COMPOSE_LIMITS.gradeAlert.studentId)) {
    found.push('studentIdTooLong');
  }
  if (textTooLong(form.semester, MAIL_COMPOSE_LIMITS.gradeAlert.semester)) {
    found.push('semesterTooLong');
  }
  // Optional summary metrics: when present they must sit inside the scale.
  const gpa4 = parseNumericInput(form.gpa4);
  if (gpa4 !== null && (gpa4 < 0 || gpa4 > 4)) {
    found.push('gpa4Invalid');
  }
  const gpa10 = parseNumericInput(form.gpa10);
  if (gpa10 !== null && (gpa10 < 0 || gpa10 > 10)) {
    found.push('gpa10Invalid');
  }
  const conduct = parseNumericInput(form.conductScore);
  if (conduct !== null && (conduct < 0 || conduct > 100)) {
    found.push('conductInvalid');
  }
  if (textTooLong(form.academicStanding, MAIL_COMPOSE_LIMITS.gradeAlert.academicStanding)) {
    found.push('academicStandingTooLong');
  }
  if (textTooLong(form.conductRank, MAIL_COMPOSE_LIMITS.gradeAlert.conductRank)) {
    found.push('conductRankTooLong');
  }
  if (form.grades.length > MAIL_COMPOSE_LIMITS.gradeAlert.grades) {
    found.push('gradesTooMany');
  }
  if (form.grades.length === 0) {
    found.push('gradesRequired');
  }
  const rowsValid = form.grades.every(
    (row) =>
      row.courseCode.trim()
      && row.courseName.trim()
      && scoreRowValid(row.score10),
  );
  if (!rowsValid) {
    found.push('gradeRowsInvalid');
  }
  if (form.grades.some((row) => textTooLong(row.courseCode, MAIL_COMPOSE_LIMITS.gradeAlert.courseCode))) {
    found.push('courseCodeTooLong');
  }
  if (form.grades.some((row) => textTooLong(row.courseName, MAIL_COMPOSE_LIMITS.gradeAlert.courseName))) {
    found.push('courseNameTooLong');
  }
  if (form.grades.some((row) => textTooLong(row.scoreLetter, MAIL_COMPOSE_LIMITS.gradeAlert.scoreLetter))) {
    found.push('scoreLetterTooLong');
  }
  // Credits get their own issue so the inline message can point at the
  // credits cell instead of the score cell.
  if (form.grades.some((row) => !rowCreditsValid(row.credits))) {
    found.push('creditsInvalid');
  }
  return [...new Set(found)];
}

function textTooLong(value: string | undefined, max: number): boolean {
  return typeof value === 'string' && value.trim().length > max;
}

function rawTextTooLong(value: string | undefined, max: number): boolean {
  return typeof value === 'string' && value.length > max;
}

function scoreRowValid(score10: number | string): boolean {
  const score = parseNumericInput(score10);
  return score !== null && score >= 0 && score <= 10;
}

export type MailErrorKind =
  | 'rateLimit'
  | 'deliveryFailed'
  | 'outOfScope'
  | 'validation'
  | 'network'
  | 'unknown';

interface MailErrorLike {
  response?: {
    status?: unknown;
    data?: unknown;
  };
  code?: unknown;
}

/**
 * Maps a dispatch failure onto a stable copy key.
 * - 429 or RATE_LIMIT_EXCEEDED: the 5-emails/hour quota is exhausted.
 * - 403 MAIL_RECIPIENT_OUT_OF_SCOPE: a lecturer may only mail students of
 *   their own sections.
 * - 502 or MAIL_DELIVERY_FAILED: SMTP gateway rejected the message.
 * - 400: bean-validation rejected a field.
 * - no response: the network call itself failed.
 */
export function classifyMailError(error: unknown): MailErrorKind {
  if (!isRecord(error)) {
    return 'unknown';
  }
  const shaped = error as MailErrorLike;
  if (!shaped.response) {
    return 'network';
  }
  const status = typeof shaped.response.status === 'number' ? shaped.response.status : undefined;
  const data = shaped.response.data;
  const backendCode =
    isRecord(data) && typeof data.code === 'string' ? data.code : undefined;

  if (status === 429 || backendCode === 'RATE_LIMIT_EXCEEDED') {
    return 'rateLimit';
  }
  if (backendCode === 'MAIL_RECIPIENT_OUT_OF_SCOPE' || backendCode === 'MAIL_SCOPE_FORBIDDEN') {
    return 'outOfScope';
  }
  if (status === 502 || backendCode === 'MAIL_DELIVERY_FAILED') {
    return 'deliveryFailed';
  }
  if (status === 400 || status === 422) {
    return 'validation';
  }
  if (typeof status === 'number' && status >= 500) {
    return 'deliveryFailed';
  }
  return 'unknown';
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/**
 * Trims optional text fields before they hit the wire so the backend never
 * receives whitespace-only optionals (Spring would happily render them).
 */
export function optionalText(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}

/**
 * Highlights chips: raw textarea lines become the string[] the DTO expects,
 * dropping blank lines and duplicates the author may have re-typed.
 */
export function parseHighlightLines(raw: string): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const line of raw.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || seen.has(trimmed)) {
      continue;
    }
    seen.add(trimmed);
    result.push(trimmed);
  }
  return result;
}
