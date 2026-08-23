'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { BookOpen, Calendar, Clock, MapPin, Trash2 } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { enrollmentsApi } from '@/lib/api';
import { getLocalizedName } from '@/lib/academic-content';
import { LinkButton } from '@/components/ui/link-button';
import { Enrollment } from '@/types/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import { toast } from 'sonner';

const statusTone: Record<string, string> = {
  CONFIRMED: 'bg-emerald-500/12 text-emerald-600 dark:text-emerald-400',
  PENDING: 'bg-amber-500/12 text-amber-600 dark:text-amber-400',
  DROPPED: 'bg-rose-500/12 text-rose-600 dark:text-rose-400',
  COMPLETED: 'bg-blue-500/12 text-blue-600 dark:text-blue-400',
};

function getDayName(day: number, locale: 'en' | 'vi') {
  const names =
    locale === 'vi'
      ? [
          'Chủ nhật',
          'Thứ hai',
          'Thứ ba',
          'Thứ tư',
          'Thứ năm',
          'Thứ sáu',
          'Thứ bảy',
        ]
      : [
          'Sunday',
          'Monday',
          'Tuesday',
          'Wednesday',
          'Thursday',
          'Friday',
          'Saturday',
        ];

  return names[day] ?? '';
}

export default function EnrollmentsPage() {
  const { user } = useAuth();
  const { locale, formatDate, formatNumber } = useI18n();
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [isDropping, setIsDropping] = useState<string | null>(null);
  const { confirm, confirmationDialog } = useConfirmationDialog();

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Workspace sinh viên',
          title: 'Môn học của tôi',
          description:
            'Theo dõi trạng thái section, xem lịch học và xử lý thay đổi enrollment mà không rời khỏi workspace dùng chung.',
          browseSections: 'Xem các section',
          loading: 'Đang tải đăng ký học',
          unavailableTitle: 'Đăng ký học chưa sẵn sàng',
          emptyTitle: 'Chưa có môn học',
          emptyDescription:
            'Khi bạn đăng ký vào một section, lịch học và chi tiết section sẽ xuất hiện ở đây.',
          coursesInView: 'Môn học trong màn hình',
          confirmed: 'Đã xác nhận',
          pending: 'Đang chờ',
          recordTitle: 'Hồ sơ đăng ký',
          sectionPrefix: 'Section',
          enrolledOn: 'Đăng ký ngày',
          credits: 'tín chỉ',
          unknownCourse: 'Môn học',
          unavailableCourseName: 'Chưa có tên môn',
          unknownSection: 'Chưa rõ section',
          dropCourse: 'Hủy môn học',
          droppingCourse: 'Đang hủy môn',
          confirmTitle: 'Hủy môn học',
          confirmMessage: (courseLabel: string) =>
            `Hủy ${courseLabel}? Hành động này giữ cho việc đổi lịch học luôn rõ ràng và có chủ đích.`,
          dropped: 'Đã hủy môn học',
          dropFailed: 'Hiện chưa thể hủy môn học này.',
          studentProfileMissing:
            'Không tìm thấy hồ sơ sinh viên trong phiên hiện tại.',
          loadFailed: 'Hiện chưa thể tải danh sách đăng ký học của bạn.',
        }
      : {
          eyebrow: 'Student workspace',
          title: 'My courses',
          description:
            'Track section status, review schedules, and make enrollment changes without leaving the shared workspace.',
          browseSections: 'Browse available sections',
          loading: 'Loading enrollments',
          unavailableTitle: 'Enrollments unavailable',
          emptyTitle: 'No courses yet',
          emptyDescription:
            'Once you enroll in a section, the current schedule and section details will appear here.',
          coursesInView: 'Courses in view',
          confirmed: 'Confirmed',
          pending: 'Pending',
          recordTitle: 'Enrollment record',
          sectionPrefix: 'Section',
          enrolledOn: 'Enrolled',
          credits: 'credits',
          unknownCourse: 'Course',
          unavailableCourseName: 'Unavailable',
          unknownSection: 'Unknown section',
          dropCourse: 'Drop course',
          droppingCourse: 'Dropping course',
          confirmTitle: 'Drop course',
          confirmMessage: (courseLabel: string) =>
            `Drop ${courseLabel}? This keeps the action explicit and prevents accidental schedule changes.`,
          dropped: 'Course dropped',
          dropFailed: 'We could not drop this course.',
          studentProfileMissing:
            'Your student profile is not available in this session.',
          loadFailed: 'Your current enrollments could not be loaded.',
        };

  const fetchEnrollments = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const data = await enrollmentsApi.getMyEnrollments();
      setEnrollments(data);
    } catch {
      setError(copy.loadFailed);
    } finally {
      setIsLoading(false);
    }
  }, [copy.loadFailed]);

  useEffect(() => {
    void fetchEnrollments();
  }, [fetchEnrollments]);

  const handleDrop = async (enrollmentId: string, courseLabel: string) => {
    if (!user?.studentId) {
      toast.error(copy.studentProfileMissing);
      return;
    }

    const shouldContinue = await confirm({
      title: copy.confirmTitle,
      message: copy.confirmMessage(courseLabel),
      confirmText: copy.dropCourse,
      variant: 'destructive',
    });

    if (!shouldContinue) {
      return;
    }

    setIsDropping(enrollmentId);
    try {
      await enrollmentsApi.drop(enrollmentId);
      toast.success(copy.dropped);
      await fetchEnrollments();
    } catch (error: any) {
      toast.error(error.response?.data?.message || copy.dropFailed);
    } finally {
      setIsDropping(null);
    }
  };

  const confirmedCourses = enrollments.filter(
    (enrollment) => enrollment.status === 'CONFIRMED',
  );
  const pendingCourses = enrollments.filter(
    (enrollment) => enrollment.status === 'PENDING',
  );

  const summaryCards = useMemo(
    () => [
      {
        label: copy.coursesInView,
        value: formatNumber(enrollments.length),
        tone: 'bg-blue-500/12 text-blue-600 dark:text-blue-400',
      },
      {
        label: copy.confirmed,
        value: formatNumber(confirmedCourses.length),
        tone: 'bg-emerald-500/12 text-emerald-600 dark:text-emerald-400',
      },
      {
        label: copy.pending,
        value: formatNumber(pendingCourses.length),
        tone: 'bg-amber-500/12 text-amber-600 dark:text-amber-400',
      },
    ],
    [
      confirmedCourses.length,
      copy.confirmed,
      copy.coursesInView,
      copy.pending,
      enrollments.length,
      formatNumber,
      pendingCourses.length,
    ],
  );

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
        actions={
          <LinkButton href="/dashboard/register">{copy.browseSections}</LinkButton>
        }
      />

      {error ? (
        <ErrorState
          title={copy.unavailableTitle}
          description={error}
          onRetry={() => void fetchEnrollments()}
        />
      ) : isLoading ? (
        <LoadingState label={copy.loading} />
      ) : enrollments.length === 0 ? (
        <EmptyState
          icon={BookOpen}
          title={copy.emptyTitle}
          description={copy.emptyDescription}
          action={
            <LinkButton href="/dashboard/register">{copy.browseSections}</LinkButton>
          }
        />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-3">
            {summaryCards.map((item) => (
              <Card key={item.label} variant="elevated">
                <CardContent className="flex items-center justify-between gap-4 pt-6">
                  <div>
                    <div className="text-sm text-muted-foreground">{item.label}</div>
                    <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                      {item.value}
                    </div>
                  </div>
                  <div
                    className={`flex h-11 w-11 items-center justify-center rounded-lg ${item.tone}`}
                  >
                    <BookOpen className="h-5 w-5" />
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>

          <Card variant="muted">
            <CardHeader>
              <CardTitle className="text-xl">{copy.recordTitle}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {enrollments.map((enrollment) => {
                const courseCode = enrollment.section?.course?.code ?? copy.unknownCourse;
                const courseName = getLocalizedName(
                  locale,
                  enrollment.section?.course,
                  enrollment.section?.course?.name ?? copy.unavailableCourseName,
                );
                const courseLabel = `${courseCode} ${courseName}`.trim();

                return (
                  <div
                    key={enrollment.id}
                    className="rounded-lg border border-border/70 bg-card px-5 py-5"
                  >
                    <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                      <div className="space-y-3">
                        <div className="flex flex-wrap items-center gap-2">
                          <h2 className="text-lg font-semibold text-foreground">
                            {courseCode} - {courseName}
                          </h2>
                          <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                            {copy.sectionPrefix}{' '}
                            {enrollment.section?.sectionNumber || copy.unknownSection}
                          </span>
                          <span
                            className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                              statusTone[enrollment.status] ??
                              'bg-secondary text-foreground'
                            }`}
                          >
                            {enrollment.status}
                          </span>
                        </div>

                        <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
                          <span className="inline-flex items-center gap-2">
                            <Calendar className="h-4 w-4" />
                            {copy.enrolledOn} {formatDate(enrollment.enrolledAt)}
                          </span>
                          <span>
                            {enrollment.section?.course?.credits} {copy.credits}
                          </span>
                          {enrollment.section?.lecturer ? (
                            <span>
                              {enrollment.section.lecturer.user?.firstName}{' '}
                              {enrollment.section.lecturer.user?.lastName}
                            </span>
                          ) : null}
                          {enrollment.section?.classroom ? (
                            <span className="inline-flex items-center gap-2">
                              <MapPin className="h-4 w-4" />
                              {enrollment.section.classroom.building}{' '}
                              {enrollment.section.classroom.roomNumber}
                            </span>
                          ) : null}
                        </div>

                        {enrollment.section?.schedules?.length ? (
                          <div className="flex flex-wrap gap-2">
                            {enrollment.section.schedules.map((schedule, index) => (
                              <span
                                key={`${enrollment.id}-${index}`}
                                className="inline-flex items-center gap-2 rounded-full bg-secondary px-3 py-1 text-xs text-foreground"
                              >
                                <Clock className="h-3.5 w-3.5" />
                                {getDayName(schedule.dayOfWeek, locale)}{' '}
                                {schedule.startTime}-
                                {schedule.endTime}
                              </span>
                            ))}
                          </div>
                        ) : null}
                      </div>

                      {enrollment.status !== 'DROPPED' &&
                      enrollment.status !== 'COMPLETED' ? (
                        <Button
                          type="button"
                          variant="destructive"
                          onClick={() => void handleDrop(enrollment.id, courseLabel)}
                          disabled={isDropping === enrollment.id}
                        >
                          <Trash2 className="mr-2 h-4 w-4" />
                          {isDropping === enrollment.id
                            ? copy.droppingCourse
                            : copy.dropCourse}
                        </Button>
                      ) : null}
                    </div>
                  </div>
                );
              })}
            </CardContent>
          </Card>
        </>
      )}

      {confirmationDialog}
    </div>
  );
}
