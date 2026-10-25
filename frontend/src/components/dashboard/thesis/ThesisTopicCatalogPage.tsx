'use client';

import { useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { ArrowUpRight, BookOpen, CheckCircle2, FileStack, GraduationCap, Loader2, Search, UserCheck } from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { LinkButton } from '@/components/ui/link-button';
import { Select } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { StatusBadge } from '@/components/thesis/StatusBadge';
import { TopicCardGridSkeleton } from '@/components/ui/skeleton';
import { normalizeVietnameseText } from '@/lib/registration-browse';
import { plainTextThesisDescription as plainTextDescription } from '@/lib/thesis-text';
import { roundsForPicker } from '@/lib/thesis-round';
import type { ThesisTopic } from '@/lib/thesis-api';
import { cn } from '@/lib/utils';
import { useThesisWorkspace } from './useThesisWorkspace';

export default function ThesisTopicCatalogPage() {
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth();
  const { messages, locale } = useI18n();
  const searchParams = useSearchParams();
  const explicitRoundId = searchParams.get('roundId') || '';
  const [searchInput, setSearchInput] = useState('');
  const [showAll, setShowAll] = useState(true);
  const [selectedSupervisorId, setSelectedSupervisorId] = useState('');
  const [submittedQuery, setSubmittedQuery] = useState('all');
  const searchActive = showAll || submittedQuery.trim().length > 0 || Boolean(selectedSupervisorId);
  const workspace = useThesisWorkspace(explicitRoundId, { topicsEnabled: searchActive, groupsEnabled: false });

  // Advisors ride inside each topic payload, so the "Choose advisor first"
  // step is built from the same list response — no per-topic fan-out.
  const uniqueSupervisors = useMemo(() => {
    const map = new Map<string, { lecturerId: string; name: string; topicCount: number }>();
    for (const topic of workspace.topics) {
      for (const s of topic.supervisors ?? []) {
        if (!s.lecturerId) continue;
        const fullName = [s.lastName, s.firstName].filter(Boolean).join(' ').trim() || s.lecturerId;
        const existing = map.get(s.lecturerId);
        if (existing) {
          existing.topicCount += 1;
        } else {
          map.set(s.lecturerId, { lecturerId: s.lecturerId, name: fullName, topicCount: 1 });
        }
      }
    }
    return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name));
  }, [workspace.topics]);

  const selectedSupervisor = useMemo(() => {
    if (!selectedSupervisorId) return null;
    return uniqueSupervisors.find((s) => s.lecturerId === selectedSupervisorId) ?? null;
  }, [selectedSupervisorId, uniqueSupervisors]);

  const getTopicSupervisorName = (topic: ThesisTopic) =>
    (topic.supervisors ?? [])
      .map((s) => [s.lastName, s.firstName].filter(Boolean).join(' ').trim() || s.lecturerId)
      .join(', ');

  const normalizedQuery = submittedQuery.trim().toLowerCase();
  const matchingTopics = useMemo(() => {
    if (!searchActive) return [];
    if (showAll || !normalizedQuery || normalizedQuery === 'all') {
      return workspace.topics;
    }
    const topicBilingualKeywords: Record<string, string[]> = {
      'artificial intelligence': ['artificial intelligence', 'ai', 'trí tuệ nhân tạo'],
      'software & web': ['software', 'web', 'phần mềm'],
      'robotics & mechatronics': ['robotics', 'mechatronics', 'cơ điện tử', 'robot'],
      'information systems': ['information systems', 'hệ thống thông tin'],
      'iot & telecom': ['iot', 'telecom', 'viễn thông'],
      'automotive': ['automotive', 'ô tô'],
      'civil engineering': ['civil engineering', 'xây dựng'],
    };

    const searchTerms = [normalizedQuery];
    for (const [key, synonyms] of Object.entries(topicBilingualKeywords)) {
      if (normalizedQuery.includes(key) || key.includes(normalizedQuery)) {
        searchTerms.push(...synonyms.map((s) => s.toLowerCase()));
      }
    }

    return workspace.topics.filter((topic) => {
      const fullText = normalizeVietnameseText(
        [topic.title, plainTextDescription(topic.description)].filter(Boolean).join(' '),
      );
      return searchTerms.some((term) => {
        const foldedTerm = normalizeVietnameseText(term);
        if (!foldedTerm) return true;
        if (fullText.includes(foldedTerm)) return true;
        const tokens = foldedTerm.split(/\s+/).filter(Boolean);
        return tokens.length > 0 && tokens.every((tok) => fullText.includes(tok));
      });
    });
  }, [normalizedQuery, searchActive, showAll, workspace.topics]);

  const displayTopics = useMemo(() => {
    if (!searchActive) return [];
    if (!selectedSupervisorId) return matchingTopics;
    return matchingTopics.filter((topic) =>
      (topic.supervisors ?? []).some((s) => s.lecturerId === selectedSupervisorId));
  }, [matchingTopics, searchActive, selectedSupervisorId]);

  if (authLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  if (workspace.roundsLoading && workspace.rounds.length === 0) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (workspace.error && workspace.rounds.length === 0) {
    return (
      <ErrorState
        title={messages.thesis.loadFailed}
        description={workspace.error}
        retryLabel={messages.thesis.retry}
        onRetry={() => void workspace.reload()}
      />
    );
  }

  const searchCopy = locale === 'vi'
    ? {
        label: 'Tìm kiếm đề tài',
        placeholder: 'Nhập tên đề tài, chuyên ngành hoặc từ khóa…',
        action: 'Tìm kiếm',
        viewAll: 'Xem tất cả đề tài đợt này',
        quickSuggest: 'Gợi ý chuyên ngành:',
        requiredTitle: 'Tìm kiếm để xem danh mục đề tài',
        requiredDescription:
          'Bạn có thể nhập từ khóa, chọn chuyên ngành gợi ý hoặc bấm "Xem tất cả đề tài đợt này".',
        noMatch: 'Không có đề tài nào khớp tiêu chí tìm kiếm.',
      }
    : {
        label: 'Search topics',
        placeholder: 'Enter topic title, specialization, or keyword…',
        action: 'Search',
        viewAll: 'Browse all topics in this round',
        quickSuggest: 'Suggested topics:',
        requiredTitle: 'Search to view the topic catalog',
        requiredDescription:
          'Enter a keyword, click a quick suggestion, or click "Browse all topics in this round".',
        noMatch: 'No topic matches your search criteria.',
      };

  const quickPills = locale === 'vi'
    ? [
        'Trí tuệ nhân tạo (AI)',
        'Phần mềm & Web',
        'Cơ điện tử & Robot',
        'Hệ thống thông tin',
        'IoT & Viễn thông',
        'Kỹ thuật Ô tô',
        'Xây dựng',
      ]
    : [
        'Artificial Intelligence',
        'Software & Web',
        'Robotics & Mechatronics',
        'Information Systems',
        'IoT & Telecom',
        'Automotive',
        'Civil Engineering',
      ];

  const handleSearch = (event: React.FormEvent) => {
    event.preventDefault();
    if (!searchInput.trim()) {
      setShowAll(true);
      setSubmittedQuery('all');
    } else {
      setShowAll(false);
      setSubmittedQuery(searchInput.trim());
    }
  };

  const handleSelectPill = (pill: string) => {
    const query = pill.split('(')[0].trim();
    setSearchInput(query);
    setShowAll(false);
    setSubmittedQuery(query);
  };

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.thesis.navigation.catalog}</SectionEyebrow>}
        title={messages.thesis.catalogTitle}
        description={messages.thesis.catalogDescription}
        actions={
          <LinkButton href="/dashboard/thesis" variant="outline">
            {messages.thesis.backToWorkspace}
          </LinkButton>
        }
      />

      {/* Executive Thesis Catalog Hero Banner */}
      <div className="overflow-hidden rounded-2xl border border-border/80 bg-card text-card-foreground shadow-xs">
        <div className="grid grid-cols-1 lg:grid-cols-12">
          <div className="flex flex-col justify-center p-6 lg:col-span-8 space-y-2.5">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-md bg-primary/10 px-2.5 py-0.5 text-[11px] font-bold uppercase tracking-wider text-primary border border-primary/20">
                {messages.thesis.navigation.catalog}
              </span>
              <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-semibold text-muted-foreground">
                HCMUTE Academic Engineering & Technology
              </span>
            </div>
            <h2 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
              {messages.thesis.catalogTitle}
            </h2>
            <p className="text-xs leading-relaxed text-muted-foreground sm:text-sm">
              {messages.thesis.catalogDescription}
            </p>
          </div>
          <div className="relative h-48 lg:h-full lg:col-span-4 overflow-hidden border-t lg:border-t-0 lg:border-l border-border/60 min-h-[160px]">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src="/images/banners/faculty_engineering_hall.jpg"
              alt="HCMUTE Faculty of Engineering Hall"
              className="h-full w-full object-cover object-center transition duration-500 hover:scale-105"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/50 via-transparent to-transparent lg:hidden" />
            <div className="absolute bottom-2 left-3 rounded-md bg-background/85 px-2 py-0.5 text-[11px] font-semibold text-foreground backdrop-blur-xs">
              {locale === 'vi' ? 'Khoa Kỹ thuật HCMUTE' : 'HCMUTE Faculty of Engineering'}
            </div>
          </div>
        </div>
      </div>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <Select
          label={messages.thesis.selectRound}
          value={workspace.selectedRoundId}
          onChange={(event) => workspace.setSelectedRoundId(event.target.value)}
          aria-label={messages.thesis.selectRound}
          groups={
            workspace.cohortGroups && workspace.cohortGroups.length > 0
              ? workspace.cohortGroups.map((group) => ({
                  label: group.cohort,
                  options: group.rounds.map((round) => ({ value: round.id, label: round.name })),
                }))
              : undefined
          }
          options={
            workspace.cohortGroups && workspace.cohortGroups.length > 0
              ? undefined
              : roundsForPicker(workspace.rounds, workspace.selectedRoundId)
                  .map((round) => ({ value: round.id, label: round.name }))
          }
        />
        {workspace.selectedRound ? (
          <div className="sm:self-end sm:pb-1">
            <StatusBadge status={workspace.selectedRound.status} />
          </div>
        ) : null}
      </div>

      {/* Step 1: Chọn Giảng viên hướng dẫn (GVHD) */}
      <div className="rounded-2xl border border-border/80 bg-card p-5 sm:p-6 shadow-xs space-y-4">
        <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2.5">
            <span className="flex h-7 w-7 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground shadow-xs">
              1
            </span>
            <div>
              <h3 className="text-base font-bold text-foreground">
                {locale === 'vi' ? 'Bước 1: Chọn Giảng viên hướng dẫn (GVHD)' : 'Step 1: Choose Thesis Advisor'}
              </h3>
              <p className="text-xs text-muted-foreground">
                {locale === 'vi'
                  ? 'Chọn GVHD để xem danh sách các đề tài nghiên cứu do giảng viên đó đề xuất.'
                  : 'Select an advisor to filter topics proposed by them.'}
              </p>
            </div>
          </div>
          {selectedSupervisorId ? (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => setSelectedSupervisorId('')}
              className="text-xs text-muted-foreground hover:text-foreground self-start sm:self-auto"
            >
              {locale === 'vi' ? 'Xem tất cả giảng viên' : 'Show all advisors'}
            </Button>
          ) : null}
        </div>

        <div className="max-w-xl">
          <Select
            label={locale === 'vi' ? 'Giảng viên hướng dẫn' : 'Thesis Advisor'}
            value={selectedSupervisorId}
            onChange={(event) => setSelectedSupervisorId(event.target.value)}
            aria-label={locale === 'vi' ? 'Chọn giảng viên hướng dẫn' : 'Select thesis advisor'}
            options={[
              {
                value: '',
                label: locale === 'vi'
                  ? `Tất cả Giảng viên hướng dẫn (${workspace.topics.length} đề tài)`
                  : `All Advisors (${workspace.topics.length} topics)`,
              },
              ...uniqueSupervisors.map((sup) => ({
                value: sup.lecturerId,
                label: `${sup.name} (${sup.topicCount} ${locale === 'vi' ? 'đề tài' : 'topics'})`,
              })),
            ]}
          />
        </div>

        {selectedSupervisor ? (
          <div className="flex items-center gap-2 rounded-xl bg-primary/10 px-3.5 py-2.5 text-xs text-primary font-medium border border-primary/20">
            <UserCheck className="h-4 w-4 shrink-0" />
            <span>
              {locale === 'vi'
                ? `Đang lọc các đề tài do GVHD: ${selectedSupervisor.name} hướng dẫn (${displayTopics.length} đề tài)`
                : `Filtering topics advised by: ${selectedSupervisor.name} (${displayTopics.length} topics)`}
            </span>
          </div>
        ) : null}
      </div>

      {/* Step 2: Chọn đề tài nghiên cứu */}
      <div className="space-y-4">
        <div className="flex items-center gap-2.5">
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-primary text-xs font-bold text-primary-foreground shadow-xs">
            2
          </span>
          <div>
            <h3 className="text-base font-bold text-foreground">
              {locale === 'vi'
                ? selectedSupervisor
                  ? `Bước 2: Chọn đề tài do GVHD ${selectedSupervisor.name} đề xuất`
                  : 'Bước 2: Chọn đề tài nghiên cứu phù hợp'
                : selectedSupervisor
                  ? `Step 2: Choose topic proposed by ${selectedSupervisor.name}`
                  : 'Step 2: Choose a thesis topic'}
            </h3>
            <p className="text-xs text-muted-foreground">
              {locale === 'vi'
                ? 'Tìm kiếm theo từ khóa hoặc chọn trực tiếp đề tài bên dưới để xem chi tiết và đăng ký cho nhóm.'
                : 'Search by keyword or select a topic below to view details and register.'}
            </p>
          </div>
        </div>

        <form onSubmit={handleSearch} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <label className="flex min-w-0 flex-1 flex-col gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
            {searchCopy.label}
            <Input
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              placeholder={searchCopy.placeholder}
              className="h-11 normal-case tracking-normal"
            />
          </label>
          <Button type="submit" disabled={workspace.workspaceLoading} className="h-11 sm:w-32">
            {workspace.workspaceLoading ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />
            ) : (
              <Search className="mr-2 h-4 w-4" aria-hidden="true" />
            )}
            {searchCopy.action}
          </Button>
        </form>

        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs font-semibold text-muted-foreground mr-1">
            {searchCopy.quickSuggest}
          </span>
          {quickPills.map((pill) => (
            <button
              key={pill}
              type="button"
              onClick={() => handleSelectPill(pill)}
              className="rounded-md border border-border/80 bg-card px-3 py-1 text-xs font-medium text-foreground transition hover:border-primary/50 hover:bg-muted/80"
            >
              {pill}
            </button>
          ))}
        </div>
      </div>

      {workspace.error ? (
        <ErrorState
          title={messages.thesis.loadFailed}
          description={workspace.error}
          onRetry={() => void workspace.reload()}
        />
      ) : workspace.workspaceLoading && workspace.topics.length === 0 ? (
        <Card variant="muted">
          <CardHeader>
            <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle>{messages.thesis.topicsTitle}</CardTitle>
                <CardDescription>{messages.thesis.topicsDescription}</CardDescription>
              </div>
              <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {messages.thesis.loading}
              </span>
            </div>
          </CardHeader>
          <CardContent>
            <TopicCardGridSkeleton count={6} />
          </CardContent>
        </Card>
      ) : !searchActive ? (
        <div className="space-y-6">
          <EmptyState
            icon={FileStack}
            title={searchCopy.requiredTitle}
            description={searchCopy.requiredDescription}
          />
          <div className="flex flex-col items-center justify-center gap-4">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setShowAll(true);
                setSubmittedQuery('all');
              }}
              className="gap-2 border-primary/40 bg-primary/5 text-primary hover:bg-primary/10 hover:text-primary transition"
            >
              <FileStack className="h-4 w-4" />
              {searchCopy.viewAll}
            </Button>
            <div className="flex flex-wrap items-center justify-center gap-2 max-w-2xl px-4">
              <span className="text-xs font-semibold text-muted-foreground mr-1">
                {searchCopy.quickSuggest}
              </span>
              {quickPills.map((pill) => (
                <button
                  key={pill}
                  type="button"
                  onClick={() => handleSelectPill(pill)}
                  className="rounded-md border border-border/80 bg-card px-3 py-1 text-xs font-medium text-foreground transition hover:border-primary/50 hover:bg-muted/80"
                >
                  {pill}
                </button>
              ))}
            </div>
          </div>
        </div>
      ) : displayTopics.length === 0 ? (
        <EmptyState
          icon={FileStack}
          title={workspace.topics.length === 0 ? messages.thesis.noTopics : searchCopy.noMatch}
          description={messages.thesis.noTopicsDescription}
          action={
            selectedSupervisorId ? (
              <Button type="button" variant="outline" onClick={() => setSelectedSupervisorId('')}>
                {locale === 'vi' ? 'Xem đề tài của tất cả giảng viên' : 'View all topics'}
              </Button>
            ) : undefined
          }
        />
      ) : (
        <Card variant="muted">
          <CardHeader>
            <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle>{messages.thesis.topicsTitle}</CardTitle>
                <CardDescription>
                  {selectedSupervisor
                    ? (locale === 'vi' ? `Các đề tài do GVHD ${selectedSupervisor.name} hướng dẫn` : `Topics advised by ${selectedSupervisor.name}`)
                    : messages.thesis.topicsDescription}
                </CardDescription>
              </div>
              <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {displayTopics.length} {locale === 'vi' ? 'đề tài' : 'topics'}
              </span>
            </div>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {displayTopics.map((topic) => {
              const supervisorName = getTopicSupervisorName(topic);
              return (
                <LocalizedLink
                  key={topic.id}
                  href={`/dashboard/thesis/topics/${topic.id}`}
                  className={cn(
                    'group flex min-h-[220px] flex-col rounded-xl border border-border/70 bg-card p-5',
                    'transition duration-200 hover:border-primary/50 hover:bg-primary/[0.025] hover:shadow-sm',
                    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  )}
                >
                  <div className="flex items-start justify-between gap-3">
                    <span className="rounded-md bg-secondary px-2.5 py-1 text-xs font-semibold text-muted-foreground">
                      {topic.maxGroups} {messages.thesis.groups.toLowerCase()}
                    </span>
                    <ArrowUpRight className="h-4 w-4 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
                  </div>
                  <h2
                    className="mt-4 line-clamp-2 text-base font-bold leading-6 text-foreground group-hover:text-primary transition-colors"
                    title={topic.title}
                  >
                    {topic.title}
                  </h2>
                  <div className="mt-2.5 flex items-center gap-1.5 text-xs text-primary font-medium">
                    <GraduationCap className="h-4 w-4 shrink-0 text-primary" />
                    <span className="font-bold text-foreground">
                      {locale === 'vi' ? 'GVHD:' : 'Advisor:'}
                    </span>
                    <span className="truncate font-semibold text-primary" title={supervisorName}>
                      {supervisorName || (locale === 'vi' ? 'Đang cập nhật' : 'TBD')}
                    </span>
                  </div>
                  <p
                    className="mt-2 line-clamp-3 text-xs leading-5 text-muted-foreground"
                    title={plainTextDescription(topic.description)}
                  >
                    {plainTextDescription(topic.description)}
                  </p>
                  <span className={cn('mt-auto pt-4 text-xs font-bold text-primary flex items-center gap-1')}>
                    {messages.thesis.topicDetailTitle}
                    <ArrowUpRight className="h-3 w-3" />
                  </span>
                </LocalizedLink>
              );
            })}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
