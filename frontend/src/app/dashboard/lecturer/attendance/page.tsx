'use client';

import { useCallback, useEffect, useId, useMemo, useRef, useState } from 'react';
import {
  AlertCircle,
  CalendarCheck,
  CheckCircle2,
  Clock,
  Download,
  Printer,
  RefreshCw,
  Save,
  Users,
  XCircle,
} from 'lucide-react';
import {
  downloadCsvFile,
  generateAttendanceCsv,
} from '@/lib/attendance-export';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { useRequireAuth } from '@/context/AuthContext';
import {
  attendanceApi,
  sectionsApi,
  semestersApi,
  type AttendanceStatus,
  type SectionAttendanceSummary,
  type StudentAttendanceEntry,
} from '@/lib/api';
import { getLocalizedFlatLabel, getLocalizedName } from '@/lib/academic-content';
import { calculateAttendanceRate } from '@/lib/attendance-rate';
import { GradingSection, Semester } from '@/types/api';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import {
  WorkspaceMetricCard,
  WorkspacePanel,
} from '@/components/dashboard/WorkspaceSurface';
import { metricToneClass } from '@/components/ui/status';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import { toast } from 'sonner';

function getTodayString(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

type StudentRowState = {
  studentId: string;
  studentCode: string;
  studentName: string;
  status: AttendanceStatus | null;
  notes: string;
};

export default function LecturerAttendancePage() {
  const { hasAccess, isLoading: authLoading } = useRequireAuth(['LECTURER']);
  const { locale, formatNumber, messages } = useI18n();
  const copy = messages.lecturerAttendance;
  const controlId = useId().replace(/:/g, '');
  const { confirm, confirmationDialog } = useConfirmationDialog();

  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [sections, setSections] = useState<GradingSection[]>([]);
  const [selectedSectionId, setSelectedSectionId] = useState('');
  const [selectedDate, setSelectedDate] = useState<string>(getTodayString);

  const [isLoadingSections, setIsLoadingSections] = useState(true);
  const [isLoadingRoster, setIsLoadingRoster] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');
  const [sectionsLoadFailed, setSectionsLoadFailed] = useState(false);

  const [roster, setRoster] = useState<StudentRowState[]>([]);
  // Rows cleared via Reset whose RESET entry has not been saved yet.
  const [resetStudentIds, setResetStudentIds] = useState<Set<string>>(new Set());
  const [sectionSummary, setSectionSummary] = useState<SectionAttendanceSummary | null>(null);
  const [existingReadFailed, setExistingReadFailed] = useState(false);
  // Request generations: a stale sections/roster response must never overwrite
  // the newer selection — writing D1's roster under D2 would persist marks to
  // the wrong students on Save.
  const sectionsGenerationRef = useRef(0);
  const rosterGenerationRef = useRef(0);

  // 1. Fetch semesters
  const fetchSemesters = useCallback(async () => {
    try {
      const response = await semestersApi.getAll();
      setSemesters(response.data ?? []);
    } catch {
      // Non-fatal, fallback to all sections
    }
  }, []);

  // 2. Fetch lecturer's assigned sections
  const fetchSections = useCallback(async () => {
    const generation = ++sectionsGenerationRef.current;
    setIsLoadingSections(true);
    setSectionsLoadFailed(false);
    try {
      const data = await sectionsApi.getMyGradingSections(
        selectedSemester || undefined,
      );
      if (sectionsGenerationRef.current !== generation) return;
      setSections(data);
      if (data.length > 0) {
        setSelectedSectionId((prev) => {
          const exists = data.some((s) => s.sectionId === prev);
          return exists ? prev : data[0].sectionId;
        });
      } else {
        setSelectedSectionId('');
      }
    } catch {
      if (sectionsGenerationRef.current !== generation) return;
      // A failed load must not render as "no classes assigned": flag it so the
      // roster area shows an error + retry instead of a false empty state.
      setSections([]);
      setSectionsLoadFailed(true);
    } finally {
      if (sectionsGenerationRef.current === generation) {
        setIsLoadingSections(false);
      }
    }
  }, [selectedSemester]);

  // 3. Fetch roster and existing attendance records for the selected section and date
  const fetchRosterAndAttendance = useCallback(async (sectionId: string, date: string) => {
    // Each new (sectionId, date) fetch starts a new generation and clears the
    // previous rows: leaving the old roster in place while a reload was in
    // flight let Save persist stale marks under the new selection.
    const generation = ++rosterGenerationRef.current;
    setRoster([]);
    setSectionSummary(null);
    setExistingReadFailed(false);
    if (!sectionId) {
      return;
    }

    setIsLoadingRoster(true);
    setError('');

    let existingReadFailedNow = false;
    try {
      const [rosterEntries, existingAttendance, summary] = await Promise.all([
        // The markable roster comes from the attendance API, not the grading
        // read model: PENDING enrollments are still attending students the
        // write path accepts, and the grades view deliberately hides them.
        attendanceApi.getSectionRoster(sectionId),
        // A failed read must NOT render as "nothing saved yet": defaulting a
        // full roster to PRESENT and saving would mass-overwrite real
        // ABSENT/LATE records. Flag it and lock the save instead.
        attendanceApi.getSectionAttendance(sectionId, date).catch(() => {
          existingReadFailedNow = true;
          return [] as Awaited<ReturnType<typeof attendanceApi.getSectionAttendance>>;
        }),
        attendanceApi.getSectionSummary(sectionId).catch(() => null),
      ]);
      if (rosterGenerationRef.current !== generation) return;
      setExistingReadFailed(existingReadFailedNow);

      const attendanceMap = new Map<string, { status: AttendanceStatus; notes: string | null }>();
      for (const record of existingAttendance) {
        if (record.studentId && record.status) {
          attendanceMap.set(record.studentId, {
            status: record.status as AttendanceStatus,
            notes: record.notes ?? null,
          });
        }
      }

      const rows: StudentRowState[] = rosterEntries.map((entry) => {
        const existing = attendanceMap.get(entry.studentId);
        return {
          studentId: entry.studentId,
          studentCode: entry.studentCode,
          studentName: `${entry.lastName} ${entry.firstName}`.trim(),
          status: existing ? existing.status : 'PRESENT', // default to PRESENT for convenient roll call
          notes: existing?.notes ?? '',
        };
      });

      setRoster(rows);
      setResetStudentIds(new Set());
      setSectionSummary(summary);
    } catch {
      if (rosterGenerationRef.current !== generation) return;
      setError(copy.rosterLoadErrorDescription);
    } finally {
      if (rosterGenerationRef.current === generation) {
        setIsLoadingRoster(false);
      }
    }
  }, [copy.rosterLoadErrorDescription]);

  useEffect(() => {
    if (hasAccess) {
      void fetchSemesters();
    }
  }, [fetchSemesters, hasAccess]);

  useEffect(() => {
    if (hasAccess) {
      void fetchSections();
    }
  }, [fetchSections, hasAccess]);

  useEffect(() => {
    if (hasAccess && selectedSectionId) {
      void fetchRosterAndAttendance(selectedSectionId, selectedDate);
    }
  }, [fetchRosterAndAttendance, hasAccess, selectedDate, selectedSectionId]);

  // Actions
  const handleStatusChange = (studentId: string, status: AttendanceStatus) => {
    setRoster((prev) =>
      prev.map((row) => (row.studentId === studentId ? { ...row, status } : row)),
    );
  };

  const handleNotesChange = (studentId: string, notes: string) => {
    setRoster((prev) =>
      prev.map((row) => (row.studentId === studentId ? { ...row, notes } : row)),
    );
  };

  const handleMarkAllPresent = () => {
    setRoster((prev) => prev.map((row) => ({ ...row, status: 'PRESENT' })));
    toast.info(copy.markAllPresent);
  };

  const handleResetStatuses = async () => {
    const hasMarks = roster.some((row) => row.status !== null || row.notes.trim() !== '');
    if (!hasMarks) return;
    const shouldReset = await confirm({
      title: copy.resetTitle,
      message: copy.resetMessage,
      confirmText: copy.resetConfirm,
      cancelText: messages.common.actions.cancel,
      variant: 'destructive',
    });
    if (!shouldReset) return;
    // Remember which rows had marks so handleSave can send RESET entries for
    // them — otherwise the backend kept the stale row and the reset silently
    // no-opped (round-2 sweep finding write-1).
    setResetStudentIds(
      new Set(
        roster
          .filter((row) => row.status !== null || row.notes.trim() !== '')
          .map((row) => row.studentId),
      ),
    );
    setRoster((prev) => prev.map((row) => ({ ...row, status: null, notes: '' })));
  };

  const handleSave = async () => {
    // Belt for the disabled buttons: never save while a roster fetch is in
    // flight (the rows on screen may belong to a previous section/date) and
    // never overwrite a section whose saved attendance could not be read back
    // (would mass-reset real records).
    if (!selectedSectionId || roster.length === 0 || isLoadingRoster) return;
    if (existingReadFailed) {
      toast.error(copy.existingReadWarning);
      return;
    }

    const marked = roster
      .filter((row): row is StudentRowState & { status: AttendanceStatus } => row.status !== null)
      .map((row) => ({
        studentId: row.studentId,
        status: row.status,
        notes: row.notes.trim() || undefined,
      }));
    // Rows cleared via Reset but not yet re-marked travel as RESET entries so
    // the backend deletes their stale records instead of silently keeping them.
    const resets = roster
      .filter((row) => row.status === null && resetStudentIds.has(row.studentId))
      .map((row) => ({ studentId: row.studentId, status: 'RESET' as const }));

    const records: StudentAttendanceEntry[] = [...marked, ...resets];

    if (records.length === 0) {
      toast.warning(copy.noStatusSelected);
      return;
    }

    setIsSaving(true);
    try {
      const response = await attendanceApi.saveSectionAttendance(selectedSectionId, {
        date: selectedDate,
        records,
      });

      toast.success(
        copy.savedSuccess.replace('{count}', String(response.updatedCount)),
      );
      setResetStudentIds(new Set());

      // Refresh section summary after saving
      const summary = await attendanceApi.getSectionSummary(selectedSectionId).catch(() => null);
      if (summary) setSectionSummary(summary);
    } catch {
      toast.error(copy.saveFailed);
    } finally {
      setIsSaving(false);
    }
  };

  const selectedSection = useMemo(
    () => sections.find((s) => s.sectionId === selectedSectionId),
    [sections, selectedSectionId],
  );

  const presentCount = roster.filter((r) => r.status === 'PRESENT').length;
  const absentCount = roster.filter((r) => r.status === 'ABSENT').length;
  const lateCount = roster.filter((r) => r.status === 'LATE').length;
  const excusedCount = roster.filter((r) => r.status === 'EXCUSED').length;

  const handleExportCsv = useCallback(() => {
    if (roster.length === 0) {
      toast.info(copy.exportCsvEmpty);
      return;
    }
    const currentSemester = semesters.find((s) => s.id === selectedSemester);
    const csvContent = generateAttendanceCsv({
      sectionNumber: selectedSection?.sectionNumber || selectedSectionId,
      courseCode: selectedSection?.courseCode,
      courseName: selectedSection?.courseName,
      date: selectedDate,
      semesterName: currentSemester ? getLocalizedName(locale, currentSemester, currentSemester.name) : '',
      metrics: {
        total: roster.length,
        present: presentCount,
        absent: absentCount,
        late: lateCount,
        excused: excusedCount,
        rate: calculateAttendanceRate({ present: presentCount, late: lateCount, absent: absentCount }, 1) ?? 0,
      },
      students: roster.map((r) => ({
        studentId: r.studentId,
        studentCode: r.studentCode,
        studentName: r.studentName,
        status: r.status,
        notes: r.notes,
      })),
      locale,
    });

    const safeSecNum = (selectedSection?.sectionNumber || selectedSectionId).replace(/[^a-zA-Z0-9_-]/g, '_');
    const filename = `diem-danh-${safeSecNum}-${selectedDate}.csv`;
    downloadCsvFile(filename, csvContent);
    toast.success(copy.exportCsvSuccess);
  }, [
    absentCount,
    copy.exportCsvEmpty,
    copy.exportCsvSuccess,
    excusedCount,
    lateCount,
    locale,
    presentCount,
    roster,
    selectedDate,
    selectedSection,
    selectedSectionId,
    selectedSemester,
    semesters,
  ]);

  const handlePrint = useCallback(() => {
    window.print();
  }, []);

  if (authLoading) {
    return <LoadingState label={copy.loadingAuth} />;
  }

  if (!hasAccess) {
    return <WorkspaceForbiddenState />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
        actions={
          <div className="flex flex-wrap items-center gap-2 print:hidden">
            <Button
              type="button"
              variant="outline"
              onClick={handleExportCsv}
              disabled={isLoadingRoster || roster.length === 0}
              className="gap-2"
              title={copy.exportCsv}
            >
              <Download className="h-4 w-4 text-primary" aria-hidden="true" />
              <span>{copy.exportCsv}</span>
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={handlePrint}
              disabled={isLoadingRoster || roster.length === 0}
              className="gap-2"
              title={copy.printRoster}
            >
              <Printer className="h-4 w-4 text-primary" aria-hidden="true" />
              <span>{copy.printRoster}</span>
            </Button>
          </div>
        }
      />

      {/* Control bar: Semester, Section & Date */}
      <WorkspacePanel title={copy.selectSection} className="p-4 sm:p-5 print:hidden">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {/* Semester Filter */}
          <div className="space-y-1.5">
            <label htmlFor={`${controlId}-semester`} className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {copy.selectSemester}
            </label>
            <Select
              id={`${controlId}-semester`}
              value={selectedSemester}
              onChange={(e) => setSelectedSemester(e.target.value)}
              className="w-full"
            >
              <option value="">{copy.allSemesters}</option>
              {semesters.map((s) => (
                <option key={s.id} value={s.id}>
                  {getLocalizedName(locale, s, s.name)}
                </option>
              ))}
            </Select>
          </div>

          {/* Section Selector */}
          <div className="space-y-1.5">
            <label htmlFor={`${controlId}-section`} className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {copy.selectSection}
            </label>
            <Select
              id={`${controlId}-section`}
              value={selectedSectionId}
              onChange={(e) => setSelectedSectionId(e.target.value)}
              className="w-full font-medium"
              disabled={isLoadingSections || sections.length === 0}
            >
              {sections.length === 0 ? (
                <option value="">{copy.noSectionsTitle}</option>
              ) : (
                sections.map((s) => (
                  <option key={s.sectionId} value={s.sectionId}>
                    {s.courseCode} - {getLocalizedFlatLabel(locale, s.courseName, s.courseNameEn, s.courseNameVi)} ({s.sectionNumber})
                  </option>
                ))
              )}
            </Select>
          </div>

          {/* Date Picker */}
          <div className="space-y-1.5">
            <label htmlFor={`${controlId}-date`} className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {copy.dateLabel}
            </label>
            <Input
              id={`${controlId}-date`}
              type="date"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
              className="w-full"
            />
          </div>
        </div>
      </WorkspacePanel>

      {/* Metrics Row */}
      {selectedSectionId && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 sm:gap-4 print:hidden">
          <WorkspaceMetricCard
            label={copy.statsTotalStudents}
            value={formatNumber(roster.length)}
            icon={<Users className="h-5 w-5" aria-hidden="true" />}
            toneClassName={metricToneClass('info')}
          />
          <WorkspaceMetricCard
            label={copy.statuses.PRESENT}
            value={formatNumber(presentCount)}
            icon={<CheckCircle2 className="h-5 w-5" aria-hidden="true" />}
            toneClassName={metricToneClass('success')}
          />
          <WorkspaceMetricCard
            label={copy.statuses.ABSENT}
            value={formatNumber(absentCount)}
            detail={excusedCount > 0 ? `${excusedCount} ${copy.statExcusedSuffix}` : undefined}
            icon={<XCircle className="h-5 w-5" aria-hidden="true" />}
            toneClassName={metricToneClass('danger')}
          />
          <WorkspaceMetricCard
            label={copy.statsPresentRate}
            value={
              sectionSummary
                ? `${formatNumber(sectionSummary.attendanceRate)}%`
                : '—'
            }
            detail={sectionSummary ? `${sectionSummary.totalSessions} ${copy.statSessionsHeldSuffix}` : undefined}
            icon={<CalendarCheck className="h-5 w-5" aria-hidden="true" />}
            toneClassName={metricToneClass('info')}
          />
        </div>
      )}

      {/* Roster & Attendance Sheet */}
      {isLoadingSections ? (
        <LoadingState label={copy.loadingSections} />
      ) : sectionsLoadFailed ? (
        <ErrorState
          title={copy.sectionsLoadError}
          description={copy.sectionsLoadErrorDescription}
          onRetry={() => void fetchSections()}
        />
      ) : sections.length === 0 ? (
        <EmptyState
          icon={Users}
          title={copy.noSectionsTitle}
          description={copy.noSectionsDescription}
        />
      ) : isLoadingRoster ? (
        <LoadingState label={copy.loadingRoster} />
      ) : error ? (
        <ErrorState
          title={copy.rosterLoadError}
          description={error}
          onRetry={() => void fetchRosterAndAttendance(selectedSectionId, selectedDate)}
        />
      ) : roster.length === 0 ? (
        <EmptyState
          icon={Users}
          title={copy.noStudentsTitle}
          description={copy.noStudentsDescription}
        />
      ) : (
        <WorkspacePanel title={copy.sheetTitle} className="space-y-4 p-4 sm:p-6">
          {/* Printable Official Institutional Header */}
          <div className="hidden print:block mb-6 border-b-2 border-primary/40 pb-4 text-center">
            <div className="text-xs uppercase font-bold tracking-wider text-muted-foreground">
              {copy.printOfficialHeader}
            </div>
            <div className="text-sm font-extrabold text-foreground">
              {(locale === 'vi'
                ? selectedSection?.departmentNameVi
                : selectedSection?.departmentNameEn) ||
                selectedSection?.departmentName ||
                copy.printDepartment}
            </div>
            <h1 className="text-xl font-black text-primary mt-2 uppercase tracking-wide">
              {copy.printReportTitle}
            </h1>
            <div className="flex flex-wrap justify-center gap-6 mt-3 text-xs text-foreground font-medium">
              <span><strong>{locale === 'vi' ? 'Lớp học phần:' : 'Section:'}</strong> {selectedSection?.sectionNumber || selectedSectionId}</span>
              <span><strong>{locale === 'vi' ? 'Học phần:' : 'Course:'}</strong> {selectedSection?.courseName} ({selectedSection?.courseCode})</span>
              <span><strong>{copy.dateLabel}:</strong> {selectedDate}</span>
              <span><strong>{copy.statsTotalStudents}:</strong> {roster.length}</span>
              <span><strong>{copy.statsPresentRate}:</strong> {(calculateAttendanceRate({ present: presentCount, late: lateCount, absent: absentCount }, 1) ?? 0).toFixed(1)}%</span>
            </div>
          </div>

          {/* Quick action bar */}
          <div className="flex flex-wrap items-center justify-between gap-3 border-b pb-4 print:hidden">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <span className="font-semibold text-foreground">{formatNumber(roster.length)}</span> {copy.statsTotalStudents}
              <span>•</span>
              <span className="text-emerald-700 dark:text-emerald-400 font-medium">{formatNumber(presentCount)} {copy.statuses.PRESENT}</span>
              <span>•</span>
              <span className="text-rose-600 dark:text-rose-400 font-medium">{formatNumber(absentCount)} {copy.statuses.ABSENT}</span>
              {lateCount > 0 && <span className="text-amber-700 dark:text-amber-400 font-medium">• {formatNumber(lateCount)} {copy.statuses.LATE}</span>}
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={handleMarkAllPresent}
                className="text-xs"
              >
                <CheckCircle2 className="mr-1.5 h-3.5 w-3.5 text-emerald-600" />
                {copy.markAllPresent}
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={handleResetStatuses}
                className="text-xs text-muted-foreground"
              >
                <RefreshCw className="mr-1.5 h-3.5 w-3.5" />
                {copy.resetStatuses}
              </Button>
              <Button
                size="sm"
                onClick={handleSave}
                disabled={isSaving || existingReadFailed || isLoadingRoster}
                className="bg-primary text-primary-foreground font-semibold"
              >
                <Save className="mr-1.5 h-4 w-4" />
                {isSaving ? copy.savingButton : copy.saveButton}
              </Button>
            </div>
          </div>

          {existingReadFailed ? (
            <div
              role="alert"
              className="mb-3 rounded-md border border-amber-300/60 bg-amber-50 px-3 py-2 text-xs font-medium text-amber-900 dark:border-amber-500/40 dark:bg-amber-950/40 dark:text-amber-200"
            >
              {copy.existingReadWarning}
            </div>
          ) : null}

          {/* Roster Table */}
          <div className="overflow-x-auto rounded-lg border">
            <table className="w-full text-left text-sm">
              <thead className="bg-muted/50 text-xs uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="py-3 px-4 font-semibold w-12 text-center">#</th>
                  <th className="py-3 px-4 font-semibold w-32">{copy.columnStudentCode}</th>
                  <th className="py-3 px-4 font-semibold">{copy.columnStudent}</th>
                  <th className="py-3 px-4 font-semibold min-w-[280px]">{copy.columnStatus}</th>
                  <th className="py-3 px-4 font-semibold min-w-[200px]">{copy.columnNotes}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {roster.map((row, index) => {
                  return (
                    <tr
                      key={row.studentId}
                      className="hover:bg-muted/30 transition-colors"
                    >
                      <td className="py-3 px-4 text-center text-xs text-muted-foreground">
                        {index + 1}
                      </td>
                      <td className="py-3 px-4 font-mono text-xs font-semibold text-foreground">
                        {row.studentCode}
                      </td>
                      <td className="py-3 px-4 font-medium text-foreground">
                        {row.studentName}
                      </td>
                      <td className="py-3 px-4">
                        <span className="hidden print:inline-block font-medium text-xs">
                          {row.status ? copy.statuses[row.status] : '—'}
                        </span>
                        <div className="inline-flex rounded-lg border bg-muted/40 p-1 gap-1 print:hidden">
                          <button
                            type="button"
                            onClick={() => handleStatusChange(row.studentId, 'PRESENT')}
                            aria-pressed={row.status === 'PRESENT'}
                            className={`flex items-center gap-1 rounded px-2.5 py-1 text-xs font-medium ${
                              row.status === 'PRESENT'
                                ? 'bg-emerald-700 text-white shadow-sm'
                                : 'text-foreground hover:bg-accent'
                            }`}
                          >
                            <CheckCircle2 className="h-3.5 w-3.5" />
                            {copy.statuses.PRESENT}
                          </button>
                          <button
                            type="button"
                            onClick={() => handleStatusChange(row.studentId, 'ABSENT')}
                            aria-pressed={row.status === 'ABSENT'}
                            className={`flex items-center gap-1 rounded px-2.5 py-1 text-xs font-medium ${
                              row.status === 'ABSENT'
                                ? 'bg-rose-700 text-white shadow-sm'
                                : 'text-foreground hover:bg-accent'
                            }`}
                          >
                            <XCircle className="h-3.5 w-3.5" />
                            {copy.statuses.ABSENT}
                          </button>
                          <button
                            type="button"
                            onClick={() => handleStatusChange(row.studentId, 'LATE')}
                            aria-pressed={row.status === 'LATE'}
                            className={`flex items-center gap-1 rounded px-2.5 py-1 text-xs font-medium ${
                              row.status === 'LATE'
                                ? 'bg-amber-700 text-white shadow-sm'
                                : 'text-foreground hover:bg-accent'
                            }`}
                          >
                            <Clock className="h-3.5 w-3.5" />
                            {copy.statuses.LATE}
                          </button>
                          <button
                            type="button"
                            onClick={() => handleStatusChange(row.studentId, 'EXCUSED')}
                            aria-pressed={row.status === 'EXCUSED'}
                            className={`flex items-center gap-1 rounded px-2.5 py-1 text-xs font-medium ${
                              row.status === 'EXCUSED'
                                ? 'bg-sky-700 text-white shadow-sm'
                                : 'text-foreground hover:bg-accent'
                            }`}
                          >
                            <AlertCircle className="h-3.5 w-3.5" />
                            {copy.statuses.EXCUSED}
                          </button>
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <span className="hidden print:inline-block text-xs">
                          {row.notes || '—'}
                        </span>
                        <Input
                          type="text"
                          value={row.notes}
                          onChange={(e) => handleNotesChange(row.studentId, e.target.value)}
                          placeholder={copy.notesPlaceholder}
                          aria-label={`${copy.notesPlaceholder} — ${row.studentName}`}
                          className="h-8 text-xs print:hidden"
                        />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Bottom Save Bar */}
          <div className="flex justify-end pt-2 print:hidden">
            <Button
              onClick={handleSave}
              disabled={isSaving || existingReadFailed || isLoadingRoster}
              className="bg-primary text-primary-foreground font-semibold px-6"
            >
              <Save className="mr-2 h-4 w-4" />
              {isSaving ? copy.savingButton : copy.saveButton}
            </Button>
          </div>

          {/* Official Signature Block - Visible ONLY when printing */}
          <div className="hidden print:flex justify-end mt-12 pr-8 text-center">
            <div className="space-y-16">
              <p className="text-xs text-muted-foreground italic">
                {locale === 'vi'
                  ? `TP. Hồ Chí Minh, ngày ${selectedDate.split('-')[2]} tháng ${selectedDate.split('-')[1]} năm ${selectedDate.split('-')[0]}`
                  : `Date: ${selectedDate}`}
              </p>
              <div className="space-y-1">
                <p className="text-sm font-bold text-foreground">{copy.printInstructorSignature}</p>
                <p className="text-xs text-muted-foreground italic">
                  ({locale === 'vi' ? 'Ký và ghi rõ họ tên' : 'Signature & full name'})
                </p>
              </div>
            </div>
          </div>
        </WorkspacePanel>
      )}
      {confirmationDialog}
    </div>
  );
}
