'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  BadgeCheck,
  Check,
  Eye,
  Info,
  Mail,
  Plus,
  Send,
  Trash2,
} from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import {
  API_BASE_URL,
  mailApi,
  type MailDispatchResponse,
} from '@/lib/api';
import {
  classifyMailError,
  creditWeightedAverage10,
  gradeAlertFormIssues,
  mailPreviewUrl,
  noticeFormIssues,
  optionalText,
  parseHighlightLines,
  parseNumericInput,
  registrationFormIssues,
  rowCreditsValid,
  sumCourseCredits,
  toWireInt,
  type MailComposeTemplate,
  type MailErrorKind,
  type MailFieldIssue,
} from '@/lib/mail-compose';
import { calculateGrade } from '@/lib/grade-export';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Modal } from '@/components/ui/modal';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LoadingState } from '@/components/ui/state-block';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';

interface NoticeForm {
  to: string;
  recipientName: string;
  category: string;
  title: string;
  author: string;
  content: string;
  actionUrl: string;
  actionText: string;
}

interface CourseRow {
  code: string;
  name: string;
  credits: string;
  lecturer: string;
  schedule: string;
}

interface GradeRow {
  courseCode: string;
  courseName: string;
  credits: string;
  score10: string;
  scoreLetter: string;
}

interface RegistrationForm {
  to: string;
  studentName: string;
  studentId: string;
  department: string;
  semester: string;
}

interface GradeAlertForm {
  to: string;
  studentName: string;
  studentId: string;
  semester: string;
  gpa4: string;
  gpa10: string;
  academicStanding: string;
  conductScore: string;
  conductRank: string;
}

interface SentRecord {
  recipient: string;
  template: string;
  dispatchedAt: string;
}

type MailCopy = ReturnType<typeof useI18n>['messages']['mailCompose'];

const EMPTY_COURSE_ROW: CourseRow = {
  code: '',
  name: '',
  credits: '',
  lecturer: '',
  schedule: '',
};

const EMPTY_GRADE_ROW: GradeRow = {
  courseCode: '',
  courseName: '',
  credits: '',
  score10: '',
  scoreLetter: '',
};

const NOTICE_CATEGORIES = [
  'academic',
  'exam',
  'scholarship',
  'event',
  'other',
] as const;

const ACADEMIC_STANDINGS = [
  'excellent',
  'veryGood',
  'good',
  'average',
  'weak',
] as const;

const INVALID_BORDER = 'border-destructive focus-visible:ring-destructive';

function emptyNoticeForm(): NoticeForm {
  return {
    to: '',
    recipientName: '',
    category: 'academic',
    title: '',
    author: '',
    content: '',
    actionUrl: '',
    actionText: '',
  };
}

function emptyRegistrationForm(): RegistrationForm {
  return { to: '', studentName: '', studentId: '', department: '', semester: '' };
}

function emptyGradeAlertForm(): GradeAlertForm {
  return {
    to: '',
    studentName: '',
    studentId: '',
    semester: '',
    gpa4: '',
    gpa10: '',
    academicStanding: '',
    conductScore: '',
    conductRank: '',
  };
}

function scoreRowInRange(score10: string): boolean {
  const score = parseNumericInput(score10);
  return score !== null && score >= 0 && score <= 10;
}

function Field({
  label,
  htmlFor,
  required,
  hideLabel,
  compact,
  children,
}: {
  label: string;
  htmlFor: string;
  required?: boolean;
  hideLabel?: boolean;
  compact?: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="min-w-0">
      <label
        htmlFor={htmlFor}
        className={cn(
          'block text-sm font-medium text-foreground',
          compact ? 'mb-1' : 'mb-1.5',
          hideLabel && 'sr-only',
        )}
      >
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
      </label>
      {children}
    </div>
  );
}

function RailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <span className="shrink-0 text-xs font-medium text-muted-foreground">{label}</span>
      <span
        className="min-w-0 truncate text-right text-sm font-semibold text-foreground"
        title={typeof children === 'string' ? children : undefined}
      >
        {children}
      </span>
    </div>
  );
}

function StudentFields({
  copy,
  form,
  onChange,
  issueText,
}: {
  copy: MailCopy;
  form: { to: string; studentName: string; studentId: string };
  onChange: (patch: Partial<{ to: string; studentName: string; studentId: string }>) => void;
  issueText: (issue: MailFieldIssue) => string | undefined;
}) {
  return (
    <div className="space-y-2">
      <div className="grid gap-4 sm:grid-cols-3">
      <Field label={copy.fields.email} required htmlFor="student-email">
        <Input
          id="student-email"
          type="email"
          autoComplete="email"
          inputMode="email"
          placeholder={copy.fields.emailPlaceholder}
          value={form.to}
          error={issueText('emailRequired') || issueText('emailInvalid')}
          onChange={(event) => onChange({ to: event.target.value })}
        />
      </Field>
      <Field label={copy.fields.studentName} required htmlFor="student-name">
        <Input
          id="student-name"
          placeholder={copy.fields.recipientNamePlaceholder}
          value={form.studentName}
          error={issueText('studentNameRequired')}
          onChange={(event) => onChange({ studentName: event.target.value })}
        />
      </Field>
      <Field label={copy.fields.studentId} required htmlFor="student-id">
        <Input
          id="student-id"
          inputMode="numeric"
          placeholder="22110001"
          value={form.studentId}
          error={issueText('studentIdRequired')}
          onChange={(event) => onChange({ studentId: event.target.value })}
        />
      </Field>
      </div>
      <p className="text-xs text-muted-foreground">{copy.fields.recipientScopeHint}</p>
    </div>
  );
}

export default function LecturerMailComposePage() {
  const { user, hasAccess, isLoading: authLoading, isForbidden } = useRequireAuth([
    'LECTURER',
  ]);
  const { messages, formatDateTime, formatNumber } = useI18n();
  const copy = messages.mailCompose;

  const [template, setTemplate] = useState<MailComposeTemplate>('notice');
  const [notice, setNotice] = useState<NoticeForm>(emptyNoticeForm);
  const [registration, setRegistration] = useState<RegistrationForm>(emptyRegistrationForm);
  const [courses, setCourses] = useState<CourseRow[]>([]);
  const [gradeAlert, setGradeAlert] = useState<GradeAlertForm>(emptyGradeAlertForm);
  const [grades, setGrades] = useState<GradeRow[]>([]);
  const [highlights, setHighlights] = useState<string[]>([]);
  const [highlightDraft, setHighlightDraft] = useState('');

  const [attemptedSend, setAttemptedSend] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [errorKind, setErrorKind] = useState<MailErrorKind | null>(null);
  const [sentThisSession, setSentThisSession] = useState(0);
  const [sent, setSent] = useState<SentRecord | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const successRef = useRef<HTMLDivElement>(null);
  const { confirm, confirmationDialog } = useConfirmationDialog();

  // The messages key is camelCase ('gradeAlert') while the template id and
  // the wire template slug stay kebab ('grade-alert').
  const templateTitle = useCallback(
    (value: MailComposeTemplate) =>
      copy.templates[value === 'grade-alert' ? 'gradeAlert' : value].title,
    [copy.templates],
  );

  const issues = useMemo<MailFieldIssue[]>(() => {
    if (template === 'notice') {
      return noticeFormIssues({ to: notice.to, title: notice.title, content: notice.content });
    }
    if (template === 'registration') {
      return registrationFormIssues({
        to: registration.to,
        studentName: registration.studentName,
        studentId: registration.studentId,
        courses,
      });
    }
    return gradeAlertFormIssues({
      to: gradeAlert.to,
      studentName: gradeAlert.studentName,
      studentId: gradeAlert.studentId,
      gpa4: gradeAlert.gpa4,
      gpa10: gradeAlert.gpa10,
      conductScore: gradeAlert.conductScore,
      grades,
    });
  }, [template, notice, registration, courses, gradeAlert, grades]);

  // Field-level error text stays hidden until the first send attempt, so the
  // author is not yelled at while still typing.
  const issueText = useCallback(
    (issue: MailFieldIssue) =>
      attemptedSend && issues.includes(issue) ? copy.validation[issue] : undefined,
    [attemptedSend, issues, copy.validation],
  );

  const totalCredits = useMemo(() => sumCourseCredits(courses), [courses]);
  const weightedAverage = useMemo(() => creditWeightedAverage10(grades), [grades]);

  // Draft preview rows mirror the payload the send endpoint receives, so the
  // modal shows what will actually merge into the email — not a canned sample.
  const previewRows = useMemo<Array<[string, string]>>(() => {
    const fields = copy.fields;
    const empty = copy.previewEmptyField;
    const value = (raw: string | undefined | null) => {
      const trimmed = (raw ?? '').trim();
      return trimmed === '' ? empty : trimmed;
    };
    if (template === 'notice') {
      return [
        [fields.email, value(notice.to)],
        [fields.recipientName, value(notice.recipientName)],
        [fields.category, value(copy.categories[notice.category as (typeof NOTICE_CATEGORIES)[number]])],
        [fields.title, value(notice.title)],
        [fields.author, value(notice.author)],
        [fields.content, value(notice.content)],
        [fields.highlights, highlights.length > 0 ? highlights.join(' • ') : empty],
        [fields.actionUrl, value(notice.actionUrl)],
        [fields.actionText, value(notice.actionText)],
      ];
    }
    if (template === 'registration') {
      return [
        [fields.email, value(registration.to)],
        [fields.studentName, value(registration.studentName)],
        [fields.studentId, value(registration.studentId)],
        [fields.department, value(registration.department)],
        [fields.semester, value(registration.semester)],
      ];
    }
    return [
      [fields.email, value(gradeAlert.to)],
      [fields.studentName, value(gradeAlert.studentName)],
      [fields.studentId, value(gradeAlert.studentId)],
      [fields.semester, value(gradeAlert.semester)],
      [fields.gpa4, value(gradeAlert.gpa4)],
      [fields.gpa10, value(gradeAlert.gpa10)],
      [fields.academicStanding, value(gradeAlert.academicStanding)],
      [fields.conductScore, value(gradeAlert.conductScore)],
      [fields.conductRank, value(gradeAlert.conductRank)],
    ];
  }, [copy, template, notice, registration, gradeAlert, highlights]);

  // The letter grade fills itself from the 10-scale score; a manually typed
  // value in the row wins so exceptional cases stay expressible.
  const letterFor = useCallback((row: GradeRow): string => {
    if (row.scoreLetter.trim()) {
      return row.scoreLetter.trim();
    }
    const score = parseNumericInput(row.score10);
    return score !== null && score >= 0 && score <= 10 ? calculateGrade(score) : '';
  }, []);

  const previewHref = mailPreviewUrl(API_BASE_URL, template);

  useEffect(() => {
    if (sent && successRef.current) {
      successRef.current.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }
  }, [sent]);

  const switchTemplate = (next: MailComposeTemplate) => {
    if (next === template) {
      return;
    }
    setTemplate(next);
    setAttemptedSend(false);
    setErrorKind(null);
  };

  const addHighlight = () => {
    const chips = parseHighlightLines(highlightDraft);
    if (chips.length === 0) {
      return;
    }
    setHighlights((current) => {
      const seen = new Set(current);
      return [...current, ...chips.filter((chip) => !seen.has(chip))];
    });
    setHighlightDraft('');
  };

  const dispatchForTemplate = async (): Promise<MailDispatchResponse> => {
    if (template === 'notice') {
      return mailApi.sendNotice({
        to: notice.to.trim(),
        recipientName: optionalText(notice.recipientName),
        category: optionalText(copy.categories[notice.category as (typeof NOTICE_CATEGORIES)[number]]),
        title: notice.title.trim(),
        author: optionalText(notice.author),
        content: notice.content.trim(),
        highlights: highlights.length > 0 ? highlights : undefined,
        actionUrl: optionalText(notice.actionUrl),
        actionText: optionalText(notice.actionText),
      });
    }
    if (template === 'registration') {
      return mailApi.sendRegistration({
        to: registration.to.trim(),
        studentName: registration.studentName.trim(),
        studentId: registration.studentId.trim(),
        department: optionalText(registration.department),
        semester: optionalText(registration.semester),
        courses: courses.map((row) => ({
          code: row.code.trim(),
          name: row.name.trim(),
          // Integer wire contract: "2,5" rounds instead of failing bean
          // validation with a fractional JSON number.
          credits: toWireInt(parseNumericInput(row.credits)),
          lecturer: optionalText(row.lecturer),
          schedule: optionalText(row.schedule),
        })),
        totalCredits,
      });
    }
    return mailApi.sendGradeAlert({
      to: gradeAlert.to.trim(),
      studentName: gradeAlert.studentName.trim(),
      studentId: gradeAlert.studentId.trim(),
      semester: optionalText(gradeAlert.semester),
      gpa4: parseNumericInput(gradeAlert.gpa4) ?? 0,
      gpa10: parseNumericInput(gradeAlert.gpa10) ?? 0,
      academicStanding: optionalText(
        gradeAlert.academicStanding
          ? copy.standings[gradeAlert.academicStanding as (typeof ACADEMIC_STANDINGS)[number]]
          : '',
      ),
      // The backend types conductScore as an int: "87,5" must leave as 88,
      // not 87.5 (which bean validation rejects with a 400).
      conductScore: toWireInt(parseNumericInput(gradeAlert.conductScore)),
      conductRank: optionalText(gradeAlert.conductRank),
      grades: grades.map((row) => ({
        courseCode: row.courseCode.trim(),
        courseName: row.courseName.trim(),
        credits: toWireInt(parseNumericInput(row.credits)),
        score10: parseNumericInput(row.score10) ?? 0,
        scoreLetter: letterFor(row) || undefined,
      })),
    });
  };

  const handleSend = async () => {
    setAttemptedSend(true);
    if (issues.length > 0 || isSending) {
      return;
    }
    const recipient = (
      template === 'notice' ? notice.to : template === 'registration' ? registration.studentId : gradeAlert.studentId
    ).trim();
    const confirmed = await confirm({
      title: copy.confirmTitle,
      message: copy.confirmMessage
        .replace('{template}', templateTitle(template))
        .replace('{recipient}', recipient),
      confirmText: copy.send,
      cancelText: messages.common.actions.cancel,
      warning: copy.quotaNote,
    });
    if (!confirmed) {
      return;
    }

    setIsSending(true);
    setErrorKind(null);
    try {
      const response = await dispatchForTemplate();
      setSent({
        recipient: response.recipient,
        template: templateTitle(template),
        dispatchedAt: response.dispatchedAt,
      });
      setSentThisSession((current) => current + 1);
      setAttemptedSend(false);
      // A dispatched draft is done — clear the form so a second send never
      // silently reuses the previous recipient's fields.
      if (template === 'notice') {
        setNotice(emptyNoticeForm);
        setHighlights([]);
        setHighlightDraft('');
      } else if (template === 'registration') {
        setRegistration(emptyRegistrationForm);
        setCourses([]);
      } else {
        setGradeAlert(emptyGradeAlertForm);
        setGrades([]);
      }
    } catch (error) {
      setErrorKind(classifyMailError(error));
    } finally {
      setIsSending(false);
    }
  };

  if (authLoading) {
    return <LoadingState label={messages.common.states.loading} />;
  }

  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  const quotaLeft = Math.max(0, 5 - sentThisSession);
  const readyToSend = issues.length === 0;
  const statusLabel = readyToSend ? copy.ready : copy.incomplete;

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        tabLabel={messages.dashboardShell.menu.mailCompose}
        description={copy.description}
        actions={
          <div className="flex flex-col items-start gap-1.5 sm:items-end">
            <span className="inline-flex items-center gap-1.5 rounded-md border border-[var(--portal-brand-gold)]/40 bg-[var(--portal-brand-gold)]/10 px-3 py-1 text-xs font-semibold text-foreground">
              <Send className="h-3.5 w-3.5 text-[var(--portal-brand-gold)]" aria-hidden="true" />
              {copy.quotaBadge.replace('{count}', formatNumber(sentThisSession))}
            </span>
            <span className="text-xs text-muted-foreground">
              {copy.quotaNote} · {copy.quotaLeft.replace('{count}', formatNumber(quotaLeft))}
            </span>
          </div>
        }
      />

      {/* Template picker: three cards, the selected one carries the gold ring
          and check mark like the mockup. */}
      <section aria-label={copy.templatesLabel}>
        <h2 className="portal-menu-label mb-2">{copy.templatesLabel}</h2>
        <div className="grid gap-3 md:grid-cols-3">
          {(['notice', 'registration', 'gradeAlert'] as const).map((cardKey) => {
            const cardTemplate: MailComposeTemplate = cardKey === 'gradeAlert' ? 'grade-alert' : cardKey;
            const selected = template === cardTemplate;
            const meta = copy.templates[cardKey];
            return (
              <button
                key={cardTemplate}
                type="button"
                onClick={() => switchTemplate(cardTemplate)}
                aria-pressed={selected}
                className={cn(
                  'relative rounded-lg border p-4 text-left transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60',
                  selected
                    ? 'border-[var(--portal-brand-gold)] bg-[var(--portal-brand-gold)]/10 shadow-xs'
                    : 'border-border/70 bg-card hover:border-primary/50 hover:bg-secondary/40',
                )}
                disabled={isSending}
              >
                <span className="flex items-center justify-between gap-2">
                  <span className="flex items-center gap-2 text-sm font-semibold text-foreground">
                    <Mail
                      className={cn('h-4 w-4 shrink-0', selected ? 'text-[var(--portal-brand-gold)]' : 'text-muted-foreground')}
                      aria-hidden="true"
                    />
                    {meta.title}
                  </span>
                  {selected ? (
                    <span
                      className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-[var(--portal-brand-gold)] text-[var(--portal-brand-gold-ink)]"
                      aria-hidden="true"
                    >
                      <Check className="h-3.5 w-3.5" />
                    </span>
                  ) : null}
                </span>
                <span className="mt-1.5 block text-xs leading-5 text-muted-foreground">{meta.description}</span>
              </button>
            );
          })}
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* Dynamic form, one variant per template. The fieldset disables every
            control at once while a dispatch is in flight. */}
        <Card variant="elevated" className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-lg">{copy.formTitle}</CardTitle>
          </CardHeader>
          <CardContent>
            <fieldset disabled={isSending} className="space-y-4">
              {template === 'notice' ? (
                <>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field label={copy.fields.email} required htmlFor="mail-to">
                      <Input
                        id="mail-to"
                        type="email"
                        autoComplete="email"
                        inputMode="email"
                        placeholder={copy.fields.emailPlaceholder}
                        value={notice.to}
                        error={issueText('emailRequired') || issueText('emailInvalid')}
                        onChange={(event) => setNotice((current) => ({ ...current, to: event.target.value }))}
                      />
                    </Field>
                    <Field label={copy.fields.recipientName} htmlFor="mail-recipient-name">
                      <Input
                        id="mail-recipient-name"
                        placeholder={copy.fields.recipientNamePlaceholder}
                        value={notice.recipientName}
                        onChange={(event) => setNotice((current) => ({ ...current, recipientName: event.target.value }))}
                      />
                    </Field>
                  </div>
                  <p className="text-xs text-muted-foreground">{copy.fields.recipientScopeHint}</p>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field label={copy.fields.category} htmlFor="mail-category">
                      <Select
                        id="mail-category"
                        value={notice.category}
                        onChange={(event) => setNotice((current) => ({ ...current, category: event.target.value }))}
                        options={NOTICE_CATEGORIES.map((key) => ({ value: key, label: copy.categories[key] }))}
                      />
                    </Field>
                    <Field label={copy.fields.author} htmlFor="mail-author">
                      <Input
                        id="mail-author"
                        placeholder={copy.fields.authorPlaceholder}
                        value={notice.author}
                        onChange={(event) => setNotice((current) => ({ ...current, author: event.target.value }))}
                      />
                    </Field>
                  </div>
                  <Field label={copy.fields.title} required htmlFor="mail-title">
                    <Input
                      id="mail-title"
                      placeholder={copy.fields.titlePlaceholder}
                      value={notice.title}
                      error={issueText('titleRequired')}
                      onChange={(event) => setNotice((current) => ({ ...current, title: event.target.value }))}
                    />
                  </Field>
                  <Field label={copy.fields.content} required htmlFor="mail-content">
                    <Textarea
                      id="mail-content"
                      placeholder={copy.fields.contentPlaceholder}
                      value={notice.content}
                      error={issueText('contentRequired')}
                      hint={copy.fields.charCount.replace('{count}', formatNumber(notice.content.length))}
                      onChange={(event) => setNotice((current) => ({ ...current, content: event.target.value }))}
                    />
                  </Field>
                  <Field label={copy.fields.highlights} htmlFor="mail-highlight">
                    <div className="flex gap-2">
                      <Input
                        id="mail-highlight"
                        placeholder={copy.fields.highlightPlaceholder}
                        value={highlightDraft}
                        onChange={(event) => setHighlightDraft(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            event.preventDefault();
                            addHighlight();
                          }
                        }}
                      />
                      <Button type="button" variant="outline" onClick={addHighlight} className="shrink-0">
                        <Plus className="mr-1.5 h-4 w-4" aria-hidden="true" />
                        {copy.fields.addHighlight}
                      </Button>
                    </div>
                    {highlights.length > 0 ? (
                      <ul className="mt-2 flex flex-wrap gap-2" aria-label={copy.fields.highlights}>
                        {highlights.map((chip) => (
                          <li key={chip}>
                            <span className="inline-flex max-w-full items-center gap-1.5 rounded-md border border-border/70 bg-secondary/60 px-2.5 py-1 text-xs font-medium text-foreground">
                              <span className="max-w-72 truncate" title={chip}>{chip}</span>
                              <button
                                type="button"
                                onClick={() => setHighlights((current) => current.filter((item) => item !== chip))}
                                aria-label={`${copy.fields.removeRow}: ${chip}`}
                                className="rounded text-muted-foreground transition-colors hover:text-destructive focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                              >
                                <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                              </button>
                            </span>
                          </li>
                        ))}
                      </ul>
                    ) : null}
                  </Field>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field label={copy.fields.actionUrl} htmlFor="mail-action-url">
                      <Input
                        id="mail-action-url"
                        type="url"
                        inputMode="url"
                        placeholder={copy.fields.actionUrlPlaceholder}
                        value={notice.actionUrl}
                        onChange={(event) => setNotice((current) => ({ ...current, actionUrl: event.target.value }))}
                      />
                    </Field>
                    <Field label={copy.fields.actionText} htmlFor="mail-action-text">
                      <Input
                        id="mail-action-text"
                        placeholder={copy.fields.actionTextPlaceholder}
                        value={notice.actionText}
                        onChange={(event) => setNotice((current) => ({ ...current, actionText: event.target.value }))}
                      />
                    </Field>
                  </div>
                </>
              ) : null}

              {template === 'registration' ? (
                <>
                  <StudentFields
                    copy={copy}
                    form={registration}
                    onChange={(patch) => setRegistration((current) => ({ ...current, ...patch }))}
                    issueText={issueText}
                  />
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field label={copy.fields.department} htmlFor="mail-department">
                      <Input
                        id="mail-department"
                        placeholder={copy.fields.departmentPlaceholder}
                        value={registration.department}
                        onChange={(event) => setRegistration((current) => ({ ...current, department: event.target.value }))}
                      />
                    </Field>
                    <Field label={copy.fields.semester} htmlFor="mail-semester">
                      <Input
                        id="mail-semester"
                        placeholder={copy.fields.semesterPlaceholder}
                        value={registration.semester}
                        onChange={(event) => setRegistration((current) => ({ ...current, semester: event.target.value }))}
                      />
                    </Field>
                  </div>
                  <section aria-label={copy.fields.courses} className="space-y-2">
                    <div className="flex items-center justify-between gap-2">
                      <h3 className="text-sm font-semibold text-foreground">{copy.fields.courses}</h3>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => setCourses((current) => [...current, { ...EMPTY_COURSE_ROW }])}
                      >
                        <Plus className="mr-1.5 h-4 w-4" aria-hidden="true" />
                        {copy.fields.addCourse}
                      </Button>
                    </div>
                    {courses.length === 0 ? (
                      <p className={cn('text-sm', issueText('coursesRequired') ? 'text-destructive' : 'text-muted-foreground')}>
                        {issueText('coursesRequired') || copy.coursesEmpty}
                      </p>
                    ) : (
                      <div className="space-y-3">
                        {courses.map((row, index) => {
                          const rowInvalid =
                            attemptedSend && (!row.code.trim() || !row.name.trim());
                          return (
                            <div key={index} className="rounded-lg border border-border/70 bg-secondary/30 p-3">
                              <div className="grid gap-2 sm:grid-cols-12">
                                <div className="sm:col-span-3">
                                  <Field label={copy.fields.code} htmlFor={`course-${index}-code`} hideLabel compact>
                                    <Input
                                      id={`course-${index}-code`}
                                      className={cn(rowInvalid && INVALID_BORDER)}
                                      aria-invalid={rowInvalid ? true : undefined}
                                      placeholder={copy.fields.code}
                                      value={row.code}
                                      onChange={(event) => setCourses((current) => current.map((item, i) => (i === index ? { ...item, code: event.target.value } : item)))}
                                    />
                                  </Field>
                                </div>
                                <div className="sm:col-span-5">
                                  <Field label={copy.fields.courseName} htmlFor={`course-${index}-name`} hideLabel compact>
                                    <Input
                                      id={`course-${index}-name`}
                                      className={cn(rowInvalid && INVALID_BORDER)}
                                      aria-invalid={rowInvalid ? true : undefined}
                                      placeholder={copy.fields.courseName}
                                      value={row.name}
                                      onChange={(event) => setCourses((current) => current.map((item, i) => (i === index ? { ...item, name: event.target.value } : item)))}
                                    />
                                  </Field>
                                </div>
                                <div className="sm:col-span-2">
                                  <Field label={copy.fields.credits} htmlFor={`course-${index}-credits`} hideLabel compact>
                                    <Input
                                      id={`course-${index}-credits`}
                                      inputMode="numeric"
                                      placeholder={copy.fields.credits}
                                      value={row.credits}
                                      className={cn(attemptedSend && !rowCreditsValid(row.credits) && INVALID_BORDER)}
                                      aria-invalid={attemptedSend && !rowCreditsValid(row.credits) ? true : undefined}
                                      error={issueText('creditsInvalid')}
                                      onChange={(event) => setCourses((current) => current.map((item, i) => (i === index ? { ...item, credits: event.target.value } : item)))}
                                    />
                                  </Field>
                                </div>
                                <div className="flex items-end justify-end sm:col-span-2">
                                  <Button
                                    type="button"
                                    variant="ghost"
                                    size="icon"
                                    aria-label={copy.fields.removeRow}
                                    onClick={() => setCourses((current) => current.filter((_, i) => i !== index))}
                                  >
                                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                                  </Button>
                                </div>
                              </div>
                              <div className="mt-2 grid gap-2 sm:grid-cols-2">
                                <Field label={copy.fields.lecturer} htmlFor={`course-${index}-lecturer`} hideLabel compact>
                                  <Input
                                    id={`course-${index}-lecturer`}
                                    placeholder={copy.fields.lecturer}
                                    value={row.lecturer}
                                    onChange={(event) => setCourses((current) => current.map((item, i) => (i === index ? { ...item, lecturer: event.target.value } : item)))}
                                  />
                                </Field>
                                <Field label={copy.fields.schedule} htmlFor={`course-${index}-schedule`} hideLabel compact>
                                  <Input
                                    id={`course-${index}-schedule`}
                                    placeholder={copy.fields.schedule}
                                    value={row.schedule}
                                    onChange={(event) => setCourses((current) => current.map((item, i) => (i === index ? { ...item, schedule: event.target.value } : item)))}
                                  />
                                </Field>
                              </div>
                            </div>
                          );
                        })}
                        {issueText('courseRowsInvalid') ? (
                          <p className="text-sm text-destructive" role="alert">{copy.validation.courseRowsInvalid}</p>
                        ) : null}
                        <div className="flex items-center justify-between rounded-lg border border-border/70 bg-secondary/40 px-3 py-2 text-sm">
                          <span className="font-medium text-muted-foreground">
                            {copy.coursesSummary.replace('{count}', formatNumber(courses.length))}
                          </span>
                          <span className="font-semibold text-foreground">
                            {copy.fields.credits}: {formatNumber(totalCredits)}
                          </span>
                        </div>
                      </div>
                    )}
                  </section>
                </>
              ) : null}

              {template === 'grade-alert' ? (
                <>
                  <StudentFields
                    copy={copy}
                    form={gradeAlert}
                    onChange={(patch) => setGradeAlert((current) => ({ ...current, ...patch }))}
                    issueText={issueText}
                  />
                  <Field label={copy.fields.semester} htmlFor="mail-grade-semester">
                    <Input
                      id="mail-grade-semester"
                      placeholder={copy.fields.semesterPlaceholder}
                      value={gradeAlert.semester}
                      onChange={(event) => setGradeAlert((current) => ({ ...current, semester: event.target.value }))}
                    />
                  </Field>
                  <div className="grid gap-4 sm:grid-cols-3">
                    <Field label={copy.fields.gpa4} htmlFor="mail-gpa4">
                      <Input
                        id="mail-gpa4"
                        inputMode="decimal"
                        placeholder="3.82"
                        value={gradeAlert.gpa4}
                        error={issueText('gpaInvalid')}
                        onChange={(event) => setGradeAlert((current) => ({ ...current, gpa4: event.target.value }))}
                      />
                    </Field>
                    <Field label={copy.fields.gpa10} htmlFor="mail-gpa10">
                      <Input
                        id="mail-gpa10"
                        inputMode="decimal"
                        placeholder="9.15"
                        value={gradeAlert.gpa10}
                        error={issueText('gpaInvalid')}
                        onChange={(event) => setGradeAlert((current) => ({ ...current, gpa10: event.target.value }))}
                      />
                    </Field>
                    <Field label={copy.fields.academicStanding} htmlFor="mail-standing">
                      <Select
                        id="mail-standing"
                        value={gradeAlert.academicStanding}
                        onChange={(event) => setGradeAlert((current) => ({ ...current, academicStanding: event.target.value }))}
                        options={[
                          { value: '', label: '—' },
                          ...ACADEMIC_STANDINGS.map((key) => ({ value: key, label: copy.standings[key] })),
                        ]}
                      />
                    </Field>
                  </div>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <Field label={copy.fields.conductScore} htmlFor="mail-conduct-score">
                      <Input
                        id="mail-conduct-score"
                        inputMode="numeric"
                        placeholder="95"
                        value={gradeAlert.conductScore}
                        error={issueText('conductInvalid')}
                        onChange={(event) => setGradeAlert((current) => ({ ...current, conductScore: event.target.value }))}
                      />
                    </Field>
                    <Field label={copy.fields.conductRank} htmlFor="mail-conduct-rank">
                      <Input
                        id="mail-conduct-rank"
                        placeholder={copy.standings.excellent}
                        value={gradeAlert.conductRank}
                        onChange={(event) => setGradeAlert((current) => ({ ...current, conductRank: event.target.value }))}
                      />
                    </Field>
                  </div>
                  <section aria-label={copy.fields.grades} className="space-y-2">
                    <div className="flex items-center justify-between gap-2">
                      <h3 className="text-sm font-semibold text-foreground">{copy.fields.grades}</h3>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => setGrades((current) => [...current, { ...EMPTY_GRADE_ROW }])}
                      >
                        <Plus className="mr-1.5 h-4 w-4" aria-hidden="true" />
                        {copy.fields.addGrade}
                      </Button>
                    </div>
                    {grades.length === 0 ? (
                      <p className={cn('text-sm', issueText('gradesRequired') ? 'text-destructive' : 'text-muted-foreground')}>
                        {issueText('gradesRequired') || copy.gradesEmpty}
                      </p>
                    ) : (
                      <div className="space-y-3">
                        {grades.map((row, index) => {
                          const rowInvalid =
                            attemptedSend &&
                            (!row.courseCode.trim() || !row.courseName.trim() || !scoreRowInRange(row.score10));
                          return (
                            <div key={index} className="rounded-lg border border-border/70 bg-secondary/30 p-3">
                              <div className="grid gap-2 sm:grid-cols-12">
                                <div className="sm:col-span-3">
                                  <Field label={copy.fields.code} htmlFor={`grade-${index}-code`} hideLabel compact>
                                    <Input
                                      id={`grade-${index}-code`}
                                      className={cn(rowInvalid && INVALID_BORDER)}
                                      aria-invalid={rowInvalid ? true : undefined}
                                      placeholder={copy.fields.code}
                                      value={row.courseCode}
                                      onChange={(event) => setGrades((current) => current.map((item, i) => (i === index ? { ...item, courseCode: event.target.value } : item)))}
                                    />
                                  </Field>
                                </div>
                                <div className="sm:col-span-4">
                                  <Field label={copy.fields.courseName} htmlFor={`grade-${index}-name`} hideLabel compact>
                                    <Input
                                      id={`grade-${index}-name`}
                                      className={cn(rowInvalid && INVALID_BORDER)}
                                      aria-invalid={rowInvalid ? true : undefined}
                                      placeholder={copy.fields.courseName}
                                      value={row.courseName}
                                      onChange={(event) => setGrades((current) => current.map((item, i) => (i === index ? { ...item, courseName: event.target.value } : item)))}
                                    />
                                  </Field>
                                </div>
                                <div className="sm:col-span-2">
                                  <Field label={copy.fields.credits} htmlFor={`grade-${index}-credits`} hideLabel compact>
                                    <Input
                                      id={`grade-${index}-credits`}
                                      inputMode="numeric"
                                      placeholder={copy.fields.credits}
                                      value={row.credits}
                                      className={cn(attemptedSend && !rowCreditsValid(row.credits) && INVALID_BORDER)}
                                      aria-invalid={attemptedSend && !rowCreditsValid(row.credits) ? true : undefined}
                                      error={issueText('creditsInvalid')}
                                      onChange={(event) => setGrades((current) => current.map((item, i) => (i === index ? { ...item, credits: event.target.value } : item)))}
                                    />
                                  </Field>
                                </div>
                                <div className="sm:col-span-2">
                                  <Field label={copy.fields.score10} htmlFor={`grade-${index}-score`} hideLabel compact>
                                    <Input
                                      id={`grade-${index}-score`}
                                      inputMode="decimal"
                                      placeholder="9.5"
                                      className={cn(rowInvalid && INVALID_BORDER)}
                                      aria-invalid={rowInvalid ? true : undefined}
                                      value={row.score10}
                                      onChange={(event) => setGrades((current) => current.map((item, i) => (i === index ? { ...item, score10: event.target.value } : item)))}
                                    />
                                  </Field>
                                </div>
                                <div className="flex items-end justify-end sm:col-span-1">
                                  <Button
                                    type="button"
                                    variant="ghost"
                                    size="icon"
                                    aria-label={copy.fields.removeRow}
                                    onClick={() => setGrades((current) => current.filter((_, i) => i !== index))}
                                  >
                                    <Trash2 className="h-4 w-4" aria-hidden="true" />
                                  </Button>
                                </div>
                              </div>
                              <p className="mt-1.5 text-xs text-muted-foreground">
                                {copy.fields.letter}:{' '}
                                <span className="font-semibold text-foreground">{letterFor(row) || '—'}</span>
                              </p>
                            </div>
                          );
                        })}
                        {issueText('gradeRowsInvalid') ? (
                          <p className="text-sm text-destructive" role="alert">{copy.validation.gradeRowsInvalid}</p>
                        ) : null}
                      </div>
                    )}
                  </section>
                </>
              ) : null}
            </fieldset>
          </CardContent>
        </Card>

        {/* Right rail: recipient summary, sandbox note, send CTA, outcome. */}
        <div className="space-y-4">
          <Card variant="muted">
            <CardHeader>
              <CardTitle>{copy.recipientTitle}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2.5 text-sm">
              <RailRow label={copy.fields.email}>
                {(template === 'notice' ? notice.to : template === 'registration' ? registration.to : gradeAlert.to).trim() || (
                  <span className="font-normal text-muted-foreground">{copy.recipientEmpty}</span>
                )}
              </RailRow>
              <RailRow label={copy.templateLabel}>{templateTitle(template)}</RailRow>
              {template === 'notice' ? (
                <RailRow label={copy.fields.charCount.replace('{count}', '').trim()}>
                  {formatNumber(notice.content.length)}
                </RailRow>
              ) : null}
              {template === 'registration' && courses.length > 0 ? (
                <>
                  <RailRow label={copy.fields.courses}>
                    {copy.coursesSummary.replace('{count}', formatNumber(courses.length))}
                  </RailRow>
                  <RailRow label={copy.fields.credits}>{formatNumber(totalCredits)}</RailRow>
                </>
              ) : null}
              {template === 'grade-alert' && grades.length > 0 ? (
                <>
                  <RailRow label={copy.fields.grades}>
                    {copy.gradesSummary.replace('{count}', formatNumber(grades.length))}
                  </RailRow>
                  <RailRow label={copy.averageLabel}>
                    {weightedAverage !== null ? formatNumber(weightedAverage) : copy.averageEmpty}
                  </RailRow>
                </>
              ) : null}
              <div
                className={cn(
                  'mt-1 flex items-center gap-2 rounded-md border px-3 py-2 text-xs font-semibold',
                  readyToSend
                    ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
                    : 'border-border/70 bg-card text-muted-foreground',
                )}
              >
                {readyToSend ? (
                  <BadgeCheck className="h-4 w-4 shrink-0" aria-hidden="true" />
                ) : (
                  <Info className="h-4 w-4 shrink-0" aria-hidden="true" />
                )}
                <span>
                  <span className="sr-only">{copy.statusLabel}: </span>
                  {statusLabel}
                </span>
              </div>
            </CardContent>
          </Card>

          <Card variant="muted">
            <CardContent className="pt-5">
              <h3 className="flex items-center gap-1.5 text-sm font-semibold text-foreground">
                <Info className="h-4 w-4 text-[var(--portal-brand-gold)]" aria-hidden="true" />
                {copy.sandboxTitle}
              </h3>
              <p className="mt-1.5 text-xs leading-5 text-muted-foreground">{copy.sandboxNote}</p>
            </CardContent>
          </Card>

          {errorKind ? (
            <div
              role="alert"
              className="rounded-lg border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm leading-6 text-destructive"
            >
              {copy.errors[errorKind]}
            </div>
          ) : null}

          <div className="space-y-2">
            <Button
              type="button"
              variant="registration"
              size="lg"
              className="w-full"
              onClick={() => void handleSend()}
              disabled={isSending || !readyToSend}
              title={readyToSend ? undefined : copy.incomplete}
            >
              <Send className="mr-2 h-4 w-4" aria-hidden="true" />
              {isSending ? copy.sending : copy.send}
            </Button>
            <Button
              type="button"
              variant="outline"
              size="lg"
              className="w-full"
              onClick={() => setPreviewOpen(true)}
            >
              <Eye className="mr-2 h-4 w-4" aria-hidden="true" />
              {copy.preview}
            </Button>
            <p className="text-center text-xs text-muted-foreground">{copy.previewHint}</p>
          </div>

          {sent ? (
            <div
              ref={successRef}
              role="status"
              className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-4"
            >
              <div className="flex items-center gap-2">
                <span
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-emerald-500 text-white"
                  aria-hidden="true"
                >
                  <Check className="h-5 w-5" />
                </span>
                <p className="text-sm font-semibold text-emerald-800 dark:text-emerald-200">{copy.successTitle}</p>
              </div>
              <dl className="mt-3 space-y-1.5 text-xs leading-5">
                <div className="flex gap-2">
                  <dt className="shrink-0 font-medium text-muted-foreground">{copy.successTemplate}:</dt>
                  <dd className="min-w-0 font-semibold text-foreground">{sent.template}</dd>
                </div>
                <div className="flex gap-2">
                  <dt className="shrink-0 font-medium text-muted-foreground">{copy.successDeliveredTo}:</dt>
                  <dd className="min-w-0 truncate font-semibold text-foreground" title={sent.recipient}>{sent.recipient}</dd>
                </div>
                <div className="flex gap-2">
                  <dt className="shrink-0 font-medium text-muted-foreground">{copy.successSentAt}:</dt>
                  <dd className="min-w-0 font-semibold text-foreground">{formatDateTime(sent.dispatchedAt)}</dd>
                </div>
              </dl>
            </div>
          ) : null}
        </div>
      </div>

      {confirmationDialog}

      {/* Draft preview: the values below are exactly what dispatchForTemplate()
          posts — the canned layout sample is only linked, never implied. */}
      <Modal
        isOpen={previewOpen}
        onClose={() => setPreviewOpen(false)}
        title={copy.previewDialogTitle}
        description={copy.previewDialogNote}
        className="max-w-lg"
        showCloseButton
      >
        <div className="space-y-4">
          <dl className="space-y-2.5">
            {previewRows.map(([label, value]) => (
              <div key={label} className="grid grid-cols-[minmax(0,9rem)_1fr] gap-3 text-sm">
                <dt className="font-medium text-muted-foreground">{label}</dt>
                <dd className="min-w-0 whitespace-pre-line break-words text-foreground">{value}</dd>
              </div>
            ))}
          </dl>
          {template === 'registration' && courses.length > 0 ? (
            <table className="w-full text-left text-xs">
              <thead className="text-muted-foreground">
                <tr>
                  <th className="py-1 pr-2 font-medium">{copy.fields.code}</th>
                  <th className="py-1 pr-2 font-medium">{copy.fields.courseName}</th>
                  <th className="py-1 pr-2 font-medium">{copy.fields.credits}</th>
                  <th className="py-1 font-medium">{copy.fields.schedule}</th>
                </tr>
              </thead>
              <tbody>
                {courses.map((row, index) => (
                  <tr key={index} className="border-t border-border/50">
                    <td className="py-1 pr-2">{row.code.trim() || copy.previewEmptyField}</td>
                    <td className="py-1 pr-2">{row.name.trim() || copy.previewEmptyField}</td>
                    <td className="py-1 pr-2">{row.credits.trim() || copy.previewEmptyField}</td>
                    <td className="py-1">{row.schedule.trim() || copy.previewEmptyField}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : null}
          {template === 'grade-alert' && grades.length > 0 ? (
            <table className="w-full text-left text-xs">
              <thead className="text-muted-foreground">
                <tr>
                  <th className="py-1 pr-2 font-medium">{copy.fields.code}</th>
                  <th className="py-1 pr-2 font-medium">{copy.fields.courseName}</th>
                  <th className="py-1 pr-2 font-medium">{copy.fields.score10}</th>
                  <th className="py-1 font-medium">{copy.fields.letter}</th>
                </tr>
              </thead>
              <tbody>
                {grades.map((row, index) => (
                  <tr key={index} className="border-t border-border/50">
                    <td className="py-1 pr-2">{row.courseCode.trim() || copy.previewEmptyField}</td>
                    <td className="py-1 pr-2">{row.courseName.trim() || copy.previewEmptyField}</td>
                    <td className="py-1 pr-2">{row.score10.trim() || copy.previewEmptyField}</td>
                    <td className="py-1">{row.scoreLetter.trim() || letterFor(row) || copy.previewEmptyField}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : null}
          <a
            href={previewHref}
            target="_blank"
            rel="noreferrer noopener"
            className="inline-flex items-center gap-1.5 text-xs font-medium text-primary hover:underline"
          >
            {copy.previewSampleLink}
          </a>
        </div>
      </Modal>
    </div>
  );
}
