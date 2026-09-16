'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import {
  thesisApi,
  type ThesisGroup,
  type ThesisRound,
  type ThesisTopic,
} from '@/lib/thesis-api';

export function useThesisWorkspace(
  initialRoundId = '',
  options?: { topicsEnabled?: boolean },
) {
  // The catalog gates topic loading behind a search query (course
  // requirement: the list only appears after searching), so the hook can be
  // told not to fetch topics at all.
  const topicsEnabled = options?.topicsEnabled ?? true;
  const { user } = useAuth();
  const { messages } = useI18n();
  const loadFailedMessage = messages.thesis.loadFailed;
  const thesisStatus = messages.thesis.status;
  const commonStatuses = messages.common.statuses;
  const [rounds, setRounds] = useState<ThesisRound[]>([]);
  const [topics, setTopics] = useState<ThesisTopic[]>([]);
  const [groups, setGroups] = useState<ThesisGroup[]>([]);
  const [selectedRoundId, setSelectedRoundId] = useState(initialRoundId);
  const [roundsLoading, setRoundsLoading] = useState(true);
  const [workspaceLoading, setWorkspaceLoading] = useState(false);
  const [error, setError] = useState('');

  const loadRounds = useCallback(async () => {
    setRoundsLoading(true);
    setError('');
    try {
      const nextRounds = await thesisApi.listRounds();
      setRounds(nextRounds);
      setSelectedRoundId((current) => {
        if (initialRoundId && nextRounds.some((round) => round.id === initialRoundId)) {
          return initialRoundId;
        }
        return current || nextRounds[0]?.id || '';
      });
    } catch {
      setError(loadFailedMessage);
    } finally {
      setRoundsLoading(false);
    }
  }, [initialRoundId, loadFailedMessage]);

  useEffect(() => {
    void loadRounds();
  }, [loadRounds]);

  const refreshWorkspace = useCallback(async () => {
    if (!selectedRoundId) {
      setTopics([]);
      setGroups([]);
      return;
    }
    setWorkspaceLoading(true);
    setError('');
    try {
      const nextGroups = await thesisApi.listGroups(selectedRoundId);
      const nextTopics = topicsEnabled ? await thesisApi.listTopics(selectedRoundId) : [];
      setTopics(nextTopics);
      setGroups(nextGroups);
    } catch {
      setError(loadFailedMessage);
    } finally {
      setWorkspaceLoading(false);
    }
  }, [loadFailedMessage, selectedRoundId, topicsEnabled]);

  useEffect(() => {
    void refreshWorkspace();
  }, [refreshWorkspace]);

  const selectedRound = useMemo(
    () => rounds.find((round) => round.id === selectedRoundId),
    [rounds, selectedRoundId],
  );
  const studentId = user?.studentId ?? '';
  const currentGroup = useMemo(
    () =>
      groups.find(
        (group) =>
          group.leaderStudentId === studentId ||
          group.memberStudentIds.includes(studentId) ||
          group.members?.some((m) => m.studentId === studentId),
      ),
    [groups, studentId],
  );
  const statusLabel = useCallback(
    (status: string) =>
      thesisStatus[status as keyof typeof thesisStatus] ??
      commonStatuses[status.toUpperCase() as keyof typeof commonStatuses] ??
      commonStatuses.UNKNOWN,
    [commonStatuses, thesisStatus],
  );

  return {
    rounds,
    topics,
    groups,
    selectedRoundId,
    setSelectedRoundId,
    selectedRound,
    currentGroup,
    statusLabel,
    isLoading: roundsLoading || workspaceLoading,
    error,
    reload: loadRounds,
    refreshWorkspace,
  };
}
