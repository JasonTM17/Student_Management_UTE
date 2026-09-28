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
 * non-negative number. Negative or unparseable input must surface as a
 * validation issue instead of being silently clamped or zeroed.
 */
export function rowCreditsValid(credits: number | string): boolean {
  if (String(credits).trim() === '') {
    return true;
  }
  const parsed = parseNumericInput(credits);
  return parsed !== null && parsed >= 0;
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
  | 'titleRequired'
  | 'contentRequired'
  | 'studentNameRequired'
  | 'studentIdRequired'
  | 'coursesRequired'
  | 'courseRowsInvalid'
  | 'creditsInvalid'
  | 'gradesRequired'
  | 'gradeRowsInvalid'
  | 'gpaInvalid'
  | 'conductInvalid';

export interface MailNoticeFormInput {
  to: string;
  title: string;
  content: string;
}

export function noticeFormIssues(form: MailNoticeFormInput): MailFieldIssue[] {
  const issues: MailFieldIssue[] = [];
  const email = form.to.trim();
  if (!email) {
    issues.push('emailRequired');
  } else if (!isValidEmail(email)) {
    issues.push('emailInvalid');
  }
  if (!form.title.trim()) {
    issues.push('titleRequired');
  }
  if (!form.content.trim()) {
    issues.push('contentRequired');
  }
  return issues;
}

export interface MailRegistrationFormInput {
  to: string;
  studentName: string;
  studentId: string;
  courses: ReadonlyArray<{ code: string; name: string; credits?: number | string }>;
}

export function registrationFormIssues(form: MailRegistrationFormInput): MailFieldIssue[] {
  const issues: MailFieldIssue[] = [];
  const email = form.to.trim();
  if (!email) {
    issues.push('emailRequired');
  } else if (!isValidEmail(email)) {
    issues.push('emailInvalid');
  }
  if (!form.studentName.trim()) {
    issues.push('studentNameRequired');
  }
  if (!form.studentId.trim()) {
    issues.push('studentIdRequired');
  }
  if (form.courses.length === 0) {
    issues.push('coursesRequired');
  } else {
    if (form.courses.some((course) => !course.code.trim() || !course.name.trim())) {
      issues.push('courseRowsInvalid');
    }
    // Negative (or junk) credits must block the send with an inline message —
    // never a silent clamp to 0, which used to fabricate a course load.
    if (form.courses.some((course) => !rowCreditsValid(course.credits ?? ''))) {
      issues.push('creditsInvalid');
    }
  }
  return issues;
}

export interface MailGradeAlertFormInput {
  to: string;
  studentName: string;
  studentId: string;
  gpa4: string;
  gpa10: string;
  conductScore: string;
  grades: ReadonlyArray<{ courseCode: string; courseName: string; credits: number | string; score10: number | string }>;
}

export function gradeAlertFormIssues(form: MailGradeAlertFormInput): MailFieldIssue[] {
  const found: MailFieldIssue[] = [];
  const email = form.to.trim();
  if (!email) {
    found.push('emailRequired');
  } else if (!isValidEmail(email)) {
    found.push('emailInvalid');
  }
  if (!form.studentName.trim()) {
    found.push('studentNameRequired');
  }
  if (!form.studentId.trim()) {
    found.push('studentIdRequired');
  }
  // Optional summary metrics: when present they must sit inside the scale.
  const gpa4 = parseNumericInput(form.gpa4);
  if (gpa4 !== null && (gpa4 < 0 || gpa4 > 4)) {
    found.push('gpaInvalid');
  }
  const gpa10 = parseNumericInput(form.gpa10);
  if (gpa10 !== null && (gpa10 < 0 || gpa10 > 10)) {
    found.push('gpaInvalid');
  }
  const conduct = parseNumericInput(form.conductScore);
  if (conduct !== null && (conduct < 0 || conduct > 100)) {
    found.push('conductInvalid');
  }
  if (form.grades.length === 0) {
    found.push('gradesRequired');
  } else {
    const rowsValid = form.grades.every(
      (row) =>
        row.courseCode.trim()
        && row.courseName.trim()
        && scoreRowValid(row.score10),
    );
    if (!rowsValid) {
      found.push('gradeRowsInvalid');
    }
    // Credits get their own issue so the inline message can point at the
    // credits cell instead of the score cell: negative or junk input blocks
    // the send rather than being silently clamped into the payload.
    if (form.grades.some((row) => !rowCreditsValid(row.credits))) {
      found.push('creditsInvalid');
    }
  }
  return [...new Set(found)];
}
function scoreRowValid(score10: number | string): boolean {
  const score = parseNumericInput(score10);
  return score !== null && score >= 0 && score <= 10;
}

export type MailErrorKind =
  | 'rateLimit'
  | 'deliveryFailed'
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
