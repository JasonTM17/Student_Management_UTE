'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import {
  CalendarDays,
  FileStack,
  UsersRound,
} from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LocalizedLink } from '@/components/LocalizedLink';
import { StatusBadge } from '@/components/thesis/StatusBadge';
import { metricToneClass, type StatusTone } from '@/components/ui/status';
import { useI18n } from '@/i18n';
import {
  thesisApi,
  type ThesisGroup,
  type ThesisRound,
  type ThesisTopic,
} from '@/lib/thesis-api';

export default function ThesisRoundDetailPage() {
  const { roundId } = useParams<{ roundId: string }>();
  const { messages } = useI18n();
  const [round, setRound] = useState<ThesisRound | null>(null);
  const [topics, setTopics] = useState<ThesisTopic[]>([]);
  const [groups, setGroups] = useState<ThesisGroup[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const statusLabel = (status: string) =>
    messages.thesis.status[status as keyof typeof messages.thesis.status] ??
    messages.common.statuses[status.toUpperCase() as keyof typeof messages.common.statuses] ??
    messages.common.statuses.UNKNOWN;

  useEffect(() => {
    if (!roundId) return;
    let cancelled = false;
    const load = async () => {
      setIsLoading(true);
      setError('');
      try {
        const [roundsData, topicsData, groupsData] = await Promise.all([
          thesisApi.listRounds(),
          thesisApi.listTopics(roundId),
          thesisApi.listGroups(roundId),
        ]);
        if (cancelled) return;
        setRound(roundsData.find((r) => r.id === roundId) ?? null);
        setTopics(topicsData);
        setGroups(groupsData);
      } catch {
        if (!cancelled) setError(messages.thesis.loadFailed);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [roundId, messages.thesis.loadFailed]);

  if (isLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (error || !round) {
    return (
      <ErrorState
        title={messages.thesis.loadFailed}
        description={error || messages.thesis.noRound}
        retryLabel={messages.thesis.retry}
        onRetry={() => window.location.reload()}
      />
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.thesis.eyebrow}</SectionEyebrow>}
        title={round.name}
        description={messages.thesis.description}
        actions={
          <div className="flex flex-wrap gap-2">
            <LocalizedLink href="/dashboard/thesis/progress">
              <span className="inline-flex items-center gap-2 rounded-lg border border-border/80 bg-card px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-secondary/50">
                <CalendarDays className="h-4 w-4" />
                {messages.thesis.progress.eyebrow}
              </span>
            </LocalizedLink>
          </div>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <MetricCard label={messages.thesis.roundStatus} value={statusLabel(round.status)} icon={<CalendarDays className="h-5 w-5" />} tone="warning" />
        <MetricCard label={messages.thesis.topics} value={topics.length} icon={<FileStack className="h-5 w-5" />} tone="info" />
        <MetricCard label={messages.thesis.groups} value={groups.length} icon={<UsersRound className="h-5 w-5" />} tone="success" />
      </div>

      <div className="grid gap-6 xl:grid-cols-2">
        <Card className="h-full">
          <CardHeader>
            <CardTitle>{messages.thesis.topicsTitle}</CardTitle>
            <CardDescription>{messages.thesis.topicsDescription}</CardDescription>
          </CardHeader>
          <CardContent>
            {topics.length === 0 ? (
              <EmptyState icon={FileStack} title={messages.thesis.noTopics} description={messages.thesis.noTopicsDescription} className="min-h-[200px]" />
            ) : (
              <div className="space-y-3">
                {topics.map((topic) => (
                  <article key={topic.id} className="rounded-xl border border-border/70 bg-card p-4">
                    <div className="flex items-start justify-between gap-3">
                      <h3 className="text-sm font-semibold leading-6 text-foreground">{topic.title}</h3>
                      <StatusBadge status={topic.status} />
                    </div>
                    <p className="mt-2 line-clamp-3 text-sm leading-6 text-muted-foreground">{topic.description}</p>
                    <p className="mt-3 text-xs text-muted-foreground">
                      {messages.thesis.detail.maxGroups}: {topic.maxGroups}
                    </p>
                  </article>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="h-full">
          <CardHeader>
            <CardTitle>{messages.thesis.detail.groupsTitle}</CardTitle>
            <CardDescription>{messages.thesis.detail.groupsDescription}</CardDescription>
          </CardHeader>
          <CardContent>
            {groups.length === 0 ? (
              <EmptyState icon={UsersRound} title={messages.thesis.detail.noGroups} description={messages.thesis.detail.noGroupsDescription} className="min-h-[200px]" />
            ) : (
              <div className="space-y-3">
                {groups.map((group, idx) => (
                  <div key={group.id} className="flex items-center justify-between gap-4 rounded-xl border border-border/70 bg-card p-4">
                    <div className="flex items-center gap-3">
                      <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
                        {idx + 1}
                      </div>
                      <div>
                        <p className="text-sm font-semibold text-foreground">
                          {messages.thesis.groupsTitle} #{idx + 1}
                        </p>
                        <p className="font-mono text-xs text-muted-foreground">{group.memberStudentIds.length} {messages.thesis.detail.members}</p>
                      </div>
                    </div>
                    <StatusBadge status={group.approvalStatus} variant="approval" />
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

    </div>
  );
}

function MetricCard({
  label,
  value,
  icon,
  tone,
}: {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  tone: StatusTone;
}) {
  return (
    <Card variant="elevated">
      <CardContent className="flex items-start justify-between gap-4 pt-6">
        <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-lg ${metricToneClass(tone)}`}>{icon}</div>
        <div className="min-w-0 text-right">
          <div className="break-words text-2xl font-semibold tracking-tight text-foreground">{value}</div>
          <div className="mt-1 text-sm text-muted-foreground">{label}</div>
        </div>
      </CardContent>
    </Card>
  );
}
