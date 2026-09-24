'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AlertCircle,
  CalendarCheck,
  CheckCircle2,
  Clock,
  RefreshCw,
  Save,
  Users,
  XCircle,
} from 'lucide-react';
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
import { GradingSection, SectionGrades, Semester } from '@/types/api';
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

  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [sections, setSections] = useState<GradingSection[]>([]);
  const [selectedSectionId, setSelectedSectionId] = useState('');
  const [selectedDate, setSelectedDate] = useState<string>(getTodayString);

  const [isLoadingSections, setIsLoadingSections] = useState(true);
  const [isLoadingRoster, setIsLoadingRoster] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');

  const [roster, setRoster] = useState<StudentRowState[]>([]);
  const [sectionSummary, setSectionSummary] = useState<SectionAttendanceSummary | null>(null);

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
    setIsLoadingSections(true);
    setError('');
    try {
      const data = await sectionsApi.getMyGradingSections(
        selectedSemester || undefined,
      );
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
      setError(copy.saveFailed);
    } finally {
      setIsLoadingSections(false);
    }
  }, [copy.saveFailed, selectedSemester]);

  // 3. Fetch roster and existing attendance records for the selected section and date
  const fetchRosterAndAttendance = useCallback(async (sectionId: string, date: string) => {
    if (!sectionId) {
      setRoster([]);
      setSectionSummary(null);
      return;
    }

    setIsLoadingRoster(true);
    setError('');

    try {
      const [gradesData, existingAttendance, summary] = await Promise.all([
        sectionsApi.getSectionGrades(sectionId),
        attendanceApi.getSectionAttendance(sectionId, date).catch(() => []),
        attendanceApi.getSectionSummary(sectionId).catch(() => null),
      ]);

      const attendanceMap = new Map<string, { status: AttendanceStatus; notes: string | null }>();
      for (const record of existingAttendance) {
        if (record.studentId && record.status) {
          attendanceMap.set(record.studentId, {
            status: record.status as AttendanceStatus,
            notes: record.notes ?? null,
          });
        }
      }

      const grades = gradesData as SectionGrades;
      const activeEnrollments = (grades?.enrollments ?? []).filter(
        (e: SectionGrades['enrollments'][number]) => e.enrollmentStatus !== 'DROPPED' && e.enrollmentStatus !== 'CANCELLED',
      );

      const rows: StudentRowState[] = activeEnrollments.map((enrollment: SectionGrades['enrollments'][number]) => {
        const existing = attendanceMap.get(enrollment.studentId);
        return {
          studentId: enrollment.studentId,
          studentCode: enrollment.studentCode,
          studentName: enrollment.studentName,
          status: existing ? existing.status : 'PRESENT', // default to PRESENT for convenient roll call
          notes: existing?.notes ?? '',
        };
      });

      setRoster(rows);
      setSectionSummary(summary);
    } catch {
      setError(copy.saveFailed);
    } finally {
      setIsLoadingRoster(false);
    }
  }, [copy.saveFailed]);

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

  const handleResetStatuses = () => {
    setRoster((prev) => prev.map((row) => ({ ...row, status: null, notes: '' })));
  };

  const handleSave = async () => {
    if (!selectedSectionId || roster.length === 0) return;

    const records: StudentAttendanceEntry[] = roster
      .filter((row): row is StudentRowState & { status: AttendanceStatus } => row.status !== null)
      .map((row) => ({
        studentId: row.studentId,
        status: row.status,
        notes: row.notes.trim() || undefined,
      }));

    if (records.length === 0) {
      toast.warning('Vui lòng chọn trạng thái điểm danh cho ít nhất 1 sinh viên');
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

      // Refresh section summary after saving
      const summary = await attendanceApi.getSectionSummary(selectedSectionId).catch(() => null);
      if (summary) setSectionSummary(summary);
    } catch {
      toast.error(copy.saveFailed);
    } finally {
      setIsSaving(false);
    }
  };

  if (authLoading) {
    return <LoadingState label="Đang xác thực thông tin giảng viên..." />;
  }

  if (!hasAccess) {
    return <WorkspaceForbiddenState />;
  }

  const markedCount = roster.filter((r) => r.status !== null).length;
  const presentCount = roster.filter((r) => r.status === 'PRESENT').length;
  const absentCount = roster.filter((r) => r.status === 'ABSENT').length;
  const lateCount = roster.filter((r) => r.status === 'LATE').length;
  const excusedCount = roster.filter((r) => r.status === 'EXCUSED').length;

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
      />

      {/* Control bar: Semester, Section & Date */}
      <WorkspacePanel title={copy.selectSection} className="p-4 sm:p-5">
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {/* Semester Filter */}
          <div className="space-y-1.5">
            <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {copy.selectSemester}
            </label>
            <Select
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
            <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {copy.selectSection}
            </label>
            <Select
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
            <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {copy.dateLabel}
            </label>
            <Input
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
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4 sm:gap-4">
          <WorkspaceMetricCard
            label={copy.statsTotalStudents}
            value={formatNumber(roster.length)}
            icon={<Users className="h-5 w-5" aria-hidden="true" />}
            toneClassName={metricToneClass('info')}
          />
          <WorkspaceMetricCard
            label={copy.statuses.PRESENT}
            value={formatNumber(presentCount)}
            detail={markedCount > 0 ? `${Math.round((presentCount / markedCount) * 100)}% hôm nay` : undefined}
            icon={<CheckCircle2 className="h-5 w-5" aria-hidden="true" />}
            toneClassName={metricToneClass('success')}
          />
          <WorkspaceMetricCard
            label={copy.statuses.ABSENT}
            value={formatNumber(absentCount)}
            detail={excusedCount > 0 ? `${excusedCount} có phép` : undefined}
            icon={<XCircle className="h-5 w-5" aria-hidden="true" />}
            toneClassName={metricToneClass('danger')}
          />
          <WorkspaceMetricCard
            label={copy.statsPresentRate}
            value={
              sectionSummary
                ? `${formatNumber(sectionSummary.attendanceRate)}%`
                : '100%'
            }
            detail={sectionSummary ? `${sectionSummary.totalSessions} buổi đã học` : undefined}
            icon={<CalendarCheck className="h-5 w-5" aria-hidden="true" />}
            toneClassName={metricToneClass('info')}
          />
        </div>
      )}

      {/* Roster & Attendance Sheet */}
      {isLoadingSections ? (
        <LoadingState label={copy.loadingSections} />
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
          title="Không thể tải danh sách điểm danh"
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
        <WorkspacePanel title="Bảng điểm danh lớp học phần" className="space-y-4 p-4 sm:p-6">
          {/* Quick action bar */}
          <div className="flex flex-wrap items-center justify-between gap-3 border-b pb-4">
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <span className="font-semibold text-foreground">{roster.length}</span> sinh viên
              <span>•</span>
              <span className="text-emerald-600 font-medium">{presentCount} có mặt</span>
              <span>•</span>
              <span className="text-rose-600 font-medium">{absentCount} vắng</span>
              {lateCount > 0 && <span className="text-amber-600 font-medium">• {lateCount} trễ</span>}
            </div>

            <div className="flex items-center gap-2">
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
                disabled={isSaving}
                className="bg-primary text-primary-foreground font-semibold"
              >
                <Save className="mr-1.5 h-4 w-4" />
                {isSaving ? copy.savingButton : copy.saveButton}
              </Button>
            </div>
          </div>

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
                        <div className="inline-flex rounded-lg border bg-muted/40 p-1 gap-1">
                          <button
                            type="button"
                            onClick={() => handleStatusChange(row.studentId, 'PRESENT')}
                            className={`flex items-center gap-1 rounded px-2.5 py-1 text-xs font-medium transition-colors ${
                              row.status === 'PRESENT'
                                ? 'bg-emerald-600 text-white shadow-sm'
                                : 'text-muted-foreground hover:text-foreground'
                            }`}
                          >
                            <CheckCircle2 className="h-3.5 w-3.5" />
                            {copy.statuses.PRESENT}
                          </button>
                          <button
                            type="button"
                            onClick={() => handleStatusChange(row.studentId, 'ABSENT')}
                            className={`flex items-center gap-1 rounded px-2.5 py-1 text-xs font-medium transition-colors ${
                              row.status === 'ABSENT'
                                ? 'bg-rose-600 text-white shadow-sm'
                                : 'text-muted-foreground hover:text-foreground'
                            }`}
                          >
                            <XCircle className="h-3.5 w-3.5" />
                            {copy.statuses.ABSENT}
                          </button>
                          <button
                            type="button"
                            onClick={() => handleStatusChange(row.studentId, 'LATE')}
                            className={`flex items-center gap-1 rounded px-2.5 py-1 text-xs font-medium transition-colors ${
                              row.status === 'LATE'
                                ? 'bg-amber-600 text-white shadow-sm'
                                : 'text-muted-foreground hover:text-foreground'
                            }`}
                          >
                            <Clock className="h-3.5 w-3.5" />
                            {copy.statuses.LATE}
                          </button>
                          <button
                            type="button"
                            onClick={() => handleStatusChange(row.studentId, 'EXCUSED')}
                            className={`flex items-center gap-1 rounded px-2.5 py-1 text-xs font-medium transition-colors ${
                              row.status === 'EXCUSED'
                                ? 'bg-sky-600 text-white shadow-sm'
                                : 'text-muted-foreground hover:text-foreground'
                            }`}
                          >
                            <AlertCircle className="h-3.5 w-3.5" />
                            {copy.statuses.EXCUSED}
                          </button>
                        </div>
                      </td>
                      <td className="py-3 px-4">
                        <Input
                          type="text"
                          value={row.notes}
                          onChange={(e) => handleNotesChange(row.studentId, e.target.value)}
                          placeholder={copy.notesPlaceholder}
                          className="h-8 text-xs"
                        />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Bottom Save Bar */}
          <div className="flex justify-end pt-2">
            <Button
              onClick={handleSave}
              disabled={isSaving}
              className="bg-primary text-primary-foreground font-semibold px-6"
            >
              <Save className="mr-2 h-4 w-4" />
              {isSaving ? copy.savingButton : copy.saveButton}
            </Button>
          </div>
        </WorkspacePanel>
      )}
    </div>
  );
}
