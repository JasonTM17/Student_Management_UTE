'use client';

import { Fragment, useCallback, useEffect, useState } from 'react';
import { ChevronDown, ChevronRight, RefreshCw, ScrollText } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { adminAuditApi, type AdminAuditEntry } from '@/lib/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import {
  AdminPaginationFooter,
  AdminTableCard,
  AdminTableScroll,
  AdminToolbarCard,
} from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { statusToneClass, type StatusTone } from '@/components/ui/status';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { campusErrorMessage } from '@/lib/campus-error';

const PAGE_SIZE = 20;

function actionTone(action: string): StatusTone {
  if (action.startsWith('DELETED') || action.includes('REJECT')) return 'danger';
  if (action.includes('RESET') || action.includes('UNLOCK') || action.includes('APPROVED')) return 'success';
  if (action.includes('ROLE') || action.includes('PASSWORD') || action.includes('TWO_FACTOR')) return 'warning';
  return 'info';
}

/** Round-3 ct-2 completion: the admin-facing viewer for GET /api/v1/admin/audit. */
export default function AdminAuditTrailPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: authLoading, isLoggingOut } = useAuth();
  const { href, messages, formatDateTime } = useI18n();
  const router = useRouter();
  const copy = messages.admin.auditTrail;
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));

  const [rows, setRows] = useState<AdminAuditEntry[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionFilter, setActionFilter] = useState('');
  const [entityFilter, setEntityFilter] = useState('');
  const [applied, setApplied] = useState<{ action?: string; entityType?: string }>({});
  const [expanded, setExpanded] = useState<string | null>(null);

  useEffect(() => {
    if (authLoading || isLoggingOut) return;
    if (!user) {
      router.replace(`${href('/login')}?portal=admin&reason=session-expired`);
      return;
    }
    if (!isAdmin && !isSuperAdmin) {
      router.replace(href('/dashboard'));
    }
  }, [authLoading, href, isAdmin, isLoggingOut, isSuperAdmin, router, user]);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await adminAuditApi.list({ page, limit: PAGE_SIZE, ...applied });
      setRows(result.data);
      setTotalPages(Math.max(1, result.meta.totalPages));
      setTotal(result.meta.total);
    } catch (cause) {
      setError(campusErrorMessage(cause, messages.common.campusErrors, copy.loadFailed));
    } finally {
      setLoading(false);
    }
  }, [applied, copy.loadFailed, messages.common.campusErrors, page]);

  useEffect(() => {
    if (canAccess) void load();
  }, [canAccess, load]);

  const applyFilters = () => {
    setPage(1);
    setApplied({
      action: actionFilter.trim() || undefined,
      entityType: entityFilter.trim() || undefined,
    });
  };

  const clearFilters = () => {
    setActionFilter('');
    setEntityFilter('');
    setPage(1);
    setApplied({});
  };

  if (authLoading || isLoggingOut) {
    return <LoadingState label={copy.loading} />;
  }
  if (!user || !canAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  return (
    <AdminFrame
      title={copy.title}
      description={copy.description}
      actions={
        <Button type="button" variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
          {copy.refresh}
        </Button>
      }
    >
      <div className="space-y-6">
        <AdminToolbarCard>
          <form
            className="grid gap-3 md:grid-cols-[repeat(2,minmax(0,1fr))_auto]"
            onSubmit={(event) => {
              event.preventDefault();
              applyFilters();
            }}
          >
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-foreground">{copy.filterAction}</span>
              <Input
                value={actionFilter}
                onChange={(event) => setActionFilter(event.target.value)}
                placeholder="PASSWORD_RESET"
                aria-label={copy.filterAction}
              />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-foreground">{copy.filterEntity}</span>
              <Input
                value={entityFilter}
                onChange={(event) => setEntityFilter(event.target.value)}
                placeholder="USER"
                aria-label={copy.filterEntity}
              />
            </label>
            <div className="flex items-end gap-2">
              <Button type="submit" size="sm" disabled={loading}>
                {copy.applyFilters}
              </Button>
              <Button type="button" size="sm" variant="outline" onClick={clearFilters} disabled={loading}>
                {copy.clearFilters}
              </Button>
            </div>
          </form>
        </AdminToolbarCard>

        {error ? (
          <ErrorState title={copy.loadFailed} description={error} onRetry={() => void load()} />
        ) : loading ? (
          <LoadingState label={copy.loading} />
        ) : rows.length === 0 ? (
          <EmptyState
            icon={ScrollText}
            title={copy.emptyTitle}
            description={copy.emptyDescription}
          />
        ) : (
          <AdminTableCard title={copy.tableTitle}>
            <AdminTableScroll>
              <table className="w-full text-left text-sm">
                <thead className="text-xs uppercase tracking-wide text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2">{copy.colTime}</th>
                    <th className="px-3 py-2">{copy.colAction}</th>
                    <th className="px-3 py-2">{copy.colActor}</th>
                    <th className="px-3 py-2">{copy.colEntity}</th>
                    <th className="px-3 py-2">{copy.colSummary}</th>
                    <th className="w-8 px-2 py-2" aria-hidden="true" />
                  </tr>
                </thead>
                <tbody>
                  {rows.map((entry) => (
                    <Fragment key={entry.id}>
                      <tr
                        key={entry.id}
                        className="cursor-pointer border-t border-border/60 hover:bg-muted/40"
                        onClick={() => setExpanded((current) => (current === entry.id ? null : entry.id))}
                      >
                        <td className="whitespace-nowrap px-3 py-2 text-muted-foreground">
                          {formatDateTime(entry.createdAt)}
                        </td>
                        <td className="px-3 py-2">
                          <span
                            className={`inline-flex rounded-full px-2 py-0.5 text-xs font-semibold ${statusToneClass(actionTone(entry.action))}`}
                          >
                            {entry.action}
                          </span>
                        </td>
                        <td className="px-3 py-2">{entry.actorLabel || entry.actorId || copy.unknownActor}</td>
                        <td className="px-3 py-2">
                          <span className="font-medium">{entry.entityType}</span>
                          {entry.entityId ? (
                            <span className="ml-1 text-xs text-muted-foreground">
                              {entry.entityId.length > 18 ? `${entry.entityId.slice(0, 18)}…` : entry.entityId}
                            </span>
                          ) : null}
                        </td>
                        <td className="max-w-[24rem] truncate px-3 py-2 text-muted-foreground" title={entry.summary ?? undefined}>
                          {entry.summary || '—'}
                        </td>
                        <td className="px-2 py-2 text-muted-foreground">
                          {expanded === entry.id ? (
                            <ChevronDown className="h-4 w-4" aria-hidden="true" />
                          ) : (
                            <ChevronRight className="h-4 w-4" aria-hidden="true" />
                          )}
                        </td>
                      </tr>
                      {expanded === entry.id ? (
                        <tr className="border-t border-border/40 bg-muted/20">
                          <td colSpan={6} className="px-4 py-3">
                            <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                              {copy.detailTitle}
                            </p>
                            <div className="grid gap-3 md:grid-cols-2">
                              <pre className="max-h-56 overflow-auto rounded-lg bg-background/80 p-3 text-xs leading-5 text-muted-foreground">
                                {entry.beforeState || '—'}
                              </pre>
                              <pre className="max-h-56 overflow-auto rounded-lg bg-background/80 p-3 text-xs leading-5 text-muted-foreground">
                                {entry.afterState || '—'}
                              </pre>
                            </div>
                          </td>
                        </tr>
                      ) : null}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </AdminTableScroll>
            <AdminPaginationFooter
              summary={copy.pageSummary.replace('{count}', String(total))}
              page={page}
              totalPages={totalPages}
              onPrevious={() => setPage((current) => Math.max(1, current - 1))}
              onNext={() => setPage((current) => Math.min(totalPages, current + 1))}
              previousLabel={copy.previous}
              nextLabel={copy.next}
            />
          </AdminTableCard>
        )}
      </div>
    </AdminFrame>
  );
}
