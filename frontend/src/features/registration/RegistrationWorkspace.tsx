'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  CalendarDays,
  Check,
  ChevronDown,
  Clock3,
  Download,
  Filter,
  LoaderCircle,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Users,
  X,
} from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { ErrorState, EmptyState, ForbiddenState, LoadingState } from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { cn } from '@/lib/utils';
import { registrationApi } from './api';
import type {
  RegistrationEligibility,
  RegistrationEnrollment,
  RegistrationRound,
  RegistrationSection,
  RegistrationSummary,
  RegistrationViolation,
} from './types';

const dayLabels: Record<string, string> = {
  '0': 'CN', '1': 'T2', '2': 'T3', '3': 'T4', '4': 'T5', '5': 'T6', '6': 'T7',
  SUNDAY: 'CN', MONDAY: 'T2', TUESDAY: 'T3', WEDNESDAY: 'T4', THURSDAY: 'T5', FRIDAY: 'T6', SATURDAY: 'T7',
};

function normalizeSection(section: RegistrationSection & { course?: { code?: string; name?: string; nameVi?: string; nameEn?: string }; schedules?: RegistrationSection['schedules'] }): RegistrationSection {
  const capacity = Number(section.capacity ?? 0);
  const enrolledCount = Number(section.enrolledCount ?? 0);
  return {
    ...section,
    courseCode: section.courseCode || section.course?.code || '—',
    courseName: section.courseName || section.course?.name || '—',
    courseNameVi: section.courseNameVi || section.course?.nameVi,
    courseNameEn: section.courseNameEn || section.course?.nameEn,
    schedules: section.schedules ?? [],
    capacity,
    enrolledCount,
    remainingSeats: Math.max(0, Number(section.remainingSeats ?? capacity - enrolledCount)),
  };
}

function normalizeEnrollment(enrollment: RegistrationEnrollment & { section?: { id?: string; courseCode?: string; courseName?: string; credits?: number; sectionNumber?: string } }): RegistrationEnrollment {
  return {
    ...enrollment,
    sectionId: enrollment.sectionId || enrollment.section?.id || '',
    courseCode: enrollment.courseCode || enrollment.section?.courseCode,
    courseName: enrollment.courseName || enrollment.section?.courseName,
    credits: enrollment.credits ?? enrollment.section?.credits,
    sectionNumber: enrollment.sectionNumber || enrollment.section?.sectionNumber,
  };
}

function formatWindow(value?: string | null, locale = 'vi'): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(locale === 'vi' ? 'vi-VN' : 'en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}

function roundTitle(round: RegistrationRound, locale: 'vi' | 'en'): string {
  return round.semester?.[locale === 'vi' ? 'nameVi' : 'nameEn'] || round.semester?.name || round.semesterName || 'Registration round';
}

function readableViolation(violation: RegistrationViolation, locale: 'vi' | 'en'): string {
  if (locale === 'vi') {
    const labels: Record<string, string> = {
      SCHEDULE_CONFLICT: 'Lịch học bị trùng với học phần đã chọn.',
      PREREQUISITE_NOT_MET: 'Chưa đạt học phần tiên quyết.',
      COREQUISITE_NOT_MET: 'Thiếu học phần học cùng kỳ.',
      CREDIT_LIMIT_EXCEEDED: 'Vượt quá giới hạn tín chỉ của đợt đăng ký.',
      SECTION_FULL: 'Lớp học phần đã đủ chỗ.',
    };
    return labels[violation.code] || violation.message;
  }
  return violation.message || violation.code;
}

export default function RegistrationWorkspace() {
  const { isLoading: authLoading, hasAccess } = useRequireAuth(['STUDENT']);
  const { locale, formatNumber } = useI18n();
  const copy = locale === 'vi' ? {
    eyebrow: 'CỔNG HỌC VỤ', title: 'Đăng ký học phần', description: 'Chọn lớp học phần trong đợt đang mở. Hệ thống kiểm tra điều kiện, lịch học và sức chứa trước khi ghi nhận.',
    round: 'Đợt đăng ký', open: 'Đang mở', closed: 'Đã đóng', starts: 'Mở từ', ends: 'Đóng lúc', addDrop: 'Thêm / hủy',
    eligibility: 'Trạng thái đủ điều kiện', eligible: 'Đủ điều kiện đăng ký', ineligible: 'Chưa đủ điều kiện', priority: 'Luồng ưu tiên', priorityValue: 'Ưu tiên', search: 'Tìm mã môn, tên môn, giảng viên', department: 'Khoa', allDepartments: 'Tất cả khoa', credits: 'Tín chỉ', allCredits: 'Tất cả số tín chỉ', day: 'Ngày học', allDays: 'Tất cả ngày', seats: 'Còn chỗ', full: 'Hết chỗ', section: 'Lớp', lecturer: 'Giảng viên', room: 'Phòng', schedule: 'Lịch học', choose: 'Chọn xem trước', chosen: 'Đã chọn', remove: 'Bỏ chọn', review: 'Kiểm tra lựa chọn', confirm: 'Xác nhận đăng ký', confirmTitle: 'Xác nhận lựa chọn học phần', confirmBody: 'Các lớp sẽ được ghi nhận theo thứ tự. Bạn có thể xem lại phiếu sau khi hoàn tất.', cancel: 'Hủy', loading: 'Đang tải dữ liệu đăng ký…', retry: 'Thử lại', empty: 'Không có lớp phù hợp', emptyBody: 'Thử đổi bộ lọc hoặc chọn đợt đăng ký khác.', error: 'Không thể tải dữ liệu đăng ký', forbidden: 'Bạn chưa được cấp quyền đăng ký', forbiddenBody: 'Tài khoản sinh viên cần được mở quyền theo đợt và khóa học.', selected: 'Đã chọn', max: 'Tối đa', downloadSlip: 'Tải phiếu đăng ký', success: 'Đăng ký học phần thành công.', failed: 'Một số lớp chưa thể đăng ký. Vui lòng xem nguyên nhân.', serverTime: 'Giờ hệ thống', validation: 'Kiểm tra điều kiện', noViolations: 'Lựa chọn hợp lệ. Bạn có thể xác nhận.', dismiss: 'Đóng', studentView: 'Chế độ sinh viên', reconcile: 'Đã cập nhật trạng thái từ máy chủ',
  } : {
    eyebrow: 'ACADEMIC PORTAL', title: 'Course registration', description: 'Choose sections during the active window. Eligibility, schedule and capacity are checked before anything is committed.',
    round: 'Registration round', open: 'Open', closed: 'Closed', starts: 'Opens', ends: 'Closes', addDrop: 'Add / drop', eligibility: 'Eligibility', eligible: 'Eligible to register', ineligible: 'Not eligible', priority: 'Priority lane', priorityValue: 'Priority', search: 'Search course code, name or lecturer', department: 'Department', allDepartments: 'All departments', credits: 'Credits', allCredits: 'All credits', day: 'Day', allDays: 'All days', seats: 'Seats left', full: 'Full', section: 'Section', lecturer: 'Lecturer', room: 'Room', schedule: 'Schedule', choose: 'Preview selection', chosen: 'Selected', remove: 'Remove', review: 'Review selection', confirm: 'Confirm registration', confirmTitle: 'Confirm selected sections', confirmBody: 'Sections will be committed in order. You can download your registration slip after completion.', cancel: 'Cancel', loading: 'Loading registration data…', retry: 'Retry', empty: 'No matching sections', emptyBody: 'Try another filter or registration round.', error: 'Registration data could not be loaded', forbidden: 'Registration access is restricted', forbiddenBody: 'Your student account must be eligible for the selected round.', selected: 'Selected', max: 'Maximum', downloadSlip: 'Download registration slip', success: 'Enrollment completed.', failed: 'Some sections could not be enrolled. Review the reasons.', serverTime: 'Server time', validation: 'Eligibility check', noViolations: 'Selection is valid. You can confirm.', dismiss: 'Close', studentView: 'Student view', reconcile: 'State reconciled with server',
  };
  const [rounds, setRounds] = useState<RegistrationRound[]>([]);
  const [roundId, setRoundId] = useState('');
  const [round, setRound] = useState<RegistrationRound | null>(null);
  const [sections, setSections] = useState<RegistrationSection[]>([]);
  const [enrollments, setEnrollments] = useState<RegistrationEnrollment[]>([]);
  const [eligibility, setEligibility] = useState<RegistrationEligibility | null>(null);
  const [summary, setSummary] = useState<RegistrationSummary | null>(null);
  const [search, setSearch] = useState('');
  const [department, setDepartment] = useState('');
  const [creditFilter, setCreditFilter] = useState('');
  const [dayFilter, setDayFilter] = useState('');
  const [selected, setSelected] = useState<string[]>([]);
  const [violations, setViolations] = useState<RegistrationViolation[]>([]);
  const [validationOpen, setValidationOpen] = useState(false);
  const [loading, setLoading] = useState(true);
  const [mutating, setMutating] = useState(false);
  const [errorStatus, setErrorStatus] = useState<'error' | 'forbidden' | null>(null);
  const [notice, setNotice] = useState('');
  const [now, setNow] = useState(() => Date.now());
  const [serverOffset, setServerOffset] = useState(0);
  const { confirm, confirmationDialog } = useConfirmationDialog();

  const load = useCallback(async (nextRoundId?: string) => {
    setLoading(true); setErrorStatus(null); setNotice('');
    try {
      const roundPage = await registrationApi.getRounds();
      const nextRounds = roundPage.items;
      setRounds(nextRounds);
      const active = nextRoundId || roundId || nextRounds.find((item) => ['OPEN', 'REGISTRATION_OPEN', 'ADD_DROP_OPEN'].includes(item.status))?.id || nextRounds[0]?.id;
      if (!active) { setRound(null); setSections([]); setLoading(false); return; }
      setRoundId(active);
      const [roundDetail, sectionPage, nextEligibility, nextSummary, enrollmentPage] = await Promise.all([
        registrationApi.getRound(active), registrationApi.getSections(active), registrationApi.getEligibility(active), registrationApi.getSummary(active), registrationApi.getEnrollments(),
      ]);
      setRound(roundDetail); setServerOffset(roundDetail.serverNow ? new Date(roundDetail.serverNow).getTime() - Date.now() : 0); setSections(sectionPage.items.map((item) => normalizeSection(item))); setEligibility(nextEligibility); setSummary(nextSummary); setEnrollments(enrollmentPage.items.map((item) => normalizeEnrollment(item))); setSelected([]);
    } catch (error) {
      const status = (error as { response?: { status?: number } }).response?.status;
      setErrorStatus(status === 401 || status === 403 ? 'forbidden' : 'error');
    } finally { setLoading(false); }
  }, [roundId]);

  useEffect(() => { if (!authLoading && hasAccess) void load(); }, [authLoading, hasAccess, load]);
  useEffect(() => { const id = window.setInterval(() => setNow(Date.now()), 30_000); return () => window.clearInterval(id); }, []);

  const enrollmentBySection = useMemo(() => new Map(enrollments.filter((item) => !['DROPPED', 'CANCELLED'].includes(item.status)).map((item) => [item.sectionId, item])), [enrollments]);
  const departments = useMemo(() => Array.from(new Set(sections.map((item) => item.departmentCode || item.department).filter(Boolean) as string[])).sort(), [sections]);
  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    return sections.filter((section) => {
      const haystack = [section.courseCode, section.courseName, section.courseNameVi, section.courseNameEn, section.lecturer].filter(Boolean).join(' ').toLocaleLowerCase();
      const dayMatch = !dayFilter || section.schedules.some((slot) => String(slot.dayOfWeek).toUpperCase() === dayFilter);
      return (!query || haystack.includes(query)) && (!department || section.departmentCode === department || section.department === department) && (!creditFilter || String(section.credits) === creditFilter) && dayMatch;
    });
  }, [sections, search, department, creditFilter, dayFilter]);
  const selectedSections = useMemo(() => selected.map((id) => sections.find((section) => section.id === id)).filter(Boolean) as RegistrationSection[], [selected, sections]);
  const selectedCredits = selectedSections.reduce((total, section) => total + section.credits, 0);
  const maxCredits = summary?.maxCredits ?? round?.maxCredits ?? 28;
  const effectiveServerNow = now + serverOffset;
  const opening = round?.registrationStart ? new Date(round.registrationStart).getTime() : 0;
  const deadline = round?.registrationEnd ? new Date(round.registrationEnd).getTime() : 0;
  const isOpen = round ? ['OPEN', 'REGISTRATION_OPEN', 'ADD_DROP_OPEN'].includes(round.status) && (!opening || opening <= effectiveServerNow) && (!deadline || deadline >= effectiveServerNow) : false;
  const serverNow = formatWindow(new Date(effectiveServerNow).toISOString(), locale);

  const toggleSelected = (section: RegistrationSection) => {
    if (enrollmentBySection.has(section.id)) return;
    setNotice('');
    setSelected((current) => current.includes(section.id) ? current.filter((id) => id !== section.id) : [...current, section.id]);
  };
  const validateSelection = async () => {
    if (!roundId || selected.length === 0) return;
    setMutating(true); setValidationOpen(true);
    try { const result = await registrationApi.validate(roundId, selected); setViolations(result.violations); } catch { setViolations([{ code: 'VALIDATION_UNAVAILABLE', message: locale === 'vi' ? 'Không thể kiểm tra lúc này.' : 'Validation is temporarily unavailable.' }]); } finally { setMutating(false); }
  };
  const commitSelection = async () => {
    if (!roundId || !selected.length) return;
    const confirmed = await confirm({ title: copy.confirmTitle, message: copy.confirmBody, confirmText: copy.confirm, cancelText: copy.cancel });
    if (!confirmed) return;
    setMutating(true); setValidationOpen(false); setNotice('');
    try {
      for (const sectionId of selected) await registrationApi.enroll(roundId, sectionId);
      setNotice(copy.success); await load(roundId);
    } catch { setNotice(copy.failed); await load(roundId); } finally { setMutating(false); }
  };
  const dropEnrollment = async (enrollment: RegistrationEnrollment) => {
    const confirmed = await confirm({ title: locale === 'vi' ? 'Hủy học phần?' : 'Drop this course?', message: locale === 'vi' ? 'Thao tác này chỉ thực hiện trong thời gian thêm / hủy.' : 'This action is only available during the add / drop window.', confirmText: locale === 'vi' ? 'Hủy học phần' : 'Drop course', cancelText: copy.cancel, variant: 'destructive' });
    if (!confirmed) return;
    setMutating(true); try { await registrationApi.drop(enrollment.id); setNotice(copy.reconcile); await load(roundId); } catch { setNotice(copy.failed); } finally { setMutating(false); }
  };
  const downloadSlip = async () => {
    if (!roundId) return;
    try { const blob = await registrationApi.getSlip(roundId); const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = `campuscore-registration-${roundId}.pdf`; anchor.click(); URL.revokeObjectURL(url); } catch { setNotice(locale === 'vi' ? 'Chưa thể tạo phiếu PDF.' : 'The registration slip is not available yet.'); }
  };

  if (authLoading) return <LoadingState label={copy.loading} />;
  if (!hasAccess) return <ForbiddenState title={copy.forbidden} description={copy.forbiddenBody} />;
  if (loading && !round) return <LoadingState label={copy.loading} />;
  if (errorStatus === 'forbidden') return <ForbiddenState title={copy.forbidden} description={copy.forbiddenBody} action={<Button onClick={() => void load()}>{copy.retry}</Button>} />;
  if (errorStatus === 'error') return <ErrorState title={copy.error} description={copy.failed} onRetry={() => void load()} retryLabel={copy.retry} />;

  return <div className="space-y-6 pb-24">
    {confirmationDialog}
    <header className="portal-page-ribbon flex flex-col gap-4 px-5 py-5 sm:flex-row sm:items-end sm:justify-between">
      <div><p className="portal-menu-label">{copy.eyebrow}</p><h1 className="mt-2 text-2xl font-semibold tracking-tight text-foreground sm:text-3xl">{copy.title}</h1><p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">{copy.description}</p></div>
      <span className="inline-flex min-h-11 items-center gap-2 self-start rounded-md border border-[var(--portal-rule)] bg-[var(--portal-surface)] px-3 text-xs font-semibold text-muted-foreground sm:self-auto"><Clock3 className="size-4 text-primary" aria-hidden="true" />{copy.serverTime}: {serverNow}</span>
    </header>

    <section className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(18rem,24rem)]" aria-label={copy.round}>
      <Card className="overflow-hidden"><CardContent className="p-0"><div className="flex flex-col gap-4 border-b border-[var(--portal-rule)] bg-[var(--portal-sidebar)] px-5 py-5 text-[var(--portal-sidebar-text)] sm:flex-row sm:items-center sm:justify-between"><div><p className="text-xs font-semibold uppercase tracking-[0.08em] text-[var(--portal-sidebar-muted)]">{copy.round}</p><h2 className="mt-1 text-lg font-semibold">{round ? roundTitle(round, locale) : '—'}</h2></div><select value={roundId} onChange={(event) => void load(event.target.value)} aria-label={copy.round} className="min-h-11 rounded-md border border-white/20 bg-[var(--portal-sidebar-strong)] px-3 text-sm text-[var(--portal-sidebar-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--portal-yellow)]">{rounds.map((item) => <option key={item.id} value={item.id}>{roundTitle(item, locale)}</option>)}</select></div><div className="grid gap-4 px-5 py-4 text-sm sm:grid-cols-3"><div><p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">{copy.starts}</p><p className="mt-1 font-medium text-foreground">{formatWindow(round?.registrationStart, locale)}</p></div><div><p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">{copy.ends}</p><p className="mt-1 font-medium text-foreground">{formatWindow(round?.registrationEnd, locale)}</p></div><div><p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">{copy.addDrop}</p><p className="mt-1 font-medium text-foreground">{formatWindow(round?.addDropStart, locale)} → {formatWindow(round?.addDropEnd, locale)}</p></div></div></CardContent></Card>
      <Card variant="muted"><CardContent className="p-5"><div className="flex items-start justify-between gap-3"><div><p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">{copy.eligibility}</p><p className={cn('mt-2 text-lg font-semibold', eligibility?.state === 'ELIGIBLE' ? 'text-emerald-700 dark:text-emerald-300' : 'text-amber-700 dark:text-amber-300')}>{eligibility?.state === 'ELIGIBLE' ? copy.eligible : copy.ineligible}</p></div><ShieldCheck className="size-5 text-primary" aria-hidden="true" /></div><div className="mt-4 flex items-center justify-between border-t border-[var(--portal-rule)] pt-4 text-sm"><span className="text-muted-foreground">{copy.priority}</span><span className="font-semibold text-foreground">{eligibility?.priorityRank ? `${copy.priorityValue} #${eligibility.priorityRank}` : '—'}</span></div><span className={cn('mt-4 inline-flex min-h-8 items-center rounded-full px-3 text-xs font-semibold', isOpen ? 'bg-emerald-100 text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200' : 'bg-secondary text-muted-foreground')}>{isOpen ? copy.open : copy.closed}</span></CardContent></Card>
    </section>

    <section className="grid gap-3 sm:grid-cols-3" aria-label={copy.selected}>
      <Card><CardContent className="flex items-center justify-between gap-3 p-4"><div><p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">{copy.selected}</p><p className="mt-1 text-2xl font-semibold tabular-nums text-foreground">{formatNumber(selectedCredits)} <span className="text-sm font-medium text-muted-foreground">/ {formatNumber(maxCredits)} {copy.credits}</span></p></div><SlidersHorizontal className="size-5 text-primary" aria-hidden="true" /></CardContent></Card>
      <Card><CardContent className="flex items-center justify-between gap-3 p-4"><div><p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">{copy.chosen}</p><p className="mt-1 text-2xl font-semibold tabular-nums text-foreground">{formatNumber(selected.length)}</p></div><CalendarDays className="size-5 text-primary" aria-hidden="true" /></CardContent></Card>
      <Card><CardContent className="flex items-center justify-between gap-3 p-4"><div><p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">{copy.seats}</p><p className="mt-1 text-2xl font-semibold tabular-nums text-foreground">{formatNumber(filtered.reduce((sum, item) => sum + item.remainingSeats, 0))}</p></div><Users className="size-5 text-primary" aria-hidden="true" /></CardContent></Card>
    </section>

    {notice ? <div role="status" className="border border-emerald-300 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200">{notice}</div> : null}

    <Card><CardContent className="grid gap-3 p-4 md:grid-cols-[minmax(0,1.5fr)_repeat(3,minmax(9rem,1fr))]"><label className="relative block"><span className="sr-only">{copy.search}</span><Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" /><Input className="min-h-11 pl-10" value={search} onChange={(event) => setSearch(event.target.value)} placeholder={copy.search} /></label><label><span className="sr-only">{copy.department}</span><Select aria-label={copy.department} value={department} onChange={(event) => setDepartment(event.target.value)} options={[{ value: '', label: copy.allDepartments }, ...departments.map((item) => ({ value: item, label: item }))]} /></label><label><span className="sr-only">{copy.credits}</span><Select aria-label={copy.credits} value={creditFilter} onChange={(event) => setCreditFilter(event.target.value)} options={[{ value: '', label: copy.allCredits }, ...[1, 2, 3, 4, 5, 6].map((item) => ({ value: String(item), label: `${item} ${copy.credits}` }))]} /></label><label><span className="sr-only">{copy.day}</span><Select aria-label={copy.day} value={dayFilter} onChange={(event) => setDayFilter(event.target.value)} options={[{ value: '', label: copy.allDays }, ...Object.entries(dayLabels).filter(([key]) => /^\d$/.test(key)).map(([value, label]) => ({ value, label }))]} /></label></CardContent></Card>

    <Card className="overflow-hidden"><div className="flex flex-col gap-3 border-b border-[var(--portal-rule)] bg-[var(--portal-surface)] px-5 py-4 sm:flex-row sm:items-center sm:justify-between"><div><p className="text-sm font-semibold text-foreground">{formatNumber(filtered.length)} {copy.section}</p><p className="mt-1 text-xs text-muted-foreground">{isOpen ? copy.open : copy.closed}</p></div><div className="flex flex-wrap gap-2"><Button type="button" variant="outline" onClick={() => void validateSelection()} disabled={!selected.length || mutating}><Filter className="mr-2 size-4" aria-hidden="true" />{copy.review}</Button><Button type="button" variant="warm" onClick={() => void commitSelection()} disabled={!selected.length || !isOpen || selectedCredits > maxCredits || mutating}>{mutating ? <LoaderCircle className="mr-2 size-4 animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <Check className="mr-2 size-4" aria-hidden="true" />}{copy.confirm}</Button><Button type="button" variant="outline" onClick={() => void downloadSlip()} disabled={!enrollments.length}><Download className="mr-2 size-4" aria-hidden="true" />{copy.downloadSlip}</Button></div></div>{filtered.length === 0 ? <EmptyState icon={Search} title={copy.empty} description={copy.emptyBody} /> : <><div className="hidden overflow-x-auto lg:block"><table className="w-full min-w-[980px] text-left text-sm"><thead><tr className="border-b border-[var(--portal-rule)]"><th className="w-12 px-5 py-3"><span className="sr-only">{copy.choose}</span></th><th className="px-3 py-3 font-semibold">{copy.section}</th><th className="px-3 py-3 font-semibold">{copy.schedule}</th><th className="px-3 py-3 font-semibold">{copy.lecturer}</th><th className="px-3 py-3 font-semibold">{copy.seats}</th><th className="px-5 py-3 text-right font-semibold">{copy.choose}</th></tr></thead><tbody>{filtered.map((section) => { const enrolled = enrollmentBySection.get(section.id); const isSelected = selected.includes(section.id); return <tr key={section.id} className="border-b border-[var(--portal-rule)] last:border-0 hover:bg-[color-mix(in_oklch,var(--portal-sidebar)_3%,var(--portal-surface))]"><td className="px-5 py-4"><button type="button" onClick={() => toggleSelected(section)} disabled={Boolean(enrolled) || section.remainingSeats === 0} aria-label={`${isSelected ? copy.remove : copy.choose} ${section.courseCode}`} aria-pressed={isSelected} className={cn('flex size-7 items-center justify-center rounded-md border transition-[background-color,border-color,color] duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2', isSelected ? 'border-primary bg-primary text-primary-foreground' : 'border-[var(--portal-rule)] bg-[var(--portal-surface)] text-transparent hover:border-primary', enrolled && 'cursor-default border-emerald-600 bg-emerald-100 text-emerald-700')}>{enrolled ? <Check className="size-4" aria-hidden="true" /> : isSelected ? <Check className="size-4" aria-hidden="true" /> : null}</button></td><td className="px-3 py-4"><p className="font-semibold text-foreground">{section.courseCode} <span className="ml-1 text-xs font-medium text-muted-foreground">{section.credits} {copy.credits}</span></p><p className="mt-1 text-muted-foreground">{locale === 'vi' ? section.courseNameVi || section.courseName : section.courseNameEn || section.courseName}</p><p className="mt-1 text-xs text-muted-foreground">{section.department || '—'} · {section.sectionNumber}</p></td><td className="px-3 py-4 text-muted-foreground">{section.schedules.length ? <ul className="space-y-1">{section.schedules.map((slot, index) => <li key={slot.id || `${slot.dayOfWeek}-${index}`}><span className="font-medium text-foreground">{dayLabels[String(slot.dayOfWeek).toUpperCase()] || slot.dayOfWeek}</span> · {slot.startTime}–{slot.endTime}{slot.classroom || slot.roomNumber ? ` · ${slot.classroom || slot.roomNumber}` : ''}</li>)}</ul> : '—'}</td><td className="px-3 py-4 text-muted-foreground">{section.lecturer || '—'}</td><td className="px-3 py-4"><span className={cn('font-semibold tabular-nums', section.remainingSeats > 0 ? 'text-emerald-700 dark:text-emerald-300' : 'text-muted-foreground')}>{section.remainingSeats > 0 ? formatNumber(section.remainingSeats) : copy.full}</span></td><td className="px-5 py-4 text-right"><Button type="button" size="sm" variant={enrolled ? 'outline' : isSelected ? 'secondary' : 'default'} onClick={() => enrolled ? void dropEnrollment(enrolled) : toggleSelected(section)} disabled={section.remainingSeats === 0 && !enrolled}>{enrolled ? copy.remove : isSelected ? copy.chosen : copy.choose}</Button></td></tr>; })}</tbody></table></div><div className="grid gap-3 p-3 lg:hidden">{filtered.map((section) => { const enrolled = enrollmentBySection.get(section.id); const isSelected = selected.includes(section.id); return <article key={section.id} className="border border-[var(--portal-rule)] bg-[var(--portal-surface)] p-4"><div className="flex items-start justify-between gap-4"><div><p className="font-semibold text-foreground">{section.courseCode} <span className="ml-1 text-xs font-medium text-muted-foreground">{section.credits} {copy.credits}</span></p><p className="mt-1 text-sm text-muted-foreground">{locale === 'vi' ? section.courseNameVi || section.courseName : section.courseNameEn || section.courseName}</p></div><span className="rounded-full bg-secondary px-2 py-1 text-xs font-semibold text-muted-foreground">{section.sectionNumber}</span></div><dl className="mt-4 grid gap-3 text-sm"><div><dt className="font-semibold text-foreground">{copy.schedule}</dt><dd className="mt-1 text-muted-foreground">{section.schedules.length ? section.schedules.map((slot) => `${dayLabels[String(slot.dayOfWeek).toUpperCase()] || slot.dayOfWeek} · ${slot.startTime}–${slot.endTime}${slot.classroom || slot.roomNumber ? ` · ${slot.classroom || slot.roomNumber}` : ''}`).join(', ') : '—'}</dd></div><div className="flex items-center justify-between"><div><dt className="font-semibold text-foreground">{copy.lecturer}</dt><dd className="mt-1 text-muted-foreground">{section.lecturer || '—'}</dd></div><div className="text-right"><dt className="font-semibold text-foreground">{copy.seats}</dt><dd className={cn('mt-1 font-semibold', section.remainingSeats > 0 ? 'text-emerald-700 dark:text-emerald-300' : 'text-muted-foreground')}>{section.remainingSeats > 0 ? formatNumber(section.remainingSeats) : copy.full}</dd></div></div></dl><Button type="button" className="mt-4 w-full" variant={enrolled ? 'outline' : isSelected ? 'secondary' : 'default'} onClick={() => enrolled ? void dropEnrollment(enrolled) : toggleSelected(section)} disabled={section.remainingSeats === 0 && !enrolled}>{enrolled ? copy.remove : isSelected ? copy.chosen : copy.choose}</Button></article>; })}</div></>}</Card>

    {validationOpen ? <div className="fixed inset-0 z-40"><button type="button" aria-label={copy.dismiss} className="absolute inset-0 bg-[var(--portal-scrim)]" onClick={() => setValidationOpen(false)} /><aside role="dialog" aria-modal="true" aria-labelledby="registration-validation-title" className="absolute inset-y-0 right-0 flex w-full max-w-md flex-col border-l border-[var(--portal-rule)] bg-[var(--portal-surface)] shadow-2xl"><div className="flex items-center justify-between border-b border-[var(--portal-rule)] px-5 py-4"><div><p className="portal-menu-label">{copy.validation}</p><h2 id="registration-validation-title" className="mt-1 text-lg font-semibold text-foreground">{selected.length} {copy.chosen}</h2></div><Button type="button" variant="ghost" size="icon" onClick={() => setValidationOpen(false)} aria-label={copy.dismiss}><X className="size-5" aria-hidden="true" /></Button></div><div className="flex-1 overflow-y-auto p-5">{mutating ? <LoadingState label={copy.loading} className="min-h-32 border-0 p-0" /> : violations.length === 0 ? <div className="border border-emerald-300 bg-emerald-50 p-4 text-sm text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200"><ShieldCheck className="mb-2 size-5" aria-hidden="true" />{copy.noViolations}</div> : <ul className="space-y-3">{violations.map((violation, index) => <li key={`${violation.code}-${index}`} className="flex gap-3 border border-amber-300 bg-amber-50 p-3 text-sm text-amber-950 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200"><AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" /><span><strong className="font-semibold">{violation.code}</strong><br />{readableViolation(violation, locale)}</span></li>)}</ul>}</div><div className="border-t border-[var(--portal-rule)] p-5"><Button type="button" className="w-full" onClick={() => void commitSelection()} disabled={mutating || violations.length > 0 || !isOpen}>{copy.confirm}</Button></div></aside></div> : null}
  </div>;
}
