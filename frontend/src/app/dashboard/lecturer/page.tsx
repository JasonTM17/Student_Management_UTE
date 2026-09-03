'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Bell, Calendar, FileText, Users } from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { announcementsApi, sectionsApi } from '@/lib/api';
import { useOrderedPosts } from '@/components/providers/SiteAppearanceProvider';
import { getLocalizedFlatLabel } from '@/lib/academic-content';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { GradingSection, LecturerSection } from '@/types/api';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { metricToneClass } from '@/components/ui/status';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import {
  WorkspaceActionTile,
  WorkspaceMetricCard,
  WorkspacePanel,
} from '@/components/dashboard/WorkspaceSurface';
import { useI18n } from '@/i18n';

type LecturerAnnouncement = {
  id: string;
  title: string;
  content: string;
  createdAt: string;
  priority: string;
};

const quickLinks = [
  {
    href: '/dashboard/lecturer/schedule',
    icon: Calendar,
    tone: metricToneClass('info'),
  },
  {
    href: '/dashboard/lecturer/grades',
    icon: FileText,
    tone: metricToneClass('neutral'),
  },
  {
    href: '/dashboard/lecturer/announcements',
    icon: Bell,
    tone: metricToneClass('warning'),
  },
];

export default function LecturerDashboardPage() {
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth([
    'LECTURER',
  ]);
  const { locale, formatDateTime, formatNumber, messages } = useI18n();
  const [scheduleSections, setScheduleSections] = useState<LecturerSection[]>([]);
  const [gradingSections, setGradingSections] = useState<GradingSection[]>([]);
  const [announcements, setAnnouncements] = useState<LecturerAnnouncement[]>([]);
  const orderedAnnouncements = useOrderedPosts(announcements);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const [scheduleData, gradingData, announcementsData] = await Promise.all([
        sectionsApi.getMySchedule(),
        sectionsApi.getMyGradingSections(),
        announcementsApi.getMy({ page: 1, limit: 5 }),
      ]);

      setScheduleSections(scheduleData);
      setGradingSections(gradingData);
      setAnnouncements(announcementsData.data ?? []);
    } catch {
      setError(messages.lecturerDashboard.errors.loadFailed);
    } finally {
      setIsLoading(false);
    }
  }, [messages.lecturerDashboard.errors.loadFailed]);

  useEffect(() => {
    if (hasAccess) {
      void fetchData();
    }
  }, [fetchData, hasAccess]);

  const totalStudents = useMemo(() => {
    return scheduleSections.reduce(
      (sum, section) => sum + (section.enrolledCount ?? 0),
      0,
    );
  }, [scheduleSections]);

  const readyToPublish = useMemo(() => {
    return gradingSections.filter((section) => section.canPublish).length;
  }, [gradingSections]);
  const statusLabel = (status: string) =>
    messages.common.statuses[status.toUpperCase() as keyof typeof messages.common.statuses] ??
    messages.common.statuses.UNKNOWN;

  if (authLoading) {
    return <LoadingState label={messages.lecturerDashboard.errors.loading} />;
  }

  if (!hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.lecturerDashboard.eyebrow}</SectionEyebrow>}
        title={messages.lecturerDashboard.title.replace('{name}', user?.firstName ?? 'lecturer')}
        description={messages.lecturerDashboard.description}
      />

      {error ? (
        <ErrorState
          title={messages.lecturerDashboard.errors.unavailableTitle}
          description={error}
          onRetry={() => void fetchData()}
        />
      ) : isLoading ? (
        <LoadingState label={messages.lecturerDashboard.errors.loading} />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-4">
            <WorkspaceMetricCard
              label={messages.lecturerDashboard.metrics.labels[0]}
              value={formatNumber(scheduleSections.length)}
              icon={<Calendar className="h-5 w-5" />}
              detail={messages.lecturerDashboard.metrics.details[0]}
              toneClassName={metricToneClass('info')}
            />
            <WorkspaceMetricCard
              label={messages.lecturerDashboard.metrics.labels[1]}
              value={formatNumber(totalStudents)}
              icon={<Users className="h-5 w-5" />}
              detail={messages.lecturerDashboard.metrics.details[1]}
              toneClassName={metricToneClass('success')}
            />
            <WorkspaceMetricCard
              label={messages.lecturerDashboard.metrics.labels[2]}
              value={formatNumber(readyToPublish)}
              icon={<FileText className="h-5 w-5" />}
              detail={messages.lecturerDashboard.metrics.details[2]}
              toneClassName={metricToneClass('neutral')}
            />
            <WorkspaceMetricCard
              label={messages.lecturerDashboard.metrics.labels[3]}
              value={formatNumber(announcements.length)}
              icon={<Bell className="h-5 w-5" />}
              detail={messages.lecturerDashboard.metrics.details[3]}
              toneClassName={metricToneClass('warning')}
            />
          </div>

          <div className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
            <WorkspacePanel
              title={messages.lecturerDashboard.quickActionsTitle}
              description={messages.lecturerDashboard.quickActionsDescription}
              variant="muted"
              contentClassName="flex flex-col gap-3"
            >
                {quickLinks.map((item, index) => (
                  <WorkspaceActionTile
                    key={item.href}
                    href={item.href}
                    icon={<item.icon className="h-5 w-5" />}
                    title={messages.lecturerDashboard.quickLinks[index][0]}
                    description={messages.lecturerDashboard.quickLinks[index][1]}
                    toneClassName={item.tone}
                    ctaLabel={messages.common.actions.openTool}
                  />
                ))}
            </WorkspacePanel>

            <WorkspacePanel
              title={messages.lecturerDashboard.gradingQueueTitle}
              description={messages.lecturerDashboard.gradingQueueDescription}
              contentClassName="space-y-3"
            >
                {gradingSections.length === 0 ? (
                  <EmptyState
                    icon={FileText}
                    title={messages.lecturerDashboard.gradingQueueEmptyTitle}
                    description={messages.lecturerDashboard.gradingQueueEmptyDescription}
                    className="min-h-[240px] border-none bg-transparent px-0 py-0"
                  />
                ) : (
                  gradingSections.slice(0, 4).map((section) => (
                    <div
                      key={section.id}
                      className="rounded-lg border border-border/70 bg-card px-4 py-4"
                    >
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                          <div className="font-medium text-foreground">
                            {section.courseCode} - {getLocalizedFlatLabel(
                              locale,
                              section.courseName,
                              section.courseNameEn,
                              section.courseNameVi,
                              section.courseName,
                            )}
                          </div>
                          <div className="mt-1 text-sm text-muted-foreground">
                            {messages.lecturerDashboard.sectionPrefix} {section.sectionNumber} - {getLocalizedFlatLabel(
                              locale,
                              section.semester,
                              section.semesterNameEn,
                              section.semesterNameVi,
                              section.semester,
                            )}
                          </div>
                        </div>
                        <div className="text-sm text-muted-foreground sm:text-right">
                          <div>{section.gradedCount}/{section.enrolledCount} {messages.lecturerDashboard.gradedSuffix}</div>
                          <div className="mt-1">
                            <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                              {section.canPublish ? messages.lecturerDashboard.queueStatusReady : messages.lecturerDashboard.queueStatusProgress}
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>
                  ))
                )}
            </WorkspacePanel>
          </div>

          <div className="grid gap-6 lg:grid-cols-[1.05fr_0.95fr]">
            <WorkspacePanel
              title={messages.lecturerDashboard.sectionsInScopeTitle}
              description={messages.lecturerDashboard.sectionsInScopeDescription}
              contentClassName="space-y-3"
            >
                {scheduleSections.length === 0 ? (
                  <EmptyState
                    icon={Calendar}
                    title={messages.lecturerDashboard.sectionsInScopeEmptyTitle}
                    description={messages.lecturerDashboard.sectionsInScopeEmptyDescription}
                    className="min-h-[240px] border-none bg-transparent px-0 py-0"
                  />
                ) : (
                  scheduleSections.slice(0, 5).map((section) => (
                    <div
                      key={section.id}
                      className="rounded-lg border border-border/70 bg-card px-4 py-4"
                    >
                      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                          <div className="font-medium text-foreground">
                            {section.courseCode} - {getLocalizedFlatLabel(
                              locale,
                              section.courseName,
                              section.courseNameEn,
                              section.courseNameVi,
                              section.courseName,
                            )}
                          </div>
                          <div className="mt-1 text-sm text-muted-foreground">
                            Section {section.sectionNumber} - {getLocalizedFlatLabel(
                              locale,
                              section.departmentName,
                              section.departmentNameEn,
                              section.departmentNameVi,
                              section.departmentName,
                            )}
                          </div>
                        </div>
                        <div className="text-sm text-muted-foreground sm:text-right">
                          <div>{section.enrolledCount}/{section.capacity} {messages.lecturerDashboard.studentsSuffix}</div>
                          <div className="mt-1">{statusLabel(section.status)}</div>
                        </div>
                      </div>
                    </div>
                  ))
                )}
            </WorkspacePanel>

            <WorkspacePanel
              title={messages.lecturerDashboard.announcementsTitle}
              description={messages.lecturerDashboard.announcementsDescription}
              variant="muted"
              contentClassName="space-y-3"
            >
                {announcements.length === 0 ? (
                  <EmptyState
                    icon={Bell}
                    title={messages.lecturerDashboard.announcementsEmptyTitle}
                    description={messages.lecturerDashboard.announcementsEmptyDescription}
                    className="min-h-[240px] border-none bg-transparent px-0 py-0"
                  />
                ) : (
                  orderedAnnouncements.map((announcement) => (
                    <div
                      key={announcement.id}
                      className="rounded-lg border border-border/70 bg-card px-4 py-4"
                    >
                      <div className="font-medium text-foreground">
                        {announcement.title}
                      </div>
                      <p className="mt-2 text-sm leading-6 text-muted-foreground">
                        {announcement.content}
                      </p>
                      <div className="mt-3 text-xs uppercase tracking-[0.16em] text-muted-foreground">
                        {formatDateTime(announcement.createdAt)}
                      </div>
                    </div>
                  ))
                )}
            </WorkspacePanel>
          </div>
        </>
      )}
    </div>
  );
}
