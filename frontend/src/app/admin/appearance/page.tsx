'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowDown, ArrowUp, Palette } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { announcementsApi } from '@/lib/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { LinkButton } from '@/components/ui/link-button';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { useI18n } from '@/i18n';
import {
  DEFAULT_SITE_APPEARANCE,
  SITE_APPEARANCE_ACCENTS,
  mergePostOrder,
  movePostOrder,
  sanitizeSiteAppearance,
  type SiteAppearance,
  type SiteAppearanceAccent,
} from '@/lib/site-appearance';
import {
  broadcastSiteAppearance,
  fetchSiteAppearance,
  saveSiteAppearance,
} from '@/lib/site-appearance-client';
import { cn } from '@/lib/utils';

type AnnouncementRow = {
  id: string;
  title: string;
};

export default function AdminAppearancePage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { href, locale, messages } = useI18n();
  const router = useRouter();
  const copy = messages.admin.appearance;
  const [draft, setDraft] = useState<SiteAppearance>(DEFAULT_SITE_APPEARANCE);
  const [copyLocale, setCopyLocale] = useState<'en' | 'vi'>(locale);
  const [posts, setPosts] = useState<AnnouncementRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));

  useEffect(() => {
    if (isAuthLoading || isLoggingOut) {
      return;
    }

    if (!user) {
      router.replace(`${href('/login')}?portal=admin&reason=session-expired`);
      return;
    }

    if (!isAdmin && !isSuperAdmin) {
      router.replace(href('/dashboard'));
    }
  }, [href, isAdmin, isSuperAdmin, isAuthLoading, isLoggingOut, router, user]);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const [appearance, announcements] = await Promise.all([
        fetchSiteAppearance(),
        announcementsApi.getAll({ page: 1, limit: 20 }).catch(() => ({ data: [] as AnnouncementRow[] })),
      ]);
      const rows = (announcements.data ?? []).map((item) => ({
        id: item.id,
        title: item.title,
      }));
      const next = sanitizeSiteAppearance({
        ...appearance,
        postOrder: mergePostOrder(appearance.postOrder, rows.map((row) => row.id)),
      });
      setPosts(rows);
      setDraft(next);
      broadcastSiteAppearance(next);
    } catch {
      setError(copy.loadFailed);
    } finally {
      setIsLoading(false);
    }
  }, [copy.loadFailed]);

  useEffect(() => {
    if (canAccess) {
      void load();
    }
  }, [canAccess, load]);

  const persistSeq = useRef(0);
  const persist = useCallback(async (next: SiteAppearance) => {
    const seq = persistSeq.current + 1;
    persistSeq.current = seq;
    setIsSaving(true);
    try {
      const saved = await saveSiteAppearance(next);
      if (seq !== persistSeq.current) {
        return;
      }
      setDraft(saved);
      broadcastSiteAppearance(saved);
    } catch {
      toast.error(copy.saveFailed);
      if (seq === persistSeq.current) {
        void load();
      }
    } finally {
      if (seq === persistSeq.current) {
        setIsSaving(false);
      }
    }
  }, [copy.saveFailed, load]);

  const orderedPosts = useMemo(() => {
    const rank = new Map(draft.postOrder.map((id, index) => [id, index]));
    return [...posts].sort((left, right) => {
      const leftRank = rank.get(left.id) ?? Number.MAX_SAFE_INTEGER;
      const rightRank = rank.get(right.id) ?? Number.MAX_SAFE_INTEGER;
      return leftRank - rightRank;
    });
  }, [draft.postOrder, posts]);

  const preview = draft.hero[locale];
  const fallbackHero = messages.home;

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={copy.title} className="m-8" />;
  }

  return (
    <AdminFrame
      title={copy.title}
      description={copy.description}
      eyebrow={copy.eyebrow}
      actions={
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <span
            className={cn(
              'inline-flex min-h-11 items-center rounded-md border px-3',
              isSaving ? 'border-border' : 'border-[var(--portal-yellow)] text-foreground',
            )}
          >
            {isSaving ? copy.saving : copy.saved}
          </span>
          <LinkButton href="/admin/announcements" variant="outline">
            {copy.openAnnouncements}
          </LinkButton>
        </div>
      }
    >
      {error ? (
        <ErrorState title={copy.loadFailed} description={error} onRetry={() => void load()} />
      ) : isLoading ? (
        <LoadingState label={copy.title} />
      ) : (
        <div className="grid gap-8 xl:grid-cols-[minmax(0,1fr)_24rem]">
          <div className="space-y-8">
            <section className="space-y-4 rounded-md border border-border/80 bg-card p-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <h2 className="text-lg font-semibold text-foreground">{copy.heroTitle}</h2>
                <div className="inline-flex rounded-md border border-border/80 p-1" role="tablist">
                  {(['en', 'vi'] as const).map((item) => (
                    <button
                      key={item}
                      type="button"
                      role="tab"
                      aria-selected={copyLocale === item}
                      onClick={() => setCopyLocale(item)}
                      className={cn(
                        'min-h-11 rounded-sm px-3 text-sm font-semibold',
                        copyLocale === item
                          ? 'bg-secondary text-foreground'
                          : 'text-muted-foreground hover:text-foreground',
                      )}
                    >
                      {item === 'en' ? copy.localeEn : copy.localeVi}
                    </button>
                  ))}
                </div>
              </div>
              <p className="text-sm text-muted-foreground">{copy.heroHint}</p>
              <label className="block space-y-2">
                <span className="text-sm font-medium text-foreground">{copy.heroEyebrow}</span>
                <Input
                  value={draft.hero[copyLocale].eyebrow}
                  onChange={(event) => {
                    setDraft((current) => ({
                      ...current,
                      hero: {
                        ...current.hero,
                        [copyLocale]: {
                          ...current.hero[copyLocale],
                          eyebrow: event.target.value,
                        },
                      },
                    }));
                  }}
                  onBlur={(event) => {
                    const next = {
                      ...draft,
                      hero: {
                        ...draft.hero,
                        [copyLocale]: {
                          ...draft.hero[copyLocale],
                          eyebrow: event.target.value,
                        },
                      },
                    };
                    setDraft(next);
                    void persist(next);
                  }}
                />
              </label>
              <label className="block space-y-2">
                <span className="text-sm font-medium text-foreground">{copy.heroTitle}</span>
                <Input
                  value={draft.hero[copyLocale].title}
                  onChange={(event) => {
                    setDraft((current) => ({
                      ...current,
                      hero: {
                        ...current.hero,
                        [copyLocale]: {
                          ...current.hero[copyLocale],
                          title: event.target.value,
                        },
                      },
                    }));
                  }}
                  onBlur={(event) => {
                    const next = {
                      ...draft,
                      hero: {
                        ...draft.hero,
                        [copyLocale]: {
                          ...draft.hero[copyLocale],
                          title: event.target.value,
                        },
                      },
                    };
                    setDraft(next);
                    void persist(next);
                  }}
                />
              </label>
              <label className="block space-y-2">
                <span className="text-sm font-medium text-foreground">{copy.heroDescription}</span>
                <Textarea
                  value={draft.hero[copyLocale].description}
                  onChange={(event) => {
                    setDraft((current) => ({
                      ...current,
                      hero: {
                        ...current.hero,
                        [copyLocale]: {
                          ...current.hero[copyLocale],
                          description: event.target.value,
                        },
                      },
                    }));
                  }}
                  onBlur={(event) => {
                    const next = {
                      ...draft,
                      hero: {
                        ...draft.hero,
                        [copyLocale]: {
                          ...draft.hero[copyLocale],
                          description: event.target.value,
                        },
                      },
                    };
                    setDraft(next);
                    void persist(next);
                  }}
                />
              </label>
            </section>

            <section className="space-y-4 rounded-md border border-border/80 bg-card p-5">
              <h2 className="text-lg font-semibold text-foreground">{copy.accent}</h2>
              <div className="grid gap-3 sm:grid-cols-3">
                {SITE_APPEARANCE_ACCENTS.map((accent) => (
                  <button
                    key={accent}
                    type="button"
                    onClick={() => {
                      const next = { ...draft, accent };
                      setDraft(next);
                      broadcastSiteAppearance(next);
                      void persist(next);
                    }}
                    className={cn(
                      'min-h-11 rounded-md border px-3 py-3 text-left text-sm font-semibold',
                      draft.accent === accent
                        ? 'border-[var(--portal-yellow)] bg-secondary'
                        : 'border-border/80 hover:border-foreground/30',
                    )}
                    aria-pressed={draft.accent === accent}
                  >
                    {copy.accents[accent as SiteAppearanceAccent]}
                  </button>
                ))}
              </div>
            </section>

            <section className="space-y-4 rounded-md border border-border/80 bg-card p-5">
              <div>
                <h2 className="text-lg font-semibold text-foreground">{copy.postsTitle}</h2>
                <p className="mt-1 text-sm text-muted-foreground">{copy.postsDescription}</p>
              </div>
              {orderedPosts.length === 0 ? (
                <EmptyState
                  icon={Palette}
                  title={copy.postsEmpty}
                  description={copy.postsDescription}
                  action={
                    <LinkButton href="/admin/announcements">{copy.openAnnouncements}</LinkButton>
                  }
                />
              ) : (
                <ol className="divide-y divide-border border-y border-border">
                  {orderedPosts.map((post, index) => (
                    <li key={post.id} className="flex items-center gap-3 py-3">
                      <span className="w-8 text-sm font-semibold tabular-nums text-muted-foreground">
                        {String(index + 1).padStart(2, '0')}
                      </span>
                      <span className="min-w-0 flex-1 truncate text-sm font-medium text-foreground">
                        {post.title}
                      </span>
                      <div className="flex shrink-0 gap-1">
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          aria-label={copy.moveUp}
                          disabled={index === 0}
                          onClick={() => {
                            const next = {
                              ...draft,
                              postOrder: movePostOrder(draft.postOrder, post.id, -1),
                            };
                            setDraft(next);
                            broadcastSiteAppearance(next);
                            void persist(next);
                          }}
                        >
                          <ArrowUp className="h-4 w-4" />
                        </Button>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          aria-label={copy.moveDown}
                          disabled={index === orderedPosts.length - 1}
                          onClick={() => {
                            const next = {
                              ...draft,
                              postOrder: movePostOrder(draft.postOrder, post.id, 1),
                            };
                            setDraft(next);
                            broadcastSiteAppearance(next);
                            void persist(next);
                          }}
                        >
                          <ArrowDown className="h-4 w-4" />
                        </Button>
                      </div>
                    </li>
                  ))}
                </ol>
              )}
            </section>
          </div>

          <aside className="space-y-3">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
              {copy.preview}
            </h2>
            <div className="rounded-lg border-l-4 border-[var(--portal-yellow)] bg-[var(--portal-sidebar)] p-6 text-[var(--portal-sidebar-text)]">
              <p className="text-xs font-semibold uppercase tracking-wide text-[var(--portal-yellow)]">
                {preview.eyebrow || fallbackHero.eyebrow}
              </p>
              <p className="mt-4 text-2xl font-semibold leading-8">
                {preview.title || fallbackHero.title}
              </p>
              <p className="mt-3 text-sm leading-6 text-[var(--portal-sidebar-muted)]">
                {preview.description || fallbackHero.description}
              </p>
              <p className="mt-6 text-xs text-[var(--portal-sidebar-muted)]">{copy.live}</p>
            </div>
          </aside>
        </div>
      )}
    </AdminFrame>
  );
}
