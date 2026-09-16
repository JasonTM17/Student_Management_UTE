'use client';

import { useMemo, useState } from 'react';
import { ArrowUpRight, FileStack, Search } from 'lucide-react';
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
  const [searchInput, setSearchInput] = useState('');
  // Course requirement: the topic catalog only appears after a search, so no
  // topic is fetched or rendered until the query is submitted.
  const [submittedQuery, setSubmittedQuery] = useState('');
  const searchActive = submittedQuery.trim().length > 0;
  const workspace = useThesisWorkspace('', { topicsEnabled: searchActive });

  const normalizedQuery = submittedQuery.trim().toLowerCase();
  const matchingTopics = useMemo(() => {
    if (!searchActive) return [];
    return workspace.topics.filter((topic) =>
      [topic.title, topic.description]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(normalizedQuery)),
    );
  }, [normalizedQuery, searchActive, workspace.topics]);

  if (authLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  if (workspace.isLoading) {
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
        placeholder: 'Nhập tên đề tài hoặc mô tả để xem danh mục…',
        action: 'Tìm kiếm',
        requiredTitle: 'Tìm kiếm để xem danh mục đề tài',
        requiredDescription:
          'Danh mục đề tài chỉ hiển thị sau khi bạn nhập từ khóa tìm kiếm và bấm tìm.',
        noMatch: 'Không có đề tài nào khớp từ khóa.',
      }
    : {
        label: 'Search topics',
        placeholder: 'Enter a topic title or description to browse the catalog…',
        action: 'Search',
        requiredTitle: 'Search to view the topic catalog',
        requiredDescription:
          'The topic catalog appears only after you enter a keyword and submit the search.',
        noMatch: 'No topic matches your search.',
      };

  const handleSearch = (event: React.FormEvent) => {
    event.preventDefault();
    setSubmittedQuery(searchInput);
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

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <label className="flex min-w-[15rem] flex-col gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
          {messages.thesis.selectRound}
          <select
            value={workspace.selectedRoundId}
            onChange={(event) => workspace.setSelectedRoundId(event.target.value)}
            className="h-11 rounded-lg border border-border/80 bg-card px-3 text-sm font-medium normal-case tracking-normal text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
            aria-label={messages.thesis.selectRound}
          >
            {workspace.rounds.map((round) => (
              <option key={round.id} value={round.id}>{round.name}</option>
            ))}
          </select>
        </label>
        {workspace.selectedRound ? (
          <StatusBadge status={workspace.selectedRound.status} />
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
        <Button type="submit" className="h-11 sm:w-32">
          <Search className="mr-2 h-4 w-4" aria-hidden="true" />
          {searchCopy.action}
        </Button>
      </form>

      {workspace.error ? (
        <ErrorState title={messages.thesis.loadFailed} description={workspace.error} />
      ) : !searchActive ? (
        <EmptyState
          icon={FileStack}
          title={searchCopy.requiredTitle}
          description={searchCopy.requiredDescription}
        />
      ) : matchingTopics.length === 0 ? (
        <EmptyState
          icon={FileStack}
          title={workspace.topics.length === 0 ? messages.thesis.noTopics : searchCopy.noMatch}
          description={messages.thesis.noTopicsDescription}
        />
      ) : (
        <Card variant="muted">
          <CardHeader>
            <CardTitle>{messages.thesis.topicsTitle}</CardTitle>
            <CardDescription>{messages.thesis.topicsDescription}</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {matchingTopics.map((topic) => (
              <LocalizedLink
                key={topic.id}
                href={`/dashboard/thesis/topics/${topic.id}?roundId=${workspace.selectedRoundId}`}
                className={cn(
                  'group flex min-h-[210px] flex-col rounded-lg border border-border/70 bg-card p-5',
                  'transition-colors hover:border-primary/50 hover:bg-primary/[0.025]',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                )}
              >
                <div className="flex items-start justify-between gap-3">
                  <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-semibold text-muted-foreground">
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
