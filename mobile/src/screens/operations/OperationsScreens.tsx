import { StyleSheet, View } from 'react-native';

import {
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

const adminMetrics = [
  { label: 'Students', value: '12.4k', detail: '+4.2% this term' },
  { label: 'Lecturers', value: '486', detail: 'Across 12 faculties' },
  { label: 'Open sections', value: '1,208', detail: '87% capacity' },
  { label: 'Alerts', value: '18', detail: '5 need review' },
];

export function AdminDashboardScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Admin dashboard" eyebrow="Operations · Admin" subtitle="A focused view of campus continuity signals.">
      <Card tone="primary" style={styles.hero}>
        <UiText variant="meta" tone="primary">TODAY · 24 AUGUST 2026</UiText>
        <UiText variant="headlineSmall" style={styles.heroTitle}>Keep the campus moving.</UiText>
        <UiText variant="bodySmall" tone="muted">Review registration pressure and people operations from one mobile surface.</UiText>
      </Card>
      <ScreenSpacer />
      <View style={styles.metricGrid}>
        {adminMetrics.slice(0, 2).map((metric, index) => (
          <MetricCard key={metric.label} {...metric} style={index === 0 ? styles.metricLeft : undefined} />
        ))}
      </View>
      <ScreenSpacer size={tokens.spacing.sm} />
      <View style={styles.metricGrid}>
        {adminMetrics.slice(2).map((metric, index) => (
          <MetricCard key={metric.label} {...metric} style={index === 0 ? styles.metricLeft : undefined} />
        ))}
      </View>
      <ScreenSpacer />
      <SectionHeading title="Operational shortcuts" />
      <Card>
        <ListRow leading="S" title="Manage students" subtitle="Search and review student records" onPress={() => navigation.navigate('admin.students')} trailing={<UiText variant="bodyMedium" tone="muted">›</UiText>} />
        <Divider />
        <ListRow leading="L" title="Manage lecturers" subtitle="Assignments and teaching load" onPress={() => navigation.navigate('admin.lecturers')} trailing={<UiText variant="bodyMedium" tone="muted">›</UiText>} />
        <Divider />
        <ListRow leading="!" title="Review notifications" subtitle="5 high-priority items" onPress={() => navigation.navigate('notifications')} trailing={<Badge label="5" tone="warning" />} />
      </Card>
    </ScreenShell>
  );
}

export function AdminStudentsScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Manage students" eyebrow="Admin workspace" subtitle="A mobile-friendly record list for common operations.">
      <Card tone="low" style={styles.summaryCard}>
        <UiText variant="label">12,418 active students</UiText>
        <UiText variant="bodySmall" tone="muted">Updated 10 minutes ago · data comes from the Java API seam when connected.</UiText>
      </Card>
      <ScreenSpacer />
      <SectionHeading title="Recently active" actionLabel="Dashboard" onAction={() => navigation.navigate('admin.dashboard')} />
      <Card>
        {[
          { id: '2022SE0417', name: 'Nguyen Duc Minh', detail: 'Software Engineering · Year 4', status: 'Active' },
          { id: '2023BA0192', name: 'Pham Anh Thu', detail: 'Business Administration · Year 3', status: 'Active' },
          { id: '2024IT0871', name: 'Le Khanh Vy', detail: 'Information Technology · Year 2', status: 'Review' },
        ].map((student, index, list) => (
          <View key={student.id}>
            <ListRow leading={student.name.slice(0, 1)} title={student.name} subtitle={`${student.id} · ${student.detail}`} trailing={<Badge label={student.status} tone={student.status === 'Active' ? 'success' : 'warning'} />} />
            {index < list.length - 1 ? <Divider /> : null}
          </View>
        ))}
      </Card>
      <Button label="Open lecturer records" onPress={() => navigation.navigate('admin.lecturers')} variant="secondary" style={styles.topButton} />
    </ScreenShell>
  );
}

export function AdminLecturersScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Manage lecturers" eyebrow="Admin workspace" subtitle="Teaching load and availability at a glance.">
      <Card tone="primary" style={styles.loadCard}>
        <UiText variant="bodySmall" tone="muted">Average teaching load</UiText>
        <UiText variant="display" tone="primary">82%</UiText>
        <ProgressBar label="Across active lecturers" value={82} />
      </Card>
      <ScreenSpacer />
      <SectionHeading title="Lecturer directory" actionLabel="Dashboard" onAction={() => navigation.navigate('admin.dashboard')} />
      {[
        { name: 'Dr. Le Minh', department: 'Computer Science', load: '92%', status: 'Busy' },
        { name: 'Assoc. Prof. Tran Ha', department: 'Business Analytics', load: '78%', status: 'Available' },
        { name: 'Ms. Hoang Lan', department: 'Service Design', load: '64%', status: 'Available' },
      ].map((lecturer) => (
        <Card key={lecturer.name} style={styles.lecturerCard}>
          <ListRow leading={lecturer.name.slice(0, 1)} title={lecturer.name} subtitle={`${lecturer.department} · ${lecturer.load} load`} trailing={<Badge label={lecturer.status} tone={lecturer.status === 'Busy' ? 'warning' : 'success'} />} />
          <ProgressBar value={Number.parseInt(lecturer.load, 10)} />
        </Card>
      ))}
      <Button label="Open lecturer dashboard" onPress={() => navigation.navigate('lecturer.dashboard')} variant="secondary" />
    </ScreenShell>
  );
}

export function LecturerDashboardScreen({ navigation, role }: MobileScreenProps) {
  return (
    <ScreenShell title="Lecturer dashboard" eyebrow={`Operations · ${role}`} subtitle="Your teaching day, kept readable.">
      <Card tone="primary" style={styles.hero}>
        <UiText variant="meta" tone="primary">TUESDAY · 19 AUGUST 2026</UiText>
        <UiText variant="headlineSmall" style={styles.heroTitle}>Two classes need you today.</UiText>
        <UiText variant="bodySmall" tone="muted">Attendance and grading actions are one tap away.</UiText>
      </Card>
      <ScreenSpacer />
      <View style={styles.metricGrid}>
        <MetricCard label="Classes" value="2" detail="Today" style={styles.metricLeft} />
        <MetricCard label="To grade" value="18" detail="Across 2 sections" />
      </View>
      <ScreenSpacer />
      <SectionHeading title="Teaching shortcuts" />
      <Card>
        <ListRow leading="◷" title="Teaching schedule" subtitle="Next class · CS204 at 08:00" onPress={() => navigation.navigate('lecturer.schedule')} trailing={<UiText variant="bodyMedium" tone="muted">›</UiText>} />
        <Divider />
        <ListRow leading="G" title="Gradebook" subtitle="18 submissions waiting" onPress={() => navigation.navigate('lecturer.grading')} trailing={<Badge label="18" tone="warning" />} />
        <Divider />
        <ListRow leading="P" title="Class attendance" subtitle="Mark today’s attendance" onPress={() => navigation.navigate('lecturer.attendance')} trailing={<UiText variant="bodyMedium" tone="muted">›</UiText>} />
      </Card>
    </ScreenShell>
  );
}

export function LecturerScheduleScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Teaching schedule" eyebrow="Lecturer workspace" subtitle="Today’s classes and the next handoff.">
      <Card tone="low" style={styles.weekCard}>
        <UiText variant="label">Tuesday · 19 August</UiText>
        <UiText variant="bodySmall" tone="muted">2 classes · 3 hours on campus</UiText>
      </Card>
      <ScreenSpacer />
      {[
        { code: 'CS204', title: 'Software Architecture', time: '08:00 – 09:30', room: 'A2.204', status: 'Next' },
        { code: 'BA312', title: 'Business Analytics', time: '13:30 – 15:00', room: 'B1.108', status: 'Later' },
      ].map((item) => (
        <Card key={item.code} style={styles.scheduleCard}>
          <View style={styles.scheduleHeader}>
            <View style={styles.topicCopy}>
              <UiText variant="meta" tone="primary">{item.code}</UiText>
              <UiText variant="headlineSmall">{item.title}</UiText>
              <UiText variant="bodySmall" tone="muted">{item.time} · Room {item.room}</UiText>
            </View>
            <Badge label={item.status} tone={item.status === 'Next' ? 'primary' : 'neutral'} />
          </View>
          <Button label="Take attendance" onPress={() => navigation.navigate('lecturer.attendance')} variant="secondary" style={styles.topButton} />
        </Card>
      ))}
      <Button label="Open gradebook" onPress={() => navigation.navigate('lecturer.grading')} variant="secondary" />
    </ScreenShell>
  );
}

export function LecturerGradingScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Gradebook" eyebrow="CS204 · Software Architecture" subtitle="Keep feedback clear before publishing.">
      <Card tone="primary" style={styles.gradingHero}>
        <View style={styles.courseHeader}>
          <View style={styles.topicCopy}>
            <UiText variant="bodySmall" tone="muted">Pending submissions</UiText>
            <UiText variant="display" tone="primary">18</UiText>
          </View>
          <Badge label="Draft" tone="warning" />
        </View>
        <ProgressBar label="Reviewed" value={64} />
      </Card>
      <ScreenSpacer />
      <SectionHeading title="Recent submissions" actionLabel="Attendance" onAction={() => navigation.navigate('lecturer.attendance')} />
      <Card>
        {[
          { name: 'Nguyen Duc Minh', task: 'Architecture review · submitted 09:24', score: '9.1' },
          { name: 'Pham Anh Thu', task: 'Architecture review · submitted yesterday', score: '8.6' },
          { name: 'Le Khanh Vy', task: 'Architecture review · needs review', score: '—' },
        ].map((submission, index, list) => (
          <View key={submission.name}>
            <ListRow leading={submission.name.slice(0, 1)} title={submission.name} subtitle={submission.task} trailing={<UiText variant="headlineSmall" tone={submission.score === '—' ? 'muted' : 'primary'}>{submission.score}</UiText>} />
            {index < list.length - 1 ? <Divider /> : null}
          </View>
        ))}
      </Card>
      <ScreenSpacer />
      <Button label="Save draft grades" onPress={() => undefined} />
      <Button label="Preview publish" onPress={() => undefined} variant="secondary" style={styles.secondaryButton} />
    </ScreenShell>
  );
}

export function LecturerAttendanceScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Class attendance" eyebrow="CS204 · Today" subtitle="Mark attendance with a readable, focused list.">
      <Card tone="low" style={styles.attendanceSummary}>
        <View style={styles.courseHeader}>
          <View style={styles.topicCopy}>
            <UiText variant="label">08:00 – 09:30 · A2.204</UiText>
            <UiText variant="bodySmall" tone="muted">Software Architecture · Section 02</UiText>
          </View>
          <Badge label="16 students" tone="primary" />
        </View>
      </Card>
      <ScreenSpacer />
      <SectionHeading title="Students" />
      <Card>
        {[
          { name: 'Nguyen Duc Minh', status: 'Present' },
          { name: 'Pham Anh Thu', status: 'Present' },
          { name: 'Le Khanh Vy', status: 'Late' },
          { name: 'Tran Gia Bao', status: 'Absent' },
        ].map((student, index, list) => (
          <View key={student.name}>
            <ListRow leading={student.name.slice(0, 1)} title={student.name} subtitle="2022 · Software Engineering" trailing={<Badge label={student.status} tone={student.status === 'Present' ? 'success' : student.status === 'Late' ? 'warning' : 'error'} />} />
            {index < list.length - 1 ? <Divider /> : null}
          </View>
        ))}
      </Card>
      <ScreenSpacer />
      <Button label="Save attendance" onPress={() => navigation.navigate('lecturer.dashboard')} />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  hero: { padding: tokens.spacing.lg },
  heroTitle: { marginBottom: tokens.spacing.xs, marginTop: tokens.spacing.sm },
  metricGrid: { flexDirection: 'row' },
  metricLeft: { marginRight: tokens.spacing.sm },
  summaryCard: { padding: tokens.spacing.md },
  topButton: { marginTop: tokens.spacing.sm },
  loadCard: { padding: tokens.spacing.lg },
  lecturerCard: { marginBottom: tokens.spacing.sm },
  weekCard: { padding: tokens.spacing.md },
  scheduleCard: { marginBottom: tokens.spacing.sm, padding: tokens.spacing.md },
  scheduleHeader: { alignItems: 'flex-start', flexDirection: 'row', justifyContent: 'space-between' },
  topicCopy: { flex: 1, paddingRight: tokens.spacing.sm },
  gradingHero: { padding: tokens.spacing.lg },
  secondaryButton: { marginTop: tokens.spacing.sm },
  attendanceSummary: { padding: tokens.spacing.md },
});

