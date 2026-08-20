import { StyleSheet, View } from 'react-native';

import {
  Badge,
  Button,
  Card,
  Divider,
  ListRow,
  ProgressBar,
  ScreenShell,
  ScreenSpacer,
  SectionHeading,
  UiText,
} from '../../components/Ui';
import { tokens } from '../../design/tokens';
import type { MobileScreenProps } from '../../navigation/types';

const topics = [
  { id: 'TH-024', title: 'AI-enabled library assistant', supervisor: 'Dr. Le Minh', status: 'Open', tone: 'success' as const },
  { id: 'TH-031', title: 'Inclusive campus navigation', supervisor: 'Ms. Hoang Lan', status: 'Open', tone: 'success' as const },
  { id: 'TH-039', title: 'Learning analytics for continuity', supervisor: 'Assoc. Prof. Tran Ha', status: 'Reviewing', tone: 'warning' as const },
];

export function ThesisTopicsScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Thesis topics" eyebrow="Thesis lifecycle" subtitle="Find a topic that keeps your research moving.">
      <Card tone="primary" style={styles.hero}>
        <UiText variant="meta" tone="primary">ROUND 01 · TOPIC DISCOVERY</UiText>
        <UiText variant="headlineSmall" style={styles.heroTitle}>Registration opens 25 August.</UiText>
        <UiText variant="bodySmall" tone="muted">Browse, compare, and save a topic before registering your group.</UiText>
      </Card>
      <ScreenSpacer />
      <SectionHeading title="Available topics" actionLabel="My progress" onAction={() => navigation.navigate('thesis.progress')} />
      {topics.map((topic) => (
        <Card key={topic.id} style={styles.topicCard}>
          <View style={styles.topicHeader}>
            <View style={styles.topicCopy}>
              <UiText variant="meta" tone="primary">{topic.id}</UiText>
              <UiText variant="headlineSmall">{topic.title}</UiText>
              <UiText variant="bodySmall" tone="muted">Supervisor · {topic.supervisor}</UiText>
            </View>
            <Badge label={topic.status} tone={topic.tone} />
          </View>
          <Button label="View topic" onPress={() => navigation.navigate('thesis.detail')} variant="text" />
        </Card>
      ))}
      <Card tone="low" style={styles.tipCard}>
        <UiText variant="label">A clear next step</UiText>
        <UiText variant="bodySmall" tone="muted" style={styles.copy}>When your group is ready, start registration from the selected topic detail.</UiText>
        <Button label="Open registration" onPress={() => navigation.navigate('thesis.registration')} variant="secondary" />
      </Card>
    </ScreenShell>
  );
}

export function ThesisDetailScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Topic detail" eyebrow="TH-024 · Open" subtitle="AI-enabled library assistant">
      <Card style={styles.detailCard}>
        <View style={styles.topicHeader}>
          <View style={styles.topicCopy}>
            <UiText variant="headlineSmall">AI-enabled library assistant</UiText>
            <UiText variant="bodySmall" tone="muted">Supervisor · Dr. Le Minh</UiText>
          </View>
          <Badge label="Open" tone="success" />
        </View>
        <Divider />
        <UiText variant="label">Research brief</UiText>
        <UiText variant="bodySmall" tone="muted" style={styles.copy}>
          Design a grounded assistant that helps students discover library resources while keeping recommendations transparent and useful.
        </UiText>
        <View style={styles.tagRow}>
          <Badge label="NLP" tone="primary" />
          <Badge label="Student success" tone="neutral" />
          <Badge label="12 credits" tone="neutral" />
        </View>
      </Card>
      <ScreenSpacer />
      <Card tone="low">
        <ListRow leading="1" title="Research scope" subtitle="Discovery, retrieval, and response quality" />
        <Divider />
        <ListRow leading="2" title="Expected output" subtitle="Prototype, evaluation report, and handoff" />
        <Divider />
        <ListRow leading="3" title="Supervisor availability" subtitle="Tuesday and Thursday · 14:00 – 16:00" />
      </Card>
      <ScreenSpacer />
      <Button label="Register this topic" onPress={() => navigation.navigate('thesis.registration')} />
      <Button label="View evaluation criteria" onPress={() => navigation.navigate('thesis.evaluation')} variant="secondary" style={styles.secondaryButton} />
    </ScreenShell>
  );
}

export function ThesisRegistrationScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Thesis registration" eyebrow="Step 2 of 3" subtitle="Confirm your group and preferred topic.">
      <Card tone="primary" style={styles.registrationProgress}>
        <View style={styles.stepRow}>
          <Badge label="1 · Group" tone="success" />
          <Badge label="2 · Topic" tone="primary" />
          <Badge label="3 · Confirm" tone="neutral" />
        </View>
        <ProgressBar label="Registration progress" value={66} />
      </Card>
      <ScreenSpacer />
      <Card>
        <SectionHeading title="Selected topic" />
        <UiText variant="headlineSmall">AI-enabled library assistant</UiText>
        <UiText variant="bodySmall" tone="muted" style={styles.copy}>TH-024 · Dr. Le Minh · 12 credits</UiText>
        <Button label="Change topic" onPress={() => navigation.navigate('thesis.topics')} variant="text" />
      </Card>
      <ScreenSpacer />
      <Card>
        <SectionHeading title="Group members" />
        <ListRow leading="M" title="Nguyen Duc Minh" subtitle="Group leader · 2022SE0417" />
        <Divider />
        <ListRow leading="A" title="Pham Anh Thu" subtitle="Member · 2022SE0398" />
        <Divider />
        <ListRow leading="K" title="Le Khanh Vy" subtitle="Member · 2022SE0421" />
      </Card>
      <ScreenSpacer />
      <Button label="Continue to confirmation" onPress={() => navigation.navigate('thesis.progress')} />
    </ScreenShell>
  );
}

export function ThesisProgressScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Thesis progress" eyebrow="TH-024 · In progress" subtitle="A calm view of your research milestones.">
      <Card tone="primary" style={styles.progressHero}>
        <View style={styles.topicHeader}>
          <View style={styles.topicCopy}>
            <UiText variant="label">Overall progress</UiText>
            <UiText variant="display" tone="primary">42%</UiText>
          </View>
          <Badge label="On track" tone="success" />
        </View>
        <ProgressBar value={42} tone="success" />
      </Card>
      <ScreenSpacer />
      <SectionHeading title="Milestones" actionLabel="Evaluation" onAction={() => navigation.navigate('thesis.evaluation')} />
      <Card>
        {[
          { title: 'Topic approved', date: '12 Aug 2026', status: 'Done', tone: 'success' as const, mark: '✓' },
          { title: 'Literature review', date: 'Due 30 Aug 2026', status: 'In progress', tone: 'primary' as const, mark: '2' },
          { title: 'Prototype review', date: 'Due 20 Sep 2026', status: 'Upcoming', tone: 'neutral' as const, mark: '3' },
          { title: 'Final defense', date: 'Due 18 Dec 2026', status: 'Upcoming', tone: 'neutral' as const, mark: '4' },
        ].map((milestone, index, list) => (
          <View key={milestone.title}>
            <ListRow
              leading={milestone.mark}
              meta={milestone.status}
              subtitle={milestone.date}
              title={milestone.title}
              trailing={<Badge label={milestone.status} tone={milestone.tone} />}
            />
            {index < list.length - 1 ? <Divider /> : null}
          </View>
        ))}
      </Card>
      <ScreenSpacer />
      <Button label="Ask the assistant" onPress={() => navigation.navigate('assistant.chat')} variant="secondary" />
    </ScreenShell>
  );
}

export function ThesisEvaluationScreen({ navigation }: MobileScreenProps) {
  return (
    <ScreenShell title="Thesis evaluation" eyebrow="Assessment guide" subtitle="Know what good looks like before your next review.">
      <Card tone="primary" style={styles.evaluationHero}>
        <UiText variant="meta" tone="primary">NEXT REVIEW</UiText>
        <UiText variant="headlineSmall" style={styles.heroTitle}>Prototype review · 20 September</UiText>
        <UiText variant="bodySmall" tone="muted">Your supervisor will score the four criteria below.</UiText>
      </Card>
      <ScreenSpacer />
      <SectionHeading title="Criteria" />
      <Card>
        {[
          { title: 'Problem framing', value: 80, detail: 'Clear users, context, and measurable need' },
          { title: 'Method quality', value: 60, detail: 'Evidence supports the selected approach' },
          { title: 'Prototype fidelity', value: 35, detail: 'Workflow is testable end to end' },
          { title: 'Reflection', value: 20, detail: 'Risks and next steps are documented' },
        ].map((criterion, index, list) => (
          <View key={criterion.title}>
            <UiText variant="label">{criterion.title}</UiText>
            <UiText variant="bodySmall" tone="muted">{criterion.detail}</UiText>
            <ProgressBar value={criterion.value} label="Readiness" />
            {index < list.length - 1 ? <Divider /> : null}
          </View>
        ))}
      </Card>
      <ScreenSpacer />
      <Button label="Back to progress" onPress={() => navigation.navigate('thesis.progress')} variant="secondary" />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  hero: { padding: tokens.spacing.lg },
  heroTitle: { marginBottom: tokens.spacing.xs, marginTop: tokens.spacing.sm },
  topicCard: { marginBottom: tokens.spacing.sm },
  topicHeader: { alignItems: 'flex-start', flexDirection: 'row', justifyContent: 'space-between' },
  topicCopy: { flex: 1, paddingRight: tokens.spacing.sm },
  tipCard: { marginTop: tokens.spacing.sm },
  copy: { marginBottom: tokens.spacing.md, marginTop: tokens.spacing.xs },
  detailCard: { padding: tokens.spacing.lg },
  tagRow: { flexDirection: 'row', flexWrap: 'wrap' },
  secondaryButton: { marginTop: tokens.spacing.sm },
  registrationProgress: { padding: tokens.spacing.lg },
  stepRow: { flexDirection: 'row', flexWrap: 'wrap', marginBottom: tokens.spacing.sm },
  progressHero: { padding: tokens.spacing.lg },
  evaluationHero: { padding: tokens.spacing.lg },
});
