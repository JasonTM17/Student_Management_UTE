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
  const topicCacheRef = useState(() => new Map<string, ThesisTopic[]>())[0];

  const loadRounds = useCallback(async () => {
    setRoundsLoading(true);
    setError('');
    try {
      const nextRounds = await thesisApi.listRounds();
      const sortedRounds = [...nextRounds].sort((a, b) => {
        // Prioritize canonical registration round holding the full topic catalog
        if (a.id === '22222222-2222-2222-2222-222222222101') return -1;
        if (b.id === '22222222-2222-2222-2222-222222222101') return 1;
        const isOpenA = a.status === 'REGISTRATION_OPEN' ? 0 : 1;
        const isOpenB = b.status === 'REGISTRATION_OPEN' ? 0 : 1;
        if (isOpenA !== isOpenB) return isOpenA - isOpenB;
        return (b.registrationStart || '').localeCompare(a.registrationStart || '');
      });
      setRounds(sortedRounds);
      setSelectedRoundId((current) => {
        if (initialRoundId && sortedRounds.some((round) => round.id === initialRoundId)) {
          return initialRoundId;
        }
        if (current && sortedRounds.some((round) => round.id === current)) {
          return current;
        }
        const preferred = sortedRounds.find(
          (r) => r.id === '22222222-2222-2222-2222-222222222101' && r.status === 'REGISTRATION_OPEN',
        ) || sortedRounds.find(
          (r) => r.status === 'REGISTRATION_OPEN',
        ) || sortedRounds[0];
        return preferred?.id || '';
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
    // Fast path: render cached topics instantly if available
    if (topicsEnabled && topicCacheRef.has(selectedRoundId)) {
      setTopics(topicCacheRef.get(selectedRoundId)!);
    }
    setWorkspaceLoading(true);
    setError('');
    try {
      const [nextGroups, nextTopics] = await Promise.all([
        thesisApi.listGroups(selectedRoundId),
        topicsEnabled ? thesisApi.listTopics(selectedRoundId) : Promise.resolve([]),
      ]);
      if (topicsEnabled) {
        topicCacheRef.set(selectedRoundId, nextTopics);
      }
      setTopics(nextTopics);
      setGroups(nextGroups);
    } catch {
      setError(loadFailedMessage);
    } finally {
      setWorkspaceLoading(false);
    }
  }, [loadFailedMessage, selectedRoundId, topicCacheRef, topicsEnabled]);

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

  const cohortGroups = useMemo(() => {
    const map = new Map<string, ThesisRound[]>();
    for (const r of rounds) {
      const match = r.name.match(/Ni\u00ean kh\u00f3a\s+\d{4}\s*[-–]\s*\d{4}/i);
      const cohortKey = match ? match[0] : (r.thesisType === 'KLTN' ? 'Khóa luận Tốt nghiệp' : 'Học phần Tốt nghiệp / Chuyên ngành');
      if (!map.has(cohortKey)) {
        map.set(cohortKey, []);
      }
      map.get(cohortKey)!.push(r);
    }
    return Array.from(map.entries()).map(([cohort, groupedRounds]) => ({
      cohort,
      rounds: groupedRounds,
    }));
  }, [rounds]);

  return {
    rounds,
    cohortGroups,
    topics,
    groups,
    selectedRoundId,
    setSelectedRoundId,
    selectedRound,
    currentGroup,
    statusLabel,
    isLoading: roundsLoading || workspaceLoading,
    roundsLoading,
    workspaceLoading,
    error,
    reload: loadRounds,
    refreshWorkspace,
  };
}
