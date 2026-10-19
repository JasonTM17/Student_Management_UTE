'use client';

import { useCallback, useEffect, useMemo, useRef, useState, type SetStateAction } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { pickPreferredThesisRound } from '@/lib/thesis-round';
import {
  thesisApi,
  type ThesisGroup,
  type ThesisRound,
  type ThesisTopic,
} from '@/lib/thesis-api';

export function useThesisWorkspace(
  initialRoundId = '',
  options?: { topicsEnabled?: boolean; groupsEnabled?: boolean },
) {
  // The catalog gates topic loading behind a search query (course
  // requirement: the list only appears after searching), so the hook can be
  // told not to fetch topics at all.
  const topicsEnabled = options?.topicsEnabled ?? true;
  const groupsEnabled = options?.groupsEnabled ?? true;
  const { user, isStudent } = useAuth();
  const { messages } = useI18n();
  const loadFailedMessage = messages.thesis.loadFailed;
  const thesisStatus = messages.thesis.status;
  const commonStatuses = messages.common.statuses;
  const [rounds, setRounds] = useState<ThesisRound[]>([]);
  const [topics, setTopics] = useState<ThesisTopic[]>([]);
  const [groups, setGroups] = useState<ThesisGroup[]>([]);
  const [selectedRoundId, setRoundId] = useState(initialRoundId);
  const appliedInitialRoundRef = useRef(initialRoundId);
  const manualSelectionRef = useRef(false);
  const membershipSelectedRef = useRef(false);
  const [myGroups, setMyGroups] = useState<ThesisGroup[] | null>(null);
  const [membershipError, setMembershipError] = useState('');
  const [membershipLoading, setMembershipLoading] = useState(isStudent);
  const [membershipReloadToken, setMembershipReloadToken] = useState(0);
  const roundsRequestRef = useRef(0);
  const workspaceRequestRef = useRef(0);
  const identity = `${user?.id ?? ''}:${user?.studentId ?? ''}`;
  const currentScopeRef = useRef({ selectedRoundId, identity });
  currentScopeRef.current = { selectedRoundId, identity };
  const setSelectedRoundId = useCallback((value: SetStateAction<string>) => {
    const roundId = typeof value === 'function' ? value(currentScopeRef.current.selectedRoundId) : value;
    manualSelectionRef.current = true;
    // Invalidate synchronously, including mutation callbacks holding old closures.
    workspaceRequestRef.current += 1;
    currentScopeRef.current.selectedRoundId = roundId;
    setRoundId(roundId);
  }, []);
  const [roundsLoading, setRoundsLoading] = useState(true);
  const [workspaceLoading, setWorkspaceLoading] = useState(false);
  const [error, setError] = useState('');
  const topicCacheRef = useState(() => new Map<string, ThesisTopic[]>())[0];

  const loadRounds = useCallback(async () => {
    const requestId = ++roundsRequestRef.current;
    setRoundsLoading(true);
    setError('');
    try {
      const nextRounds = await thesisApi.listRounds();
      if (requestId !== roundsRequestRef.current) return;
      const sortedRounds = [...nextRounds].sort((a, b) => {
        // Academic rule priority: OPEN registration > KLTN thesis type > newest start date
        const isOpenA = a.status === 'REGISTRATION_OPEN' ? 0 : 1;
        const isOpenB = b.status === 'REGISTRATION_OPEN' ? 0 : 1;
        if (isOpenA !== isOpenB) return isOpenA - isOpenB;
        const isKltnA = a.thesisType === 'KLTN' ? 0 : 1;
        const isKltnB = b.thesisType === 'KLTN' ? 0 : 1;
        if (isKltnA !== isKltnB) return isKltnA - isKltnB;
        return (b.registrationStart || '').localeCompare(a.registrationStart || '');
      });
      setRounds(sortedRounds);
      const explicitRoundChanged = appliedInitialRoundRef.current !== initialRoundId;
      appliedInitialRoundRef.current = initialRoundId;
      setRoundId((current) => {
        if (explicitRoundChanged && initialRoundId && sortedRounds.some((round) => round.id === initialRoundId)) {
          return initialRoundId;
        }
        if (current && sortedRounds.some((round) => round.id === current)) {
          return current;
        }
        if (initialRoundId && sortedRounds.some((round) => round.id === initialRoundId)) {
          return initialRoundId;
        }
        const preferred =
          sortedRounds.find((r) => r.status === 'REGISTRATION_OPEN' && r.thesisType === 'KLTN') ||
          sortedRounds.find((r) => r.status === 'REGISTRATION_OPEN') ||
          sortedRounds.find((r) => r.status === 'REGISTRATION_CLOSED' && r.thesisType === 'KLTN') ||
          sortedRounds.find((r) => r.status === 'REGISTRATION_CLOSED') ||
          sortedRounds[0];
        return preferred?.id || '';
      });
    } catch {
      if (requestId === roundsRequestRef.current) setError(loadFailedMessage);
    } finally {
      if (requestId === roundsRequestRef.current) setRoundsLoading(false);
    }
  }, [initialRoundId, loadFailedMessage]);

  useEffect(() => {
    void loadRounds();
    return () => { roundsRequestRef.current += 1; };
  }, [loadRounds]);

  useEffect(() => {
    membershipSelectedRef.current = false;
    manualSelectionRef.current = false;
    topicCacheRef.clear();
  }, [identity, isStudent, topicCacheRef]);

  useEffect(() => {
    let cancelled = false;
    setMyGroups(null);
    setMembershipError('');
    setMembershipLoading(isStudent);
    if (isStudent) {
      void thesisApi.myGroups().then((mine) => {
        if (!cancelled) setMyGroups(mine);
      }).catch(() => {
        if (!cancelled) setMembershipError(loadFailedMessage);
      }).finally(() => {
        if (!cancelled) setMembershipLoading(false);
      });
    }
    return () => { cancelled = true; };
  }, [identity, isStudent, loadFailedMessage, membershipReloadToken, topicCacheRef]);

  useEffect(() => {
    if (!isStudent || initialRoundId || manualSelectionRef.current || membershipSelectedRef.current) return;
    const preferred = pickPreferredThesisRound(rounds, myGroups);
    if (preferred) {
      membershipSelectedRef.current = true;
      setRoundId(preferred);
    }
  }, [initialRoundId, isStudent, myGroups, rounds]);

  const refreshWorkspace = useCallback(async () => {
    if (currentScopeRef.current.selectedRoundId !== selectedRoundId || currentScopeRef.current.identity !== identity) return;
    const requestId = ++workspaceRequestRef.current;
    const isCurrent = () => requestId === workspaceRequestRef.current
      && currentScopeRef.current.selectedRoundId === selectedRoundId
      && currentScopeRef.current.identity === identity;
    if (!selectedRoundId) {
      setTopics([]);
      setGroups([]);
      setWorkspaceLoading(false);
      return;
    }
    setTopics(topicsEnabled ? topicCacheRef.get(selectedRoundId) ?? [] : []);
    setGroups([]);
    setWorkspaceLoading(true);
    setError('');
    try {
      const [nextGroups, nextTopics] = await Promise.all([
        groupsEnabled ? thesisApi.listGroups(selectedRoundId) : Promise.resolve([]),
        topicsEnabled ? thesisApi.listTopics(selectedRoundId) : Promise.resolve([]),
      ]);
      if (!isCurrent()) return;
      if (topicsEnabled) {
        topicCacheRef.set(selectedRoundId, nextTopics);
      }
      setTopics(nextTopics);
      setGroups(nextGroups);
    } catch {
      if (isCurrent()) setError(loadFailedMessage);
    } finally {
      if (isCurrent()) setWorkspaceLoading(false);
    }
  }, [groupsEnabled, identity, loadFailedMessage, selectedRoundId, topicCacheRef, topicsEnabled]);

  useEffect(() => {
    void refreshWorkspace();
    return () => { workspaceRequestRef.current += 1; };
  }, [refreshWorkspace]);

  const selectedRound = useMemo(
    () => rounds.find((round) => round.id === selectedRoundId),
    [rounds, selectedRoundId],
  );
  const reload = useCallback(() => {
    setMembershipReloadToken((token) => token + 1);
    return loadRounds();
  }, [loadRounds]);
  const studentId = user?.studentId ?? '';
  const currentGroup = useMemo(() => {
    const ownGroups = groups.filter(
      (group) =>
        group.leaderStudentId === studentId ||
        group.memberStudentIds.includes(studentId) ||
        group.members?.some((m) => m.studentId === studentId),
    );
    // A newer cancelled membership must never shadow an active group the
    // student already belongs to in the same round.
    return ownGroups.find((group) => group.status !== 'CANCELLED') ?? ownGroups[0];
  }, [groups, studentId]);
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
    isLoading: roundsLoading || workspaceLoading || membershipLoading,
    roundsLoading,
    workspaceLoading,
    error: membershipError || error,
    reload,
    refreshWorkspace,
  };
}
