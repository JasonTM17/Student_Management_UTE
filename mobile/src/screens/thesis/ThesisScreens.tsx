import { useCallback, useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';

import {
  ApiClientError,
  apiClient,
  campusApi,
  type MobileThesisGroup,
  type MobileThesisRound,
  type MobileThesisTopic,
} from '../../api/client';
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

const previewRound: MobileThesisRound = {
  id: 'preview-round',
  name: 'Course thesis round',
  status: 'REGISTRATION_OPEN',
  registrationStart: '2026-08-25T00:00:00Z',
  registrationEnd: '2026-08-30T23:59:59Z',
};

const previewTopics: MobileThesisTopic[] = [
  {
    id: 'preview-topic',
    roundId: previewRound.id,
    departmentId: 'FIT',
    title: 'Academic portal assistant',
    description: 'Build a transparent assistant over curated academic guidance.',
    maxGroups: 3,
    status: 'PUBLISHED',
  },
];

interface ThesisWorkspace {
  round: MobileThesisRound | null;
  topics: MobileThesisTopic[];
  groups: MobileThesisGroup[];
  loading: boolean;
  error: string | null;
  refresh(): Promise<void>;
}

function getErrorMessage(error: unknown) {
  return error instanceof ApiClientError ? error.message : 'Thesis data is unavailable.';
}

function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? value : date.toLocaleDateString('vi-VN');
}

function statusTone(status: string): 'neutral' | 'primary' | 'success' | 'warning' | 'error' {
  if (['PUBLISHED', 'APPROVED', 'COMPLETED'].includes(status)) return 'success';
  if (['REGISTRATION_OPEN', 'SUBMITTED'].includes(status)) return 'primary';
  if (['REJECTED', 'CANCELLED'].includes(status)) return 'error';
  if (['REGISTRATION_CLOSED', 'DRAFT'].includes(status)) return 'warning';
  return 'neutral';
}

function useThesisWorkspace(): ThesisWorkspace {
  const [round, setRound] = useState<MobileThesisRound | null>(null);
  const [topics, setTopics] = useState<MobileThesisTopic[]>([]);
  const [groups, setGroups] = useState<MobileThesisGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    if (apiClient.mode === 'preview') {
      setRound(previewRound);
      setTopics(previewTopics);
      setGroups([]);
      setLoading(false);
      return;
    }

    try {
      const rounds = await campusApi.thesisRounds();
      const activeRound = rounds.find((item) => item.status === 'REGISTRATION_OPEN')
        ?? rounds.find((item) => item.status === 'PROPOSALS_PUBLISHED')
        ?? rounds[0]
        ?? null;
      setRound(activeRound);
      if (!activeRound) {
        setTopics([]);
        setGroups([]);
        return;
      }

      const [availableTopics, availableGroups, account] = await Promise.all([
        campusApi.thesisTopics(activeRound.id),
        campusApi.thesisGroups(activeRound.id),
        campusApi.account(),
      ]);
      const studentId = account.studentId;
      setTopics(availableTopics);
      setGroups(studentId
        ? availableGroups.filter((group) => group.leaderStudentId === studentId || group.memberStudentIds.includes(studentId))
        : []);
    } catch (nextError) {
      setError(getErrorMessage(nextError));
      setTopics([]);
      setGroups([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { round, topics, groups, loading, error, refresh };
}

function WorkspaceState({ loading, error }: Pick<ThesisWorkspace, 'loading' | 'error'>) {
  if (loading) {
    return <Card tone="low"><UiText variant="bodySmall" tone="muted">Loading thesis workspace...</UiText></Card>;
  }
  if (error) {
    return <Card tone="low"><UiText variant="bodySmall" tone="error">{error}</UiText></Card>;
  }
  return null;
}

export function ThesisTopicsScreen({ navigation }: MobileScreenProps) {
  const workspace = useThesisWorkspace();

  return (
    <ScreenShell title="Thesis topics" eyebrow="Thesis core" subtitle="Published topics from the Java REST API.">
      {workspace.round ? (
        <Card tone="primary" style={styles.hero}>
          <UiText variant="meta" tone="primary">{workspace.round.status.replaceAll('_', ' ')}</UiText>
          <UiText variant="headlineSmall" style={styles.heroTitle}>{workspace.round.name}</UiText>
          <UiText variant="bodySmall" tone="muted">
            Registration closes {formatDate(workspace.round.registrationEnd)}.
          </UiText>
        </Card>
      ) : null}
      <ScreenSpacer />
      <WorkspaceState loading={workspace.loading} error={workspace.error} />
      {!workspace.loading && !workspace.error ? (
        <>
          <SectionHeading title="Published topics" actionLabel="My group" onAction={() => navigation.navigate('thesis.progress')} />
          {workspace.topics.map((topic) => (
            <Card key={topic.id} style={styles.topicCard}>
              <View style={styles.topicHeader}>
                <View style={styles.topicCopy}>
                  <UiText variant="meta" tone="primary">{topic.departmentId}</UiText>
                  <UiText variant="headlineSmall">{topic.title}</UiText>
                  <UiText variant="bodySmall" tone="muted" numberOfLines={3}>{topic.description}</UiText>
                </View>
                <Badge label={topic.status} tone={statusTone(topic.status)} />
              </View>
              <Button
                label="View topic"
                onPress={() => navigation.navigate('thesis.detail', { thesisTopicId: topic.id })}
                variant="text"
              />
            </Card>
          ))}
          {workspace.topics.length === 0 ? (
            <Card tone="low"><UiText variant="bodySmall" tone="muted">No published topics are available for this round.</UiText></Card>
          ) : null}
        </>
      ) : null}
    </ScreenShell>
  );
}

export function ThesisDetailScreen({ navigation, selectedThesisTopicId }: MobileScreenProps) {
  const workspace = useThesisWorkspace();
  const topic = workspace.topics.find((item) => item.id === selectedThesisTopicId);

  return (
    <ScreenShell title="Topic detail" eyebrow={topic?.departmentId ?? 'Thesis core'} subtitle={topic?.title ?? 'Published topic'}>
      <WorkspaceState loading={workspace.loading} error={workspace.error} />
      {topic ? (
        <>
          <Card style={styles.detailCard}>
            <View style={styles.topicHeader}>
              <View style={styles.topicCopy}>
                <UiText variant="headlineSmall">{topic.title}</UiText>
                <UiText variant="bodySmall" tone="muted">Round: {workspace.round?.name ?? topic.roundId}</UiText>
              </View>
              <Badge label={topic.status} tone={statusTone(topic.status)} />
            </View>
            <Divider />
            <UiText variant="label">Research brief</UiText>
            <UiText variant="bodySmall" tone="muted" style={styles.copy}>{topic.description}</UiText>
            <UiText variant="meta" tone="muted">Maximum {topic.maxGroups} groups</UiText>
          </Card>
          <ScreenSpacer />
          <Button
            label="Register this topic"
            onPress={() => navigation.navigate('thesis.registration', { thesisTopicId: topic.id })}
          />
        </>
      ) : !workspace.loading && !workspace.error ? (
        <Card tone="low"><UiText variant="bodySmall" tone="muted">Select a published topic when one becomes available.</UiText></Card>
      ) : null}
    </ScreenShell>
  );
}

export function ThesisRegistrationScreen({ navigation, selectedThesisTopicId }: MobileScreenProps) {
  const workspace = useThesisWorkspace();
  const [working, setWorking] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const group = workspace.groups[0];
  const topic = workspace.topics.find(
    (item) => item.id === (selectedThesisTopicId ?? group?.topicId),
  );
  const canRegister = Boolean(workspace.round && topic && workspace.round.status === 'REGISTRATION_OPEN');

  const register = async () => {
    if (!workspace.round || !topic || apiClient.mode === 'preview') return;
    setWorking(true);
    setNotice(null);
    try {
      const targetGroup = group ?? await campusApi.createThesisGroup(workspace.round.id);
      if (targetGroup.topicId !== topic.id) {
        await campusApi.assignThesisTopic(targetGroup.id, topic.id);
      }
      setNotice('Topic registration saved.');
      await workspace.refresh();
    } catch (error) {
      setNotice(getErrorMessage(error));
    } finally {
      setWorking(false);
    }
  };

  return (
    <ScreenShell title="Thesis registration" eyebrow="Group and topic" subtitle="One live registration path with clear conflicts.">
      <WorkspaceState loading={workspace.loading} error={workspace.error} />
      {workspace.round && topic ? (
        <>
          <Card tone="primary" style={styles.registrationProgress}>
            <View style={styles.stepRow}>
              <Badge label={group ? 'Group ready' : 'Create group'} tone={group ? 'success' : 'warning'} />
              <Badge label={group?.topicId ? 'Topic assigned' : 'Choose topic'} tone={group?.topicId ? 'success' : 'primary'} />
            </View>
            <ProgressBar label="Registration progress" value={group?.topicId ? 100 : group ? 50 : 0} />
          </Card>
          <ScreenSpacer />
          <Card>
            <SectionHeading title="Selected topic" />
            <UiText variant="headlineSmall">{topic.title}</UiText>
            <UiText variant="bodySmall" tone="muted" style={styles.copy}>{topic.departmentId} - {topic.status}</UiText>
            <Button label="Change topic" onPress={() => navigation.navigate('thesis.topics')} variant="text" />
          </Card>
          <ScreenSpacer />
          <Card>
            <SectionHeading title="My group" />
            {group ? (
              group.memberStudentIds.map((studentId, index) => (
                <View key={studentId}>
                  <ListRow leading={studentId.slice(0, 1)} title={studentId} subtitle={studentId === group.leaderStudentId ? 'Group leader' : 'Member'} />
                  {index < group.memberStudentIds.length - 1 ? <Divider /> : null}
                </View>
              ))
            ) : (
              <UiText variant="bodySmall" tone="muted">A group will be created with you as leader.</UiText>
            )}
          </Card>
          <ScreenSpacer />
          {notice ? <UiText variant="bodySmall" tone={notice.endsWith('saved.') ? 'success' : 'error'} style={styles.notice}>{notice}</UiText> : null}
          <Button
            label={apiClient.mode === 'preview' ? 'Preview only' : group?.topicId === topic.id ? 'Registration saved' : 'Confirm registration'}
            onPress={() => void register()}
            disabled={apiClient.mode === 'preview' || !canRegister || group?.topicId === topic.id}
            loading={working}
          />
        </>
      ) : !workspace.loading && !workspace.error ? (
        <Card tone="low"><UiText variant="bodySmall" tone="muted">Registration is unavailable until a round and published topic are open.</UiText></Card>
      ) : null}
    </ScreenShell>
  );
}

export function ThesisProgressScreen({ navigation }: MobileScreenProps) {
  const workspace = useThesisWorkspace();
  const group = workspace.groups[0];
  const topic = workspace.topics.find((item) => item.id === group?.topicId);
  const progress = !group ? 0 : group.status === 'COMPLETED' ? 100 : group.topicId ? 60 : 30;

  return (
    <ScreenShell title="Thesis progress" eyebrow="My group" subtitle="Current group, topic, and status from the Java API.">
      <WorkspaceState loading={workspace.loading} error={workspace.error} />
      {group ? (
        <>
          <Card tone="primary" style={styles.progressHero}>
            <View style={styles.topicHeader}>
              <View style={styles.topicCopy}>
                <UiText variant="label">{topic?.title ?? 'Topic not assigned'}</UiText>
                <UiText variant="display" tone="primary">{progress}%</UiText>
              </View>
              <Badge label={group.status} tone={statusTone(group.status)} />
            </View>
            <ProgressBar value={progress} tone={progress === 100 ? 'success' : 'primary'} />
          </Card>
          <ScreenSpacer />
          <SectionHeading title="Registration status" />
          <Card>
            <ListRow leading="1" title="Group created" subtitle={`${group.memberStudentIds.length} member(s)`} trailing={<Badge label="Done" tone="success" />} />
            <Divider />
            <ListRow leading="2" title="Topic assignment" subtitle={topic?.title ?? 'Choose a published topic'} trailing={<Badge label={group.topicId ? 'Done' : 'Pending'} tone={group.topicId ? 'success' : 'warning'} />} />
            <Divider />
            <ListRow leading="3" title="Approval" subtitle={group.approvalStatus} trailing={<Badge label={group.approvalStatus} tone={statusTone(group.approvalStatus)} />} />
          </Card>
          <ScreenSpacer />
          <Button label="Ask the assistant" onPress={() => navigation.navigate('assistant.chat')} variant="secondary" />
        </>
      ) : !workspace.loading && !workspace.error ? (
        <Card tone="low">
          <UiText variant="bodySmall" tone="muted" style={styles.copy}>You do not have a thesis group in the active round.</UiText>
          <Button label="Start registration" onPress={() => navigation.navigate('thesis.registration')} variant="secondary" />
        </Card>
      ) : null}
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  hero: { padding: tokens.spacing.lg },
  heroTitle: { marginBottom: tokens.spacing.xs, marginTop: tokens.spacing.sm },
  topicCard: { marginBottom: tokens.spacing.sm },
  topicHeader: { alignItems: 'flex-start', flexDirection: 'row', justifyContent: 'space-between' },
  topicCopy: { flex: 1, paddingRight: tokens.spacing.sm },
  copy: { marginBottom: tokens.spacing.md, marginTop: tokens.spacing.xs },
  detailCard: { padding: tokens.spacing.lg },
  registrationProgress: { padding: tokens.spacing.lg },
  stepRow: { flexDirection: 'row', flexWrap: 'wrap', gap: tokens.spacing.sm, marginBottom: tokens.spacing.sm },
  progressHero: { padding: tokens.spacing.lg },
  notice: { marginBottom: tokens.spacing.sm },
});
