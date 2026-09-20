'use client';

import { useMemo, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import { ArrowUpRight, FileStack, Loader2, Search } from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { LinkButton } from '@/components/ui/link-button';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { StatusBadge } from '@/components/thesis/StatusBadge';
import { cn } from '@/lib/utils';
import { useThesisWorkspace } from './useThesisWorkspace';

export default function ThesisTopicCatalogPage() {
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth();
  const { messages, locale } = useI18n();
  const searchParams = useSearchParams();
  const explicitRoundId = searchParams.get('roundId') || '';
  const [searchInput, setSearchInput] = useState('');
  const [showAll, setShowAll] = useState(false);
  // Course requirement: the topic catalog only appears after a search, so no
  // topic is fetched or rendered until the query is submitted.
  const [submittedQuery, setSubmittedQuery] = useState('');
  const searchActive = showAll || submittedQuery.trim().length > 0;
  const workspace = useThesisWorkspace(explicitRoundId, { topicsEnabled: searchActive, groupsEnabled: false });

  const normalizedQuery = submittedQuery.trim().toLowerCase();
  const matchingTopics = useMemo(() => {
    if (!searchActive) return [];
    if (showAll || !normalizedQuery || normalizedQuery === 'all') {
      return workspace.topics;
    }
    return workspace.topics.filter((topic) =>
      [topic.title, topic.description]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalizedQuery)),
    );
  }, [normalizedQuery, searchActive, showAll, workspace.topics]);

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
        noMatch: 'Không có đề tài nào khớp từ khóa.',
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
        noMatch: 'No topic matches your search.',
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
        <label className="flex min-w-[15rem] flex-1 flex-col gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
          {messages.thesis.selectRound}
          <select
            value={workspace.selectedRoundId}
            onChange={(event) => workspace.setSelectedRoundId(event.target.value)}
            className="h-11 rounded-lg border border-border/80 bg-card px-3 text-sm font-medium normal-case tracking-normal text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
            aria-label={messages.thesis.selectRound}
          >
            {workspace.cohortGroups && workspace.cohortGroups.length > 0 ? (
              workspace.cohortGroups.map((group) => (
                <optgroup key={group.cohort} label={group.cohort}>
                  {group.rounds.map((round) => (
                    <option key={round.id} value={round.id}>
                      {round.name}
                    </option>
                  ))}
                </optgroup>
              ))
            ) : (
              workspace.rounds.map((round) => (
                <option key={round.id} value={round.id}>
                  {round.name}
                </option>
              ))
            )}
          </select>
        </label>
        {workspace.selectedRound ? (
          <div className="sm:self-end sm:pb-1">
            <StatusBadge status={workspace.selectedRound.status} />
          </div>
        ) : null}
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

      {workspace.error ? (
        <ErrorState title={messages.thesis.loadFailed} description={workspace.error} />
      ) : workspace.workspaceLoading && workspace.topics.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-border/80 p-12 text-center">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
          <p className="mt-3 text-sm text-muted-foreground">{messages.thesis.loading}</p>
        </div>
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
      ) : matchingTopics.length === 0 ? (
        <EmptyState
          icon={FileStack}
          title={workspace.topics.length === 0 ? messages.thesis.noTopics : searchCopy.noMatch}
          description={messages.thesis.noTopicsDescription}
        />
      ) : (
        <Card variant="muted">
          <CardHeader>
            <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <CardTitle>{messages.thesis.topicsTitle}</CardTitle>
                <CardDescription>{messages.thesis.topicsDescription}</CardDescription>
              </div>
              <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                {matchingTopics.length} {locale === 'vi' ? 'đề tài' : 'topics'}
              </span>
            </div>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {matchingTopics.map((topic) => (
              <LocalizedLink
                key={topic.id}
                href={`/dashboard/thesis/topics/${topic.id}`}
                className={cn(
                  'group flex min-h-[210px] flex-col rounded-lg border border-border/70 bg-card p-5',
                  'transition-colors hover:border-primary/50 hover:bg-primary/[0.025]',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <span className="rounded-md bg-secondary px-2.5 py-1 text-xs font-semibold text-muted-foreground">
                    {topic.maxGroups} {messages.thesis.groups.toLowerCase()}
                  </span>
                  <ArrowUpRight className="h-4 w-4 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
                </div>
                <h2 className="mt-5 line-clamp-3 text-lg font-semibold leading-7 text-foreground">
                  {topic.title}
                </h2>
                <p className="mt-2 line-clamp-4 text-sm leading-6 text-muted-foreground">
                  {topic.description}
                </p>
                <span className={cn('mt-auto pt-5 text-sm font-semibold text-primary')}>
                  {messages.thesis.topicDetailTitle}
                </span>
              </LocalizedLink>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
