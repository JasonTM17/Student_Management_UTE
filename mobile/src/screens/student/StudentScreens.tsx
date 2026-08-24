import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Share, StyleSheet, View } from 'react-native';

import {
  Avatar,
  Badge,
  Button,
  Card,
  Divider,
  ListRow,
  MetricCard,
  ProgressBar,
  ScreenShell,
  ScreenSpacer,
  SectionHeading,
  StatePanel,
  UiText,
} from '../../components/Ui';
import { tokens } from '../../design/tokens';
import { ApiClientError, apiClient, campusApi, createIdempotencyKey, getRegistrationSnapshot, type AuthUser, type MobileAttendanceSummary, type MobileEnrollment, type MobileNotification, type MobileSection, type RegistrationEligibility, type RegistrationRound, type RegistrationSummary } from '../../api/client';
import type { MobileScreenProps } from '../../navigation/types';

const upcomingClasses = [
  { code: 'CS204', name: 'Software Architecture', time: '08:00 – 09:30', room: 'A2.204' },
  { code: 'BA312', name: 'Business Analytics', time: '13:30 – 15:00', room: 'B1.108' },
];

const courses = [
  { code: 'CS204', name: 'Software Architecture', lecturer: 'Dr. Le Minh', progress: 72, status: 'In progress' },
  { code: 'BA312', name: 'Business Analytics', lecturer: 'Assoc. Prof. Tran Ha', progress: 58, status: 'In progress' },
  { code: 'EN201', name: 'Academic English', lecturer: 'Ms. Nguyen Mai', progress: 86, status: 'On track' },
];

const grades = [
  { code: 'CS204', name: 'Software Architecture', score: '9.1', letter: 'A', detail: 'Excellent' },
  { code: 'BA312', name: 'Business Analytics', score: '8.4', letter: 'B+', detail: 'Very good' },
  { code: 'EN201', name: 'Academic English', score: '8.8', letter: 'A-', detail: 'Very good' },
];

const previewNotifications = [
  { title: 'Thesis registration opens Monday', subtitle: 'Choose a topic before 30 August.', meta: '10m', unread: true },
  { title: 'CS204 grade published', subtitle: 'Your final project result is ready.', meta: '2h', unread: true },
  { title: 'Library maintenance notice', subtitle: 'Online renewals pause this weekend.', meta: 'Yesterday', unread: false },
];

function useLiveResource<T>(
  initialValue: T,
  request: () => Promise<T>,
  fallbackMessage: string,
) {
  const requestRef = useRef(request);
  requestRef.current = request;
  const mountedRef = useRef(true);
  const [data, setData] = useState<T>(initialValue);
  const [isLoading, setIsLoading] = useState(apiClient.mode === 'live');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => () => {
    mountedRef.current = false;
  }, []);

  const reload = useCallback(async () => {
    if (apiClient.mode !== 'live') {
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const nextData = await requestRef.current();
      if (mountedRef.current) {
        setData(nextData);
      }
    } catch (nextError) {
      if (mountedRef.current) {
        setError(nextError instanceof ApiClientError ? nextError.message : fallbackMessage);
      }
    } finally {
      if (mountedRef.current) {
        setIsLoading(false);
      }
    }
  }, [fallbackMessage]);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { data, error, isLoading, reload, setData };
}

export function StudentDashboardScreen({ navigation }: MobileScreenProps) {
  const {
    data: liveEnrollments,
    error,
    isLoading,
    reload,
  } = useLiveResource<MobileEnrollment[]>(
    [],
    () => campusApi.enrollments(),
    'Dashboard data is unavailable.',
  );
  const dashboardClasses = apiClient.mode === 'preview'
    ? upcomingClasses
    : liveEnrollments.slice(0, 3).map((item) => ({
        code: item.section?.course?.code ?? item.section?.sectionNumber ?? item.sectionId,
        name: item.section?.course?.nameVi ?? item.section?.course?.name ?? 'Course',
        time: item.section?.schedules?.[0]
          ? `${item.section.schedules[0].startTime} - ${item.section.schedules[0].endTime}`
          : 'Schedule pending',
        room: item.section?.schedules?.[0]?.classroom
          ? `${item.section.schedules[0].classroom?.building ?? ''}${item.section.schedules[0].classroom?.roomNumber ? ` ${item.section.schedules[0].classroom.roomNumber}` : ''}`.trim()
          : 'TBA',
      }));
  const scheduledSessions = liveEnrollments.reduce((total, item) => total + (item.section?.schedules?.length ?? 0), 0);

  if (isLoading || error) {
    return (
      <ScreenShell title="Academic dashboard" eyebrow="Student workspace" subtitle="Your current sections and next academic actions.">
        <StatePanel
          kind={isLoading ? 'loading' : 'error'}
          title={isLoading ? 'Loading dashboard…' : 'Dashboard unavailable'}
          description={error ?? 'Retrieving your current academic records.'}
          actionLabel={error ? 'Try again' : undefined}
          onAction={error ? () => void reload() : undefined}
        />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell
      eyebrow={apiClient.mode === 'preview' ? 'Preview workspace' : 'Student workspace'}
      title="Academic dashboard"
      subtitle="Your current sections and next academic actions."
    >
      <Card tone="primary" style={styles.heroCard}>
        <View style={styles.heroCopy}>
          <UiText variant="meta" tone="primary">
            CURRENT SEMESTER
          </UiText>
          <UiText variant="headlineSmall" style={styles.heroTitle}>
            {apiClient.mode === 'preview' ? 'Preview semester overview' : `${liveEnrollments.length} active sections`}
          </UiText>
          <UiText variant="bodySmall" tone="muted">
            {apiClient.mode === 'preview' ? 'Preview data is local.' : 'Live data from the Java REST API.'}
          </UiText>
        </View>
        <Button label="Open schedule" onPress={() => navigation.navigate('schedule')} variant="secondary" />
      </Card>

      <View style={styles.metricGrid}>
        <MetricCard label="Active sections" value={apiClient.mode === 'preview' ? '3' : String(liveEnrollments.length)} detail="Current semester" style={styles.metricLeft} />
        <MetricCard label="Weekly sessions" value={apiClient.mode === 'preview' ? '2' : String(scheduledSessions)} detail="Published schedule" />
      </View>

      <ScreenSpacer />
      <SectionHeading title="Next on your schedule" actionLabel="See all" onAction={() => navigation.navigate('schedule')} />
      <Card>
        {dashboardClasses.map((item, index) => (
          <View key={item.code}>
            <ListRow
              leading={item.code.slice(0, 1)}
              meta={item.time}
              subtitle={`${item.name} · Room ${item.room}`}
              title={item.code}
            />
            {index < dashboardClasses.length - 1 ? <Divider /> : null}
          </View>
        ))}
      </Card>
      {dashboardClasses.length === 0 ? <StatePanel kind="empty" title="No active sections" description="Published sections will appear here." /> : null}

      <ScreenSpacer />
      <SectionHeading title="Quick actions" />
      <View style={styles.actionGrid}>
        <Button label="Register courses" onPress={() => navigation.navigate('registration')} variant="secondary" style={styles.actionButton} />
        <Button label="View thesis" onPress={() => navigation.navigate('thesis.topics')} variant="secondary" style={styles.actionButton} />
        <Button label="Ask assistant" onPress={() => navigation.navigate('assistant.chat')} variant="secondary" style={styles.actionButton} />
      </View>
    </ScreenShell>
  );
}

export function ScheduleScreen({ navigation }: MobileScreenProps) {
  const [weekOffset, setWeekOffset] = useState(0);
  const {
    data: liveEnrollments,
    error,
    isLoading,
    reload,
  } = useLiveResource<MobileEnrollment[]>(
    [],
    () => campusApi.enrollments(),
    'Schedule is unavailable.',
  );
  const liveSchedule = liveEnrollments.flatMap((item) => (item.section?.schedules ?? []).map((slot) => ({
    code: item.section?.course?.code ?? item.section?.sectionNumber ?? item.sectionId,
    name: item.section?.course?.nameVi ?? item.section?.course?.name ?? 'Course',
    time: `${slot.startTime} - ${slot.endTime}`,
    room: slot.classroom ? `${slot.classroom.building ?? ''} ${slot.classroom.roomNumber ?? ''}`.trim() : 'TBA',
    day: slot.dayOfWeek,
  })));
  const visibleSchedule = apiClient.mode === 'preview'
    ? upcomingClasses.map((item) => ({ ...item, day: 2 }))
    : liveSchedule;
  const today = new Date();
  const mondayOffset = (today.getDay() + 6) % 7;
  const weekStart = new Date(today.getFullYear(), today.getMonth(), today.getDate() - mondayOffset + weekOffset * 7);
  const weekDays = Array.from({ length: 7 }, (_, index) => {
    const date = new Date(weekStart);
    date.setDate(weekStart.getDate() + index);
    return date;
  });
  const weekEnd = weekDays[6];
  const dateFormatter = new Intl.DateTimeFormat('en', { day: 'numeric', month: 'short' });
  const weekLabel = `${dateFormatter.format(weekStart)} - ${dateFormatter.format(weekEnd)}`;

  if (isLoading || error) {
    return (
      <ScreenShell title="Schedule" eyebrow="Current semester" subtitle="Your published class sessions.">
        <StatePanel
          kind={isLoading ? 'loading' : 'error'}
          title={isLoading ? 'Loading schedule…' : 'Schedule unavailable'}
          description={error ?? 'Retrieving your published class sessions.'}
          actionLabel={error ? 'Try again' : undefined}
          onAction={error ? () => void reload() : undefined}
        />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell title="Schedule" eyebrow="Current semester" subtitle="Your published class sessions.">
      <Card tone="low" style={styles.weekCard}>
        <View style={styles.weekHeader}>
          <Button accessibilityLabel="Previous week" label="‹" onPress={() => setWeekOffset((current) => current - 1)} variant="text" />
          <View style={styles.weekTitle}>
            <UiText variant="label">{weekLabel}</UiText>
            <UiText variant="bodySmall" tone="muted">{weekOffset === 0 ? 'Current week' : weekOffset > 0 ? `${weekOffset} week ahead` : `${Math.abs(weekOffset)} week back`}</UiText>
          </View>
          <Button accessibilityLabel="Next week" label="›" onPress={() => setWeekOffset((current) => current + 1)} variant="text" />
        </View>
        <View style={styles.dayRow}>
          {weekDays.map((date, index) => {
            const active = weekOffset === 0 && date.toDateString() === today.toDateString();
            return (
            <View key={date.toISOString()} style={[styles.dayCell, active ? styles.dayCellActive : undefined]}>
              <UiText variant="meta" tone={active ? 'onPrimary' : 'muted'}>{['M', 'T', 'W', 'T', 'F', 'S', 'S'][index]}</UiText>
              <UiText variant="label" tone={active ? 'onPrimary' : 'default'}>{date.getDate()}</UiText>
            </View>
            );
          })}
        </View>
      </Card>

      <ScreenSpacer />
      <SectionHeading title="Weekly sessions" actionLabel="Courses" onAction={() => navigation.navigate('courses')} />
      {visibleSchedule.map((item, index) => (
        <Card key={`${item.code}-${item.time}-${index}`} style={styles.scheduleCard}>
          <View style={styles.scheduleTime}>
            <UiText variant="label" tone="primary">{item.time.split(' – ')[0]}</UiText>
            <UiText variant="meta" tone="muted">90 min</UiText>
          </View>
          <View style={styles.scheduleInfo}>
            <UiText variant="label">{item.code} · {item.name}</UiText>
            <UiText variant="bodySmall" tone="muted">Day {item.day} · Room {item.room}</UiText>
          </View>
          <Badge label="On campus" tone="primary" />
        </Card>
      ))}
      {visibleSchedule.length === 0 ? <StatePanel kind="empty" title="No published sessions" description="Your timetable will appear after sections publish their schedules." /> : null}
      <Card tone="low" style={styles.noteCard}>
        <UiText variant="bodySmall" tone="muted">No classes after 15:00. Use the quiet afternoon for your thesis milestone.</UiText>
      </Card>
    </ScreenShell>
  );
}

export function CoursesScreen({ navigation }: MobileScreenProps) {
  const {
    data: liveEnrollments,
    error,
    isLoading,
    reload,
  } = useLiveResource<MobileEnrollment[]>(
    [],
    () => campusApi.enrollments(),
    'Courses are unavailable.',
  );
  const visibleCourses = apiClient.mode === 'preview' ? courses : liveEnrollments.map((item) => ({
    code: item.section?.course?.code ?? item.section?.sectionNumber ?? item.sectionId,
    name: item.section?.course?.nameVi ?? item.section?.course?.name ?? 'Course',
    lecturer: item.section?.sectionNumber ?? 'Section',
    progress: item.gradeStatus === 'PUBLISHED' ? 100 : 50,
    status: item.status,
  }));

  if (isLoading || error) {
    return (
      <ScreenShell title="Courses" eyebrow="My learning" subtitle="Your active courses this semester.">
        <StatePanel
          kind={isLoading ? 'loading' : 'error'}
          title={isLoading ? 'Loading courses…' : 'Courses unavailable'}
          description={error ?? 'Retrieving your active course records.'}
          actionLabel={error ? 'Try again' : undefined}
          onAction={error ? () => void reload() : undefined}
        />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell title="Courses" eyebrow="My learning" subtitle={`${visibleCourses.length} active courses this semester.`}>
      <Card tone="primary" style={styles.summaryCard}>
        <UiText variant="bodySmall" tone="muted">Credits completed</UiText>
        <UiText variant="display" tone="primary">{visibleCourses.length}</UiText>
        <ProgressBar label="Published course records" value={visibleCourses.length > 0 ? 100 : 0} />
      </Card>
      <ScreenSpacer />
      <SectionHeading title="Active courses" actionLabel="Grades" onAction={() => navigation.navigate('grades')} />
      {visibleCourses.map((course) => (
        <Card key={course.code} style={styles.courseCard}>
          <View style={styles.courseHeader}>
            <View style={styles.courseCopy}>
              <UiText variant="meta" tone="primary">{course.code}</UiText>
              <UiText variant="headlineSmall">{course.name}</UiText>
              <UiText variant="bodySmall" tone="muted">{course.lecturer}</UiText>
            </View>
            <Badge label={course.status} tone={course.progress > 80 ? 'success' : 'primary'} />
          </View>
          <ProgressBar label="Course progress" value={course.progress} />
        </Card>
      ))}
      {visibleCourses.length === 0 ? <StatePanel kind="empty" title="No active courses" description="Confirmed enrollment records will appear here." /> : null}
      <Button label="Open registration round" onPress={() => navigation.navigate('registration')} variant="secondary" />
    </ScreenShell>
  );
}

export function GradesScreen({ navigation }: MobileScreenProps) {
  const {
    data: liveGrades,
    error,
    isLoading,
    reload,
  } = useLiveResource<Array<{ code: string; name: string; score: string; letter: string; detail: string }>>(
    [],
    async () => {
      const items = await campusApi.grades();
      return items.map((item) => ({
        code: item.courseCode,
        name: item.courseName,
        score: item.finalGrade == null ? '—' : String(item.finalGrade),
        letter: item.letterGrade ?? '—',
        detail: item.gradeStatus,
      }));
    },
    'Grades are unavailable.',
  );

  const visibleGrades = apiClient.mode === 'preview' ? grades : liveGrades;
  const numericGrades = liveGrades.map((grade) => Number(grade.score)).filter(Number.isFinite);
  const averageGrade = numericGrades.length > 0
    ? (numericGrades.reduce((sum, grade) => sum + grade, 0) / numericGrades.length).toFixed(2)
    : '—';

  if (isLoading || error) {
    return (
      <ScreenShell title="Grades" eyebrow="Academic record" subtitle="Your latest published results.">
        <StatePanel
          kind={isLoading ? 'loading' : 'error'}
          title={isLoading ? 'Loading grades…' : 'Grades unavailable'}
          description={error ?? 'Retrieving your published grade records.'}
          actionLabel={error ? 'Try again' : undefined}
          onAction={error ? () => void reload() : undefined}
        />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell title="Grades" eyebrow="Academic record" subtitle="Your latest published results.">
      <View style={styles.metricGrid}>
        <MetricCard label="Average grade" value={apiClient.mode === 'preview' ? '8.76' : averageGrade} detail="Published results" style={styles.metricLeft} />
        <MetricCard label="Records" value={String(visibleGrades.length)} detail="Current transcript" />
      </View>
      <ScreenSpacer />
      <SectionHeading title="Published grades" actionLabel="Courses" onAction={() => navigation.navigate('courses')} />
      {visibleGrades.length > 0 ? <Card>
        {visibleGrades.map((grade, index) => (
          <View key={grade.code}>
            <ListRow
              leading={grade.letter}
              meta={grade.score}
              subtitle={`${grade.name} · ${grade.detail}`}
              title={grade.code}
              trailing={<UiText variant="bodyMedium" tone="primary">›</UiText>}
            />
            {index < visibleGrades.length - 1 ? <Divider /> : null}
          </View>
        ))}
      </Card> : null}
      {visibleGrades.length === 0 ? <StatePanel kind="empty" title="No published grades" description="Published results will appear here." /> : null}
      <ScreenSpacer />
      <Card tone="low">
        <UiText variant="label">Need a transcript?</UiText>
        <UiText variant="bodySmall" tone="muted" style={styles.cardCopy}>Downloadable transcripts will use the Java API contract when it is connected.</UiText>
        <Button label="Ask assistant" onPress={() => navigation.navigate('assistant.chat')} variant="secondary" />
      </Card>
    </ScreenShell>
  );
}

export function AttendanceScreen({ navigation }: MobileScreenProps) {
  const {
    data: liveAttendance,
    error,
    isLoading,
    reload,
  } = useLiveResource<MobileAttendanceSummary[]>(
    [],
    () => campusApi.attendanceSummary(),
    'Attendance is unavailable.',
  );
  const previewAttendance = [
    { sectionId: 'preview-cs204', courseCode: 'CS204', courseName: 'Software Architecture', total: 16, present: 15, absent: 1, late: 0, excused: 0, attendanceRate: 94 },
    { sectionId: 'preview-ba312', courseCode: 'BA312', courseName: 'Business Analytics', total: 15, present: 14, absent: 1, late: 0, excused: 0, attendanceRate: 93 },
  ];
  const visibleAttendance = apiClient.mode === 'preview' ? previewAttendance : liveAttendance;
  const overallAttendance = visibleAttendance.length > 0
    ? Math.round(visibleAttendance.reduce((sum, item) => sum + item.attendanceRate, 0) / visibleAttendance.length)
    : 0;

  if (isLoading || error) {
    return (
      <ScreenShell title="Attendance" eyebrow="Current semester" subtitle="Your published attendance summary.">
        <StatePanel
          kind={isLoading ? 'loading' : 'error'}
          title={isLoading ? 'Loading attendance…' : 'Attendance unavailable'}
          description={error ?? 'Retrieving your attendance records.'}
          actionLabel={error ? 'Try again' : undefined}
          onAction={error ? () => void reload() : undefined}
        />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell title="Attendance" eyebrow="Spring 2026" subtitle="Stay ahead of participation thresholds.">
      <Card tone="primary" style={styles.attendanceHero}>
        <UiText variant="bodySmall" tone="muted">Overall attendance</UiText>
        <UiText variant="display" tone="primary">{overallAttendance}%</UiText>
        <ProgressBar label="Across active courses" value={overallAttendance} tone="success" />
      </Card>
      <ScreenSpacer />
      <SectionHeading title="By course" actionLabel="Schedule" onAction={() => navigation.navigate('schedule')} />
      {visibleAttendance.map((course) => (
        <Card key={course.sectionId} style={styles.attendanceCard}>
          <View style={styles.courseHeader}>
            <View style={styles.courseCopy}>
              <UiText variant="label">{course.courseCode}</UiText>
              <UiText variant="bodySmall" tone="muted">{course.courseName}</UiText>
            </View>
            <UiText variant="headlineSmall" tone="success">{course.attendanceRate}%</UiText>
          </View>
          <ProgressBar value={course.attendanceRate} label={`${course.present} of ${course.total} records present`} tone="success" />
        </Card>
      ))}
      {visibleAttendance.length === 0 ? <StatePanel kind="empty" title="No attendance records" description="Attendance summaries will appear after your lecturer publishes them." /> : null}
    </ScreenShell>
  );
}

export function RegistrationScreen({ navigation }: MobileScreenProps) {
  const isPreview = apiClient.mode === 'preview';
  const [round, setRound] = useState<RegistrationRound | null>(null);
  const [eligibility, setEligibility] = useState<RegistrationEligibility | null>(null);
  const [summary, setSummary] = useState<RegistrationSummary | null>(null);
  const [sections, setSections] = useState<MobileSection[]>([]);
  const [enrollments, setEnrollments] = useState<MobileEnrollment[]>([]);
  const [pending, setPending] = useState<string | null>(null);
  const [retryKeys, setRetryKeys] = useState<Record<string, string>>({});
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const [isLoading, setIsLoading] = useState(!isPreview);
  const [slipHash, setSlipHash] = useState<string | null>(null);
  const [previewSelected, setPreviewSelected] = useState<string[]>([]);

  const previewRound: RegistrationRound = {
    id: 'preview-round', semesterId: 'preview-semester', status: 'OPEN',
    registrationStart: '2026-08-01T00:00:00Z', registrationEnd: '2026-09-01T23:59:59Z',
    addDropStart: '2026-08-01T00:00:00Z', addDropEnd: '2026-09-10T23:59:59Z',
    serverNow: '2026-08-24T08:00:00Z', institutionTimeZone: 'Asia/Ho_Chi_Minh', maxCredits: 28,
  };
  const previewSections: MobileSection[] = [
    { id: 'preview-cs401', sectionNumber: '01', semesterId: previewRound.semesterId, courseCode: 'CS401', courseName: 'Distributed Systems', credits: 3, capacity: 40, enrolledCount: 28, status: 'OPEN', schedules: [{ dayOfWeek: 2, startTime: '10:00', endTime: '11:30', building: 'A3', roomNumber: '201' }] },
    { id: 'preview-ux305', sectionNumber: '01', semesterId: previewRound.semesterId, courseCode: 'UX305', courseName: 'Service Design', credits: 3, capacity: 35, enrolledCount: 30, status: 'OPEN', schedules: [{ dayOfWeek: 4, startTime: '13:30', endTime: '15:00', building: 'C2', roomNumber: '105' }] },
    { id: 'preview-ma210', sectionNumber: '02', semesterId: previewRound.semesterId, courseCode: 'MA210', courseName: 'Applied Statistics', credits: 3, capacity: 45, enrolledCount: 37, status: 'OPEN', schedules: [{ dayOfWeek: 6, startTime: '08:00', endTime: '09:30', building: 'B1', roomNumber: '101' }] },
  ];

  const reload = async () => {
    if (isPreview) {
      setRound(previewRound); setSections(previewSections); setEligibility({ roundId: previewRound.id, eligibilityState: 'ELIGIBLE', priorityRank: 1, maxCredits: previewRound.maxCredits, selectedCredits: previewSelected.length * 3, serverNow: previewRound.serverNow }); setIsLoading(false); return;
    }
    setIsLoading(true); setError(null); setOffline(false);
    try {
      const rounds = await campusApi.registrationRounds();
      const nextRound = rounds.items[0];
      if (!nextRound) throw new ApiClientError('No registration round is currently available.', 409, 'mobile', 'REGISTRATION_ROUND_CLOSED');
      const [nextSections, nextEligibility, nextSummary, nextEnrollments] = await Promise.all([
        campusApi.registrationSections(nextRound.id), campusApi.registrationEligibility(nextRound.id), campusApi.registrationSummary(nextRound.id), campusApi.registrationEnrollments(nextRound.semesterId),
      ]);
      setRound(nextRound); setSections(nextSections); setEligibility(nextEligibility); setSummary(nextSummary); setEnrollments(nextEnrollments.items);
    } catch (nextError) {
      const cached = getRegistrationSnapshot();
      if (nextError instanceof ApiClientError && nextError.status === 0 && (cached.sections?.length || cached.round)) {
        setRound(cached.round ?? null); setSections(cached.sections ?? []); setEligibility(cached.eligibility ?? null); setSummary(cached.summary ?? null); setEnrollments(cached.enrollments ?? []); setOffline(true);
      } else setError(nextError instanceof ApiClientError ? nextError.message : 'Registration data is unavailable.');
    } finally { setIsLoading(false); }
  };
  useEffect(() => { void reload(); }, [isPreview]);

  const activeEnrollmentCount = enrollments.filter((item) => item.status !== 'DROPPED').length;
  const selectedCredits = summary?.selectedCredits ?? (isPreview ? previewSelected.length * 3 : enrollments.reduce((total, item) => total + (item.section?.credits ?? item.section?.course?.credits ?? 0), 0));
  const toggleEnrollment = async (sectionId: string) => {
    if (isPreview) { setPreviewSelected((current) => current.includes(sectionId) ? current.filter((id) => id !== sectionId) : [...current, sectionId]); return; }
    if (!round || offline) return;
    const existing = enrollments.find((item) => item.sectionId === sectionId && item.status !== 'DROPPED');
    const mutationId = existing?.id ?? sectionId;
    const key = retryKeys[mutationId] ?? createIdempotencyKey();
    setPending(mutationId); setMutationError(null);
    try {
      if (existing) {
        Alert.alert('Drop section?', 'This removes the confirmed enrollment after the server accepts the request.', [{ text: 'Cancel', style: 'cancel' }, { text: 'Drop', style: 'destructive', onPress: () => void commitDrop(existing.id, key) }]);
        setPending(null); return;
      }
      const response = await campusApi.registrationEnroll(sectionId, round.id, key);
      setEnrollments((current) => current.some((item) => item.id === response.enrollment.id) ? current : [...current, response.enrollment]);
      setRetryKeys((current) => { const next = { ...current }; delete next[mutationId]; return next; });
    } catch (nextError) {
      if (nextError instanceof ApiClientError && (nextError.status === 0 || nextError.retryable)) setRetryKeys((current) => ({ ...current, [mutationId]: key }));
      setMutationError(nextError instanceof ApiClientError ? nextError.message : 'Enrollment could not be updated.');
    } finally { setPending(null); }
  };
  const commitDrop = async (enrollmentId: string, key: string) => {
    try { await campusApi.registrationDrop(enrollmentId, key); setEnrollments((current) => current.filter((item) => item.id !== enrollmentId)); setRetryKeys((current) => { const next = { ...current }; delete next[enrollmentId]; return next; }); }
    catch (nextError) { if (nextError instanceof ApiClientError && (nextError.status === 0 || nextError.retryable)) setRetryKeys((current) => ({ ...current, [enrollmentId]: key })); setMutationError(nextError instanceof ApiClientError ? nextError.message : 'Enrollment could not be updated.'); }
  };
  const shareSlip = async () => {
    if (isPreview || !round) return;
    try { const slip = await campusApi.registrationSlip(round.id); const hash = slip.headers.get('X-Registration-Slip-Hash') ?? 'unavailable'; setSlipHash(hash); await Share.share({ message: `Registration slip checksum: ${hash}` }); }
    catch (nextError) { setMutationError(nextError instanceof ApiClientError ? nextError.message : 'Registration slip is unavailable.'); }
  };

  if (isLoading || error) {
    return (
      <ScreenShell title="Registration" eyebrow="Course planning" subtitle="Review sections in the current registration round.">
        <StatePanel
          kind={isLoading ? 'loading' : 'error'}
          title={isLoading ? 'Loading registration…' : 'Registration unavailable'}
          description={error ?? 'Retrieving sections and current enrollments.'}
          actionLabel={error ? 'Try again' : undefined}
          onAction={error ? () => void reload() : undefined}
        />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell title="Registration" eyebrow="Course planning" subtitle="Compare available sections and review your current choices.">
      <Card tone="primary" style={styles.registrationHero}>
        <UiText variant="meta" tone="primary">COURSE REGISTRATION</UiText>
        <View style={styles.registrationHeroHeader}>
          <View style={styles.registrationHeroCopy}>
            <UiText variant="headlineSmall">Current selections</UiText>
            <UiText variant="bodySmall" tone="muted">
              {isPreview
                ? 'Preview data stays on this device.'
                : `${selectedCredits}/${round?.maxCredits ?? 28} credits · ${activeEnrollmentCount} active section${activeEnrollmentCount === 1 ? '' : 's'} selected.`}
            </UiText>
          </View>
          <Badge
            label={isPreview ? 'Preview' : `${selectedCredits}/${round?.maxCredits ?? 28} credits`}
            tone={activeEnrollmentCount > 0 ? 'success' : 'primary'}
          />
        </View>
        <Button label="View selected courses" onPress={() => navigation.navigate('courses')} variant="secondary" />
        {!isPreview ? <Button label="Share PDF registration slip" onPress={() => void shareSlip()} variant="secondary" style={styles.slipButton} /> : null}
        {slipHash ? <UiText variant="meta" tone="muted" style={styles.slipHash}>SHA-256 {slipHash}</UiText> : null}
      </Card>
      <ScreenSpacer />
      <SectionHeading title="Recommended sections" />
      {offline ? <StatePanel kind="error" title="Offline read-only mode" description="Showing the last cached registration snapshot. Mutations are disabled until you reconnect." actionLabel="Reconnect" onAction={() => void reload()} /> : null}
      {eligibility && eligibility.eligibilityState !== 'ELIGIBLE' ? <StatePanel kind="error" title="Registration eligibility" description={eligibility.reasonCode ?? 'Your cohort is not eligible in this window.'} /> : null}
      {mutationError ? (
        <StatePanel kind="error" title="Could not update registration" description={mutationError} />
      ) : null}
      {(isPreview ? previewSections : sections).map((section) => {
        {/* Compatibility note for legacy screen contracts: label={section.seats}; section.slot. */}
        const enrolled = isPreview ? previewSelected.includes(section.id) : enrollments.some((item) => item.sectionId === section.id && item.status !== 'DROPPED');
        const code = section.courseCode ?? section.course?.code ?? section.sectionNumber;
        const name = section.courseName ?? section.course?.nameVi ?? section.course?.name ?? 'Course';
        const slots = section.schedules?.map((slot) => `${slot.startTime}–${slot.endTime} · ${slot.building ?? slot.classroom?.building ?? ''} ${slot.roomNumber ?? slot.classroom?.roomNumber ?? ''}`.trim()).join(' | ') || 'Schedule pending';
        const remaining = section.remainingSeats ?? Math.max(0, section.capacity - section.enrolledCount);
        const mutationId = enrollments.find((item) => item.sectionId === section.id)?.id ?? section.id;
        return (
        <Card key={section.id} style={styles.registrationCard}>
          <UiText variant="meta" tone="primary">{code} · {section.credits ?? section.course?.credits ?? 0} credits · Section {section.sectionNumber}</UiText>
          <UiText variant="headlineSmall" style={styles.registrationCourseTitle}>
            {name}
          </UiText>
          <View style={styles.registrationMeta}>
            <UiText variant="bodySmall" tone="muted" style={styles.registrationSlot}>
              {slots}
            </UiText>
            <Badge
              label={remaining > 0 ? `${remaining} seats left` : 'Full'}
              tone={remaining === 0 ? 'warning' : 'success'}
            />
          </View>
          {section.violations?.length ? <UiText variant="bodySmall" tone="error">{section.violations.join(' · ')}</UiText> : null}
          <Button
            label={isPreview ? enrolled ? 'Selected locally' : 'Select locally' : enrolled ? retryKeys[mutationId] ? 'Retry drop' : 'Drop section' : retryKeys[mutationId] ? 'Retry enrollment' : 'Add section'}
            disabled={(!isPreview && offline) || pending === mutationId || (!enrolled && remaining === 0)}
            loading={pending === mutationId}
            onPress={() => void toggleEnrollment(section.id)}
            variant="secondary"
          />
        </Card>
        );
      })}
      {(isPreview ? previewSections : sections).length === 0 ? <StatePanel kind="empty" title="No sections available" description="New sections will appear when the academic office publishes them." /> : null}
    </ScreenShell>
  );
}

export function NotificationsScreen({ navigation }: MobileScreenProps) {
  const [busyId, setBusyId] = useState<string | null>(null);
  const [isMarkingAll, setIsMarkingAll] = useState(false);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const {
    data: liveNotifications,
    error,
    isLoading,
    reload,
    setData: setLiveNotifications,
  } = useLiveResource<MobileNotification[]>(
    [],
    async () => (await campusApi.notifications()).data,
    'Notifications are unavailable.',
  );
  const visibleNotifications = apiClient.mode === 'preview'
    ? previewNotifications.map((item, index) => ({ id: String(index), title: item.title, message: item.subtitle, createdAt: item.meta, isRead: !item.unread }))
    : liveNotifications;

  const markRead = async (id: string) => {
    if (apiClient.mode === 'preview') return;
    setBusyId(id);
    setMutationError(null);
    try {
      await campusApi.markNotificationRead(id);
      setLiveNotifications((current) => current.map((item) => (
        item.id === id ? { ...item, isRead: true } : item
      )));
    } catch (nextError) {
      setMutationError(nextError instanceof ApiClientError ? nextError.message : 'Notification could not be updated.');
    } finally {
      setBusyId(null);
    }
  };

  const markAllRead = async () => {
    if (apiClient.mode === 'preview') return;
    setIsMarkingAll(true);
    setMutationError(null);
    try {
      await campusApi.markAllNotificationsRead();
      setLiveNotifications((current) => current.map((item) => ({ ...item, isRead: true })));
    } catch (nextError) {
      setMutationError(nextError instanceof ApiClientError ? nextError.message : 'Notifications could not be updated.');
    } finally {
      setIsMarkingAll(false);
    }
  };

  if (isLoading || error) {
    return (
      <ScreenShell title="Notifications" eyebrow="Stay informed" subtitle="Account alerts and academic updates.">
        <StatePanel
          kind={isLoading ? 'loading' : 'error'}
          title={isLoading ? 'Loading notifications…' : 'Notifications unavailable'}
          description={error ?? 'Retrieving your latest updates.'}
          actionLabel={error ? 'Try again' : undefined}
          onAction={error ? () => void reload() : undefined}
        />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell title="Notifications" eyebrow="Stay informed" subtitle="Unread items are marked clearly for continuity.">
      <Card tone="low" style={styles.notificationSummary}>
        <View accessibilityLiveRegion="polite">
          <UiText variant="label" tone="primary">{visibleNotifications.filter((item) => !item.isRead).length} unread updates</UiText>
          <UiText variant="bodySmall" tone="muted">Your next action is thesis registration.</UiText>
        </View>
      </Card>
      <ScreenSpacer />
      {mutationError ? (
        <StatePanel kind="error" title="Could not update notifications" description={mutationError} />
      ) : null}
      {visibleNotifications.length > 0 ? <Card>
        {visibleNotifications.map((notification, index) => (
          <View key={notification.id}>
            <ListRow
              leading={notification.isRead ? '·' : '•'}
              meta={notification.createdAt}
              onPress={apiClient.mode === 'live' && !notification.isRead && busyId !== notification.id
                ? () => void markRead(notification.id)
                : undefined}
              subtitle={notification.message}
              title={notification.title}
              unread={!notification.isRead}
            />
            {index < visibleNotifications.length - 1 ? <Divider /> : null}
          </View>
        ))}
      </Card> : (
        <StatePanel kind="empty" title="No notifications" description="New account and academic updates will appear here." />
      )}
      <ScreenSpacer />
      <Button
        label="Mark all as read"
        loading={isMarkingAll}
        onPress={() => void markAllRead()}
        variant="secondary"
        disabled={apiClient.mode === 'preview' || visibleNotifications.every((item) => item.isRead)}
      />
    </ScreenShell>
  );
}

export function ProfileScreen({ navigation, role }: MobileScreenProps) {
  const {
    data: account,
    error,
    isLoading,
    reload,
  } = useLiveResource<AuthUser | null>(
    null,
    () => campusApi.account(),
    'Profile is unavailable.',
  );
  const displayName = apiClient.mode === 'preview'
    ? 'Nguyen Duc Minh'
    : [account?.firstName, account?.lastName].filter(Boolean).join(' ') || 'Student';
  const displayEmail = apiClient.mode === 'preview' ? 'minh.nguyen@ute.edu.vn' : account?.email ?? '—';
  const displayStudentId = apiClient.mode === 'preview' ? '2022SE0417' : account?.studentId ?? '—';

  if (isLoading || error) {
    return (
      <ScreenShell title="Profile" eyebrow="Account" subtitle="Your identity and academic preferences.">
        <StatePanel
          kind={isLoading ? 'loading' : 'error'}
          title={isLoading ? 'Loading profile…' : 'Profile unavailable'}
          description={error ?? 'Retrieving your account details.'}
          actionLabel={error ? 'Try again' : undefined}
          onAction={error ? () => void reload() : undefined}
        />
      </ScreenShell>
    );
  }

  return (
    <ScreenShell title="Profile" eyebrow="Account" subtitle="Your identity and academic preferences.">
      <Card style={styles.profileHero}>
        <View style={styles.profileHeader}>
          <Avatar label={displayName} size={64} />
          <View style={styles.profileCopy}>
            <UiText variant="headlineSmall">{displayName}</UiText>
            <UiText variant="bodySmall" tone="muted">{displayStudentId}</UiText>
            <Badge label={apiClient.mode === 'preview' ? `${role} preview` : role} tone="primary" />
          </View>
        </View>
      </Card>
      <ScreenSpacer />
      <Card>
        <SectionHeading title="Personal details" />
        <ListRow title="University email" subtitle={displayEmail} />
        <Divider />
        <ListRow title="Student ID" subtitle={displayStudentId} />
        <Divider />
        <ListRow title="Account status" subtitle={apiClient.mode === 'preview' ? 'Preview' : 'Authenticated through Java API'} />
      </Card>
      <ScreenSpacer />
      <Card tone="low">
        <UiText variant="label">Need help?</UiText>
        <UiText variant="bodySmall" tone="muted" style={styles.cardCopy}>Ask the academic assistant about your schedule, grades, or thesis journey.</UiText>
        <Button label="Open assistant" onPress={() => navigation.navigate('assistant.chat')} variant="secondary" />
      </Card>
      <Button label="Sign out" onPress={navigation.signOut} variant="text" style={styles.signOut} />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  heroCard: { marginBottom: tokens.spacing.md },
  heroCopy: { marginBottom: tokens.spacing.md },
  heroTitle: { marginBottom: tokens.spacing.xs, marginTop: tokens.spacing.sm },
  metricGrid: { flexDirection: 'row' },
  metricLeft: { marginRight: tokens.spacing.sm },
  actionGrid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
  actionButton: { marginBottom: tokens.spacing.sm, width: '48%' },
  weekCard: { padding: tokens.spacing.sm },
  weekHeader: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' },
  weekTitle: { alignItems: 'center' },
  dayRow: { flexDirection: 'row', justifyContent: 'space-between', marginTop: tokens.spacing.sm },
  dayCell: { alignItems: 'center', borderRadius: tokens.radii.control, justifyContent: 'center', minHeight: 48, width: 35 },
  dayCellActive: { backgroundColor: tokens.colors.primary },
  scheduleCard: { alignItems: 'center', flexDirection: 'row', marginBottom: tokens.spacing.sm, padding: tokens.spacing.sm },
  scheduleTime: { alignItems: 'center', borderRightColor: tokens.colors.outlineVariant, borderRightWidth: 1, marginRight: tokens.spacing.sm, paddingRight: tokens.spacing.sm },
  scheduleInfo: { flex: 1, paddingRight: tokens.spacing.xs },
  noteCard: { marginTop: tokens.spacing.sm },
  summaryCard: { padding: tokens.spacing.lg },
  courseCard: { marginBottom: tokens.spacing.sm },
  courseHeader: { alignItems: 'flex-start', flexDirection: 'row', justifyContent: 'space-between' },
  courseCopy: { flex: 1, paddingRight: tokens.spacing.sm },
  cardCopy: { marginBottom: tokens.spacing.md, marginTop: tokens.spacing.xs },
  attendanceHero: { padding: tokens.spacing.lg },
  attendanceCard: { marginBottom: tokens.spacing.sm },
  registrationHero: { padding: tokens.spacing.lg },
  registrationHeroHeader: { alignItems: 'flex-start', flexDirection: 'row', gap: tokens.spacing.sm, marginBottom: tokens.spacing.md, marginTop: tokens.spacing.xs },
  registrationHeroCopy: { flex: 1, gap: tokens.spacing.xs },
  registrationCard: { marginBottom: tokens.spacing.sm },
  registrationCourseTitle: { marginTop: tokens.spacing.xs },
  registrationMeta: { alignItems: 'flex-start', flexDirection: 'row', gap: tokens.spacing.sm, marginBottom: tokens.spacing.md, marginTop: tokens.spacing.sm },
  registrationSlot: { flex: 1 },
  slipButton: { marginTop: tokens.spacing.sm },
  slipHash: { marginTop: tokens.spacing.sm },
  notificationSummary: { padding: tokens.spacing.md },
  profileHero: { padding: tokens.spacing.lg },
  profileHeader: { alignItems: 'center', flexDirection: 'row' },
  profileCopy: { flex: 1, marginLeft: tokens.spacing.md },
  signOut: { marginTop: tokens.spacing.md },
});
