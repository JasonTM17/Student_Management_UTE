import { StyleSheet, View } from 'react-native';

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
  UiText,
} from '../../components/Ui';
import { tokens } from '../../design/tokens';
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

const invoices = [
  { number: 'INV-2026-0041', term: 'Spring 2026', amount: 'Tuition balance', status: 'Pending', tone: 'warning' as const },
  { number: 'INV-2025-0117', term: 'Fall 2025', amount: 'Paid in full', status: 'Paid', tone: 'success' as const },
];

const notifications = [
  { title: 'Thesis registration opens Monday', subtitle: 'Choose a topic before 30 August.', meta: '10m', unread: true },
  { title: 'CS204 grade published', subtitle: 'Your final project result is ready.', meta: '2h', unread: true },
  { title: 'Library maintenance notice', subtitle: 'Online renewals pause this weekend.', meta: 'Yesterday', unread: false },
];

export function StudentDashboardScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell
      eyebrow="Monday · 24 August 2026"
      title="Good morning, Minh"
      subtitle="Here is your academic continuity snapshot."
    >
      <Card tone="primary" style={styles.heroCard}>
        <View style={styles.heroCopy}>
          <UiText variant="meta" tone="primary">
            CURRENT SEMESTER
          </UiText>
          <UiText variant="headlineSmall" style={styles.heroTitle}>
            Spring 2026 is on track.
          </UiText>
          <UiText variant="bodySmall" tone="muted">
            Three actions are ready for your attention today.
          </UiText>
        </View>
        <Button label="Open schedule" onPress={() => navigation.navigate('schedule')} variant="secondary" />
      </Card>

      <View style={styles.metricGrid}>
        <MetricCard label="Current GPA" value="3.62" detail="+0.18 this term" style={styles.metricLeft} />
        <MetricCard label="Attendance" value="94%" detail="Above your target" />
      </View>

      <ScreenSpacer />
      <SectionHeading title="Next on your schedule" actionLabel="See all" onAction={() => navigation.navigate('schedule')} />
      <Card>
        {upcomingClasses.map((item, index) => (
          <View key={item.code}>
            <ListRow
              leading={item.code.slice(0, 1)}
              meta={item.time}
              subtitle={`${item.name} · Room ${item.room}`}
              title={item.code}
            />
            {index < upcomingClasses.length - 1 ? <Divider /> : null}
          </View>
        ))}
      </Card>

      <ScreenSpacer />
      <SectionHeading title="Quick actions" />
      <View style={styles.actionGrid}>
        <Button label="Register courses" onPress={() => navigation.navigate('registration')} variant="secondary" style={styles.actionButton} />
        <Button label="View thesis" onPress={() => navigation.navigate('thesis.topics')} variant="secondary" style={styles.actionButton} />
        <Button label="Open alerts" onPress={() => navigation.navigate('notifications')} variant="secondary" style={styles.actionButton} />
      </View>
    </ScreenShell>
  );
}

export function ScheduleScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Schedule" eyebrow="Spring 2026" subtitle="Your week at a glance.">
      <Card tone="low" style={styles.weekCard}>
        <View style={styles.weekHeader}>
          <Button label="‹" onPress={() => undefined} variant="text" />
          <View style={styles.weekTitle}>
            <UiText variant="label">18 – 24 August</UiText>
            <UiText variant="bodySmall" tone="muted">Week 34</UiText>
          </View>
          <Button label="›" onPress={() => undefined} variant="text" />
        </View>
        <View style={styles.dayRow}>
          {['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((day, index) => (
            <View key={`${day}-${index}`} style={[styles.dayCell, index === 1 ? styles.dayCellActive : undefined]}>
              <UiText variant="meta" tone={index === 1 ? 'onPrimary' : 'muted'}>{day}</UiText>
              <UiText variant="label" tone={index === 1 ? 'onPrimary' : 'default'}>{18 + index}</UiText>
            </View>
          ))}
        </View>
      </Card>

      <ScreenSpacer />
      <SectionHeading title="Tuesday, 19 August" actionLabel="Courses" onAction={() => navigation.navigate('courses')} />
      {upcomingClasses.map((item) => (
        <Card key={item.code} style={styles.scheduleCard}>
          <View style={styles.scheduleTime}>
            <UiText variant="label" tone="primary">{item.time.split(' – ')[0]}</UiText>
            <UiText variant="meta" tone="muted">90 min</UiText>
          </View>
          <View style={styles.scheduleInfo}>
            <UiText variant="label">{item.code} · {item.name}</UiText>
            <UiText variant="bodySmall" tone="muted">Room {item.room} · Dr. Le Minh</UiText>
          </View>
          <Badge label="On campus" tone="primary" />
        </Card>
      ))}
      <Card tone="low" style={styles.noteCard}>
        <UiText variant="bodySmall" tone="muted">No classes after 15:00. Use the quiet afternoon for your thesis milestone.</UiText>
      </Card>
    </ScreenShell>
  );
}

export function CoursesScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Courses" eyebrow="My learning" subtitle="Three active courses this semester.">
      <Card tone="primary" style={styles.summaryCard}>
        <UiText variant="bodySmall" tone="muted">Credits completed</UiText>
        <UiText variant="display" tone="primary">96 / 120</UiText>
        <ProgressBar label="Degree progress" value={80} />
      </Card>
      <ScreenSpacer />
      <SectionHeading title="Active courses" actionLabel="Grades" onAction={() => navigation.navigate('grades')} />
      {courses.map((course) => (
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
      <Button label="Open registration round" onPress={() => navigation.navigate('registration')} variant="secondary" />
    </ScreenShell>
  );
}

export function GradesScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Grades" eyebrow="Academic record" subtitle="Your latest published results.">
      <View style={styles.metricGrid}>
        <MetricCard label="Term GPA" value="3.62" detail="Spring 2026" style={styles.metricLeft} />
        <MetricCard label="Credits" value="12" detail="This term" />
      </View>
      <ScreenSpacer />
      <SectionHeading title="Published grades" actionLabel="Courses" onAction={() => navigation.navigate('courses')} />
      <Card>
        {grades.map((grade, index) => (
          <View key={grade.code}>
            <ListRow
              leading={grade.letter}
              meta={grade.score}
              subtitle={`${grade.name} · ${grade.detail}`}
              title={grade.code}
              trailing={<UiText variant="bodyMedium" tone="primary">›</UiText>}
            />
            {index < grades.length - 1 ? <Divider /> : null}
          </View>
        ))}
      </Card>
      <ScreenSpacer />
      <Card tone="low">
        <UiText variant="label">Need a transcript?</UiText>
        <UiText variant="bodySmall" tone="muted" style={styles.cardCopy}>Downloadable transcripts will appear here when records are connected.</UiText>
        <Button label="View profile" onPress={() => navigation.navigate('profile')} variant="secondary" />
      </Card>
    </ScreenShell>
  );
}

export function AttendanceScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Attendance" eyebrow="Spring 2026" subtitle="Stay ahead of participation thresholds.">
      <Card tone="primary" style={styles.attendanceHero}>
        <UiText variant="bodySmall" tone="muted">Overall attendance</UiText>
        <UiText variant="display" tone="primary">94%</UiText>
        <ProgressBar label="Across active courses" value={94} tone="success" />
      </Card>
      <ScreenSpacer />
      <SectionHeading title="By course" actionLabel="Schedule" onAction={() => navigation.navigate('schedule')} />
      {[
        { code: 'CS204', name: 'Software Architecture', attended: 15, total: 16, value: 94 },
        { code: 'BA312', name: 'Business Analytics', attended: 14, total: 15, value: 93 },
        { code: 'EN201', name: 'Academic English', attended: 17, total: 18, value: 94 },
      ].map((course) => (
        <Card key={course.code} style={styles.attendanceCard}>
          <View style={styles.courseHeader}>
            <View style={styles.courseCopy}>
              <UiText variant="label">{course.code}</UiText>
              <UiText variant="bodySmall" tone="muted">{course.name}</UiText>
            </View>
            <UiText variant="headlineSmall" tone="success">{course.value}%</UiText>
          </View>
          <ProgressBar value={course.value} label={`${course.attended} of ${course.total} sessions`} tone="success" />
        </Card>
      ))}
    </ScreenShell>
  );
}

export function RegistrationScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Registration" eyebrow="Course planning" subtitle="The next round closes in 4 days.">
      <Card tone="primary" style={styles.registrationHero}>
        <View style={styles.courseHeader}>
          <View style={styles.courseCopy}>
            <UiText variant="meta" tone="primary">ROUND 02 · OPEN</UiText>
            <UiText variant="headlineSmall">Spring 2026 add/drop</UiText>
          </View>
          <Badge label="4 days left" tone="warning" />
        </View>
        <UiText variant="bodySmall" tone="muted" style={styles.cardCopy}>You have 3 selected sections and 1 waitlist position.</UiText>
        <Button label="View selected courses" onPress={() => navigation.navigate('courses')} variant="secondary" />
      </Card>
      <ScreenSpacer />
      <SectionHeading title="Recommended sections" />
      {[
        { code: 'CS401', name: 'Distributed Systems', slot: 'Tue · 10:00 · A3.201', seats: '12 seats left' },
        { code: 'UX305', name: 'Service Design', slot: 'Thu · 13:30 · C2.105', seats: '5 seats left' },
        { code: 'MA210', name: 'Applied Statistics', slot: 'Fri · 08:00 · B1.101', seats: 'Waitlist 2' },
      ].map((section) => (
        <Card key={section.code} style={styles.registrationCard}>
          <View style={styles.courseHeader}>
            <View style={styles.courseCopy}>
              <UiText variant="label">{section.code}</UiText>
              <UiText variant="headlineSmall">{section.name}</UiText>
              <UiText variant="bodySmall" tone="muted">{section.slot}</UiText>
            </View>
            <UiText variant="meta" tone={section.seats.includes('Waitlist') ? 'warning' : 'success'} style={styles.seatsText}>
              {section.seats}
            </UiText>
          </View>
          <Button label={section.seats.includes('Waitlist') ? 'Join waitlist' : 'Add section'} onPress={() => undefined} variant="secondary" />
        </Card>
      ))}
    </ScreenShell>
  );
}

export function InvoicesScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Invoices" eyebrow="Finance" subtitle="Review balances and payment history.">
      <Card tone="primary" style={styles.invoiceHero}>
        <UiText variant="bodySmall" tone="muted">Outstanding balance</UiText>
        <UiText variant="display" tone="primary">₫2.45m</UiText>
        <UiText variant="bodySmall" tone="muted">Due 30 August 2026</UiText>
        <Button label="Continue to payment" onPress={() => undefined} style={styles.invoiceButton} />
      </Card>
      <ScreenSpacer />
      <SectionHeading title="Recent payment records" actionLabel="Profile" onAction={() => navigation.navigate('profile')} />
      {invoices.map((invoice) => (
        <Card key={invoice.number} style={styles.invoiceCard}>
          <View style={styles.courseHeader}>
            <View style={styles.courseCopy}>
              <UiText variant="label">{invoice.number}</UiText>
              <UiText variant="bodySmall" tone="muted">{invoice.term}</UiText>
            </View>
            <Badge label={invoice.status} tone={invoice.tone} />
          </View>
          <UiText variant="headlineSmall" style={styles.invoiceAmount}>{invoice.amount}</UiText>
          <Button label="View details" onPress={() => undefined} variant="text" />
        </Card>
      ))}
    </ScreenShell>
  );
}

export function NotificationsScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Notifications" eyebrow="Stay informed" subtitle="Unread items are marked clearly for continuity.">
      <Card tone="low" style={styles.notificationSummary}>
        <UiText variant="label" tone="primary">2 unread updates</UiText>
        <UiText variant="bodySmall" tone="muted">Your next action is thesis registration.</UiText>
      </Card>
      <ScreenSpacer />
      <Card>
        {notifications.map((notification, index) => (
          <View key={notification.title}>
            <ListRow
              leading={notification.unread ? '•' : '·'}
              meta={notification.meta}
              onPress={() => notification.title.includes('Thesis') ? navigation.navigate('thesis.registration') : undefined}
              subtitle={notification.subtitle}
              title={notification.title}
              unread={notification.unread}
            />
            {index < notifications.length - 1 ? <Divider /> : null}
          </View>
        ))}
      </Card>
      <ScreenSpacer />
      <Button label="Mark all as read" onPress={() => undefined} variant="secondary" />
    </ScreenShell>
  );
}

export function ProfileScreen({ navigation, role }: MobileScreenProps) {
  return (
    <ScreenShell title="Profile" eyebrow="Account" subtitle="Your identity and academic preferences.">
      <Card style={styles.profileHero}>
        <View style={styles.profileHeader}>
          <Avatar label="Minh" size={64} />
          <View style={styles.profileCopy}>
            <UiText variant="headlineSmall">Nguyen Duc Minh</UiText>
            <UiText variant="bodySmall" tone="muted">2022 · Software Engineering</UiText>
            <Badge label={`${role} preview`} tone="primary" />
          </View>
        </View>
      </Card>
      <ScreenSpacer />
      <Card>
        <SectionHeading title="Personal details" />
        <ListRow title="University email" subtitle="minh.nguyen@ute.edu.vn" />
        <Divider />
        <ListRow title="Student ID" subtitle="2022SE0417" />
        <Divider />
        <ListRow title="Faculty" subtitle="Information Technology" />
      </Card>
      <ScreenSpacer />
      <Card tone="low">
        <UiText variant="label">Next step</UiText>
        <UiText variant="bodySmall" tone="muted" style={styles.cardCopy}>Keep your profile details current before the next registration window.</UiText>
        <Button label="View thesis progress" onPress={() => navigation.navigate('thesis.progress')} variant="secondary" />
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
  registrationCard: { marginBottom: tokens.spacing.sm },
  seatsText: { maxWidth: 78, textAlign: 'right' },
  invoiceHero: { padding: tokens.spacing.lg },
  invoiceButton: { marginTop: tokens.spacing.md },
  invoiceCard: { marginBottom: tokens.spacing.sm },
  invoiceAmount: { marginBottom: tokens.spacing.xs, marginTop: tokens.spacing.md },
  notificationSummary: { padding: tokens.spacing.md },
  profileHero: { padding: tokens.spacing.lg },
  profileHeader: { alignItems: 'center', flexDirection: 'row' },
  profileCopy: { flex: 1, marginLeft: tokens.spacing.md },
  signOut: { marginTop: tokens.spacing.md },
});
