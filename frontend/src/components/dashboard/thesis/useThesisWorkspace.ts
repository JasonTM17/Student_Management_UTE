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

export function useThesisWorkspace(initialRoundId = '') {
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

  useEffect(() => {
    if (!selectedRoundId) {
      setTopics([]);
      setGroups([]);
      return;
    }

    let cancelled = false;
    const loadWorkspace = async () => {
      setWorkspaceLoading(true);
      setError('');
      try {
        const [nextTopics, nextGroups] = await Promise.all([
          thesisApi.listTopics(selectedRoundId),
          thesisApi.listGroups(selectedRoundId),
        ]);
        if (cancelled) return;
        setTopics(nextTopics);
        setGroups(nextGroups);
      } catch {
        if (!cancelled) setError(loadFailedMessage);
      } finally {
        if (!cancelled) setWorkspaceLoading(false);
      }
    };

    void loadWorkspace();
    return () => {
      cancelled = true;
    };
  }, [loadFailedMessage, selectedRoundId]);

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
          group.memberStudentIds.includes(studentId),
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
  };
}
