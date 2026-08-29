'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { FileText, GraduationCap, Users } from 'lucide-react';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { useRequireAuth } from '@/context/AuthContext';
import { sectionsApi, semestersApi } from '@/lib/api';
import { getLocalizedFlatLabel, getLocalizedName } from '@/lib/academic-content';
import { GradingSection, Semester } from '@/types/api';
import { LinkButton } from '@/components/ui/link-button';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
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

export default function LecturerGradesPage() {
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['LECTURER']);
  const { locale, formatNumber, messages } = useI18n();
  const [sections, setSections] = useState<GradingSection[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchSemesters = useCallback(async () => {
    const response = await semestersApi.getAll();
    setSemesters(response.data ?? []);
  }, []);

  const fetchSections = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const data = await sectionsApi.getMyGradingSections(
        selectedSemester || undefined,
      );
      setSections(data);
    } catch {
      setError(messages.lecturerGrades.errors.loadFailed);
    } finally {
      setIsLoading(false);
    }
  }, [messages.lecturerGrades.errors.loadFailed, selectedSemester]);

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

  const selectedSemesterName = useMemo(() => {
    return (
      getLocalizedName(
        locale,
        semesters.find((semester) => semester.id === selectedSemester),
        messages.lecturerGrades.allSemesters,
      ) ??
      messages.lecturerGrades.allSemesters
    );
  }, [locale, messages.lecturerGrades.allSemesters, selectedSemester, semesters]);

  const publishReadyCount = sections.filter((section) => section.canPublish).length;
  const totalGradedEntries = sections.reduce(
    (sum, section) => sum + section.gradedCount,
    0,
  );

  if (authLoading) {
    return <LoadingState label={messages.lecturerGrades.errors.loading} />;
  }

  if (!hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.lecturerGrades.eyebrow}</SectionEyebrow>}
        title={messages.lecturerGrades.title}
        description={messages.lecturerGrades.description.replace('{semester}', selectedSemesterName)}
        actions={
          <div className="min-w-[220px]">
            <Select
              aria-label={messages.lecturerGrades.selectSemester}
              value={selectedSemester}
              onChange={(event) => setSelectedSemester(event.target.value)}
              options={[
                { value: '', label: messages.lecturerGrades.allSemestersOption },
                ...semesters.map((semester) => ({
                  value: semester.id,
                  label: getLocalizedName(locale, semester, semester.name),
                })),
              ]}
            />
          </div>
        }
      />

      {error ? (
        <ErrorState
          title={messages.lecturerGrades.errors.unavailableTitle}
          description={error}
          onRetry={() => void fetchSections()}
        />
      ) : isLoading ? (
        <LoadingState label={messages.lecturerGrades.errors.loading} />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-3">
            <WorkspaceMetricCard
              label={messages.lecturerGrades.labels.sections}
              value={formatNumber(sections.length)}
              icon={<FileText className="h-5 w-5" />}
              detail={messages.lecturerGrades.details[0]}
              toneClassName={metricToneClass('info')}
            />
            <WorkspaceMetricCard
              label={messages.lecturerGrades.labels.gradesCaptured}
              value={formatNumber(totalGradedEntries)}
              icon={<Users className="h-5 w-5" />}
              detail={messages.lecturerGrades.details[1]}
              toneClassName={metricToneClass('success')}
            />
            <WorkspaceMetricCard
              label={messages.lecturerGrades.labels.readyToPublish}
              value={formatNumber(publishReadyCount)}
              icon={<GraduationCap className="h-5 w-5" />}
              detail={messages.lecturerGrades.details[2]}
              toneClassName={metricToneClass('neutral')}
            />
          </div>

          <WorkspacePanel
            title={messages.lecturerGrades.queueTitle}
            description={messages.lecturerGrades.queueDescription}
            contentClassName="space-y-4"
          >
            {sections.length === 0 ? (
              <EmptyState
                icon={FileText}
                title={messages.lecturerGrades.emptyTitle}
                description={messages.lecturerGrades.emptyDescription}
                className="min-h-[260px] border-none bg-transparent px-0 py-0"
              />
            ) : (
              sections.map((section) => (
                <div
                  key={section.id}
                  className="rounded-lg border border-border/70 bg-card px-5 py-5"
                >
                  <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
                    <div className="space-y-3">
                      <div className="space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <h2 className="text-lg font-semibold text-foreground">
                            {section.courseCode} - {getLocalizedFlatLabel(
                              locale,
                              section.courseName,
                              section.courseNameEn,
                              section.courseNameVi,
                              section.courseName,
                            )}
                          </h2>
                          <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                            {messages.lecturerGrades.labels.sectionPrefix} {section.sectionNumber}
                          </span>
                        </div>
                        <p className="text-sm text-muted-foreground">
                          {getLocalizedFlatLabel(
                            locale,
                            section.departmentName,
                            section.departmentNameEn,
                            section.departmentNameVi,
                            section.departmentName,
                          )}{' '}
                          -{' '}
                          {getLocalizedFlatLabel(
                            locale,
                            section.semester,
                            section.semesterNameEn,
                            section.semesterNameVi,
                            section.semester,
                          )}
                        </p>
                      </div>

                      <div className="flex flex-wrap gap-3 text-sm text-muted-foreground">
                        <span>{section.credits} {messages.lecturerGrades.labels.credits}</span>
                        <span>{section.enrolledCount} {messages.lecturerGrades.labels.enrolled}</span>
                        <span>{section.gradedCount} {messages.lecturerGrades.labels.graded}</span>
                        <span>{section.publishedCount} {messages.lecturerGrades.labels.published}</span>
                      </div>
                    </div>

                    <div className="flex shrink-0 flex-col items-start gap-3 xl:min-w-[200px] xl:items-end">
                      <span
                        className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                          section.canPublish
                            ? metricToneClass('success')
                            : section.gradeStatus === 'PARTIAL'
                              ? metricToneClass('warning')
                              : metricToneClass('neutral')
                        }`}
                      >
                        {section.canPublish ? messages.lecturerGrades.labels.readyStatus : section.gradeStatus}
                      </span>
                      <LinkButton href={`/dashboard/lecturer/grades/${section.id}`}>
                        {section.gradedCount > 0 ? messages.lecturerGrades.labels.manageGrades : messages.lecturerGrades.labels.enterGrades}
                      </LinkButton>
                    </div>
                  </div>
                </div>
              ))
            )}
          </WorkspacePanel>
        </>
      )}
    </div>
  );
}
