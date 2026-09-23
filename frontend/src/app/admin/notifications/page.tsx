'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  AlertCircle,
  AlertTriangle,
  Bell,
  CheckCircle2,
  Eye,
  Info,
  Search,
  Send,
  ShieldCheck,
  Trash2,
  X,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import {
  adminNotificationsApi,
  usersApi,
  type AdminNotificationInput,
  type AdminNotificationRecord,
  type AdminNotificationType,
} from '@/lib/api';
import type { User } from '@/types/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import {
  AdminFormField,
  AdminPaginationFooter,
  AdminRowActions,
  AdminTableCard,
  AdminTableScroll,
} from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { statusToneClass, type StatusTone } from '@/components/ui/status';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';
import { campusErrorMessage } from '@/lib/campus-error';

const TITLE_MAX = 120;
const MESSAGE_MAX = 500;

// The backend enum is INFO|WARNING|ERROR|SUCCESS; the design presents the
// ERROR tier as the urgent card, so the label lives in messages, not here.
const TYPE_OPTIONS: AdminNotificationType[] = ['INFO', 'SUCCESS', 'WARNING', 'ERROR'];

const TYPE_ICONS: Record<AdminNotificationType, typeof Info> = {
  INFO: Info,
  SUCCESS: CheckCircle2,
  WARNING: AlertTriangle,
  ERROR: AlertCircle,
};

const TYPE_TONES: Record<AdminNotificationType, StatusTone> = {
  INFO: 'info',
  SUCCESS: 'success',
  WARNING: 'warning',
  ERROR: 'danger',
};

const TYPE_ICON_TONES: Record<AdminNotificationType, string> = {
  INFO: 'text-status-info-foreground',
  SUCCESS: 'text-status-success-foreground',
  WARNING: 'text-status-warning-foreground',
  ERROR: 'text-status-danger-foreground',
};

function recordTone(type: string): StatusTone {
  return TYPE_TONES[type as AdminNotificationType] ?? 'neutral';
}

function displayName(user: User): string {
  const name = `${user.lastName ?? ''} ${user.firstName ?? ''}`.trim();
  return name || user.email;
}

function initialsOf(user: User): string {
  const surname = (user.lastName || user.firstName || '').charAt(0);
  const given = (user.firstName || '').charAt(0);
  const initials = `${surname}${given && given !== surname ? given : ''}`;
  return initials.toUpperCase() || '?';
}

export default function AdminNotificationsPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { href, formatDateTime, formatNumber, messages } = useI18n();
  const router = useRouter();
  const copy = messages.admin.notifications;
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const { confirm, confirmationDialog } = useConfirmationDialog();

  // Composer state.
  const [recipient, setRecipient] = useState<User | null>(null);
  const [recipientInput, setRecipientInput] = useState('');
  const [recipientResults, setRecipientResults] = useState<User[]>([]);
  const [recipientSearched, setRecipientSearched] = useState(false);
  const [recipientSearching, setRecipientSearching] = useState(false);
  const [recipientError, setRecipientError] = useState('');
  const [type, setType] = useState<AdminNotificationType>('INFO');
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [link, setLink] = useState('');
  const [isSending, setIsSending] = useState(false);

  // Sent list state (the admin GET /notifications ledger).
  const [rows, setRows] = useState<AdminNotificationRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [isRowsLoading, setIsRowsLoading] = useState(true);
  const [rowsError, setRowsError] = useState('');

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

  const loadSent = useCallback(async () => {
    setIsRowsLoading(true);
    setRowsError('');

    try {
      const response = await adminNotificationsApi.adminList({ page, limit: 20 });
      setRows(response.data ?? []);
      setTotal(response.meta?.total ?? (response.data ?? []).length);
      setTotalPages(response.meta?.totalPages || 1);
    } catch (cause) {
      setRowsError(
        campusErrorMessage(cause, messages.common.campusErrors, copy.sentLoadFailed),
      );
    } finally {
      setIsRowsLoading(false);
    }
  }, [copy.sentLoadFailed, messages.common.campusErrors, page]);

  useEffect(() => {
    if (canAccess) {
      void loadSent();
    }
  }, [canAccess, loadSent]);

  // Recipient identity comes from the account directory only: the search box
  // queries usersApi and a result must be clicked. Free-text recipients would
  // POST a userId the server cannot resolve.
  const searchRecipients = useCallback(async () => {
    const term = recipientInput.trim();
    if (!term || recipientSearching) {
      return;
    }

    setRecipientSearching(true);
    setRecipientError('');

    try {
      const response = await usersApi.getAll({ page: 1, limit: 5, search: term });
      setRecipientResults(response.data ?? []);
      setRecipientSearched(true);
    } catch (cause) {
      setRecipientResults([]);
      setRecipientSearched(false);
      setRecipientError(
        campusErrorMessage(cause, messages.common.campusErrors, copy.recipientSearchFailed),
      );
    } finally {
      setRecipientSearching(false);
    }
  }, [
    copy.recipientSearchFailed,
    messages.common.campusErrors,
    recipientInput,
    recipientSearching,
  ]);

  const clearRecipient = () => {
    setRecipient(null);
    setRecipientInput('');
    setRecipientResults([]);
    setRecipientSearched(false);
    setRecipientError('');
  };

  const trimmedTitle = title.trim();
  const trimmedMessage = message.trim();
  const trimmedLink = link.trim();
  const titleValid = trimmedTitle.length >= 1 && title.length <= TITLE_MAX;
  const messageValid =
    trimmedMessage.length >= 1 && message.length <= MESSAGE_MAX;
  const linkValid = trimmedLink === '' || trimmedLink.startsWith('/');
  const isValid = Boolean(recipient) && titleValid && messageValid && linkValid;

  const clearDraft = () => {
    clearRecipient();
    setType('INFO');
    setTitle('');
    setMessage('');
    setLink('');
  };

  const handleSend = async (event: React.FormEvent) => {
    event.preventDefault();
    // Double-submit guard: the button is disabled, but an Enter keypress can
    // still race a previous send that has not settled yet.
    if (!recipient || !isValid || isSending) {
      return;
    }

    setIsSending(true);

    try {
      const body: AdminNotificationInput = {
        userId: recipient.id,
        title: trimmedTitle,
        message: trimmedMessage,
        type,
      };
      if (trimmedLink) {
        body.link = trimmedLink;
      }

      await adminNotificationsApi.adminCreate(body);
      toast.success(copy.sentSuccess.replace('{name}', displayName(recipient)));
      // The recipient stays picked so the office can send the same notice to
      // the next account without re-searching; the content resets.
      setTitle('');
      setMessage('');
      setLink('');
      await loadSent();
    } catch (cause) {
      toast.error(
        campusErrorMessage(cause, messages.common.campusErrors, copy.sentFailed),
      );
    } finally {
      setIsSending(false);
    }
  };

  const handleDelete = async (record: AdminNotificationRecord) => {
    const shouldDelete = await confirm({
      title: copy.deleteConfirmTitle,
      message: copy.deleteConfirmMessage,
      confirmText: copy.deleteConfirm,
      variant: 'destructive',
    });

    if (!shouldDelete) {
      return;
    }

    try {
      await adminNotificationsApi.adminDelete(record.id);
      toast.success(copy.deleted);
      await loadSent();
    } catch {
      toast.error(copy.deleteFailed);
    }
  };

  const typeLabels: Record<AdminNotificationType, string> = useMemo(
    () => ({
      INFO: copy.typeInfo,
      SUCCESS: copy.typeSuccess,
      WARNING: copy.typeWarning,
      ERROR: copy.typeError,
    }),
    [copy.typeError, copy.typeInfo, copy.typeSuccess, copy.typeWarning],
  );

  const roleLabel = (candidate: User) => {
    const role = candidate.role ?? candidate.roles?.[0];
    if (!role) {
      return '';
    }
    return (
      copy.roles[role as keyof typeof copy.roles] ??
      candidate.role ??
      role
    );
  };

  const pageSummary = copy.sentPageSummary.replace('{count}', formatNumber(total));
  const PreviewIcon = TYPE_ICONS[type];

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={messages.admin.loading} className="m-8" />;
  }

  return (
    <AdminFrame
      title={copy.title}
      description={copy.description}
      eyebrow={copy.eyebrow}
    >
      <div className="space-y-6">
        <Card variant="elevated">
          <form onSubmit={handleSend} noValidate aria-label={copy.title}>
            <CardHeader className="border-b border-border/70 pb-4">
              <CardTitle>{copy.eyebrow}</CardTitle>
              <CardDescription>{copy.recipientSingleOnly}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-6 pt-6">
              <AdminFormField
                label={copy.recipientLabel}
                description={copy.recipientHint}
                error={recipientError || undefined}
              >
                {recipient ? (
                  <div className="flex items-center justify-between gap-3 rounded-lg border border-primary/25 bg-primary/5 p-3">
                    <div className="flex min-w-0 items-center gap-3">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary text-xs font-semibold tracking-wide text-primary-foreground">
                        {initialsOf(recipient)}
                      </div>
                      <div className="min-w-0">
                        <p className="truncate text-sm font-semibold text-foreground">
                          {displayName(recipient)}
                        </p>
                        <p className="truncate text-xs text-muted-foreground">
                          <span className="font-mono">{recipient.email}</span>
                          {roleLabel(recipient) ? ` · ${roleLabel(recipient)}` : ''}
                        </p>
                      </div>
                    </div>
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      className="shrink-0"
                      aria-label={copy.recipientRemove}
                      title={copy.recipientRemove}
                      onClick={clearRecipient}
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  </div>
                ) : (
                  <div className="space-y-3">
                    <div className="flex flex-col gap-2 sm:flex-row">
                      <Input
                        type="text"
                        value={recipientInput}
                        onChange={(event) => setRecipientInput(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === 'Enter') {
                            // Keep the directory lookup out of the composer
                            // submit: Enter here searches, it never sends.
                            event.preventDefault();
                            void searchRecipients();
                          }
                        }}
                        placeholder={copy.recipientSearchPlaceholder}
                        aria-label={copy.recipientSearchLabel}
                        icon={<Search className="h-4 w-4" aria-hidden="true" />}
                      />
                      <Button
                        type="button"
                        variant="outline"
                        className="sm:shrink-0"
                        disabled={recipientSearching}
                        onClick={() => void searchRecipients()}
                      >
                        {messages.common.actions.search}
                      </Button>
                    </div>
                    {recipientSearched && !recipientError ? (
                      recipientResults.length === 0 ? (
                        <p className="text-sm text-muted-foreground">
                          {copy.recipientNoMatch}
                        </p>
                      ) : (
                        <ul
                          aria-label={copy.recipientResultsLabel}
                          className="divide-y divide-border/60 overflow-hidden rounded-lg border border-border/70 bg-card"
                        >
                          {recipientResults.map((candidate) => (
                            <li key={candidate.id}>
                              <button
                                type="button"
                                className="flex w-full items-center gap-3 p-3 text-left transition-colors hover:bg-secondary/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
                                onClick={() => {
                                  setRecipient(candidate);
                                  setRecipientResults([]);
                                  setRecipientSearched(false);
                                  setRecipientInput('');
                                  setRecipientError('');
                                }}
                              >
                                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-secondary text-xs font-semibold text-secondary-foreground">
                                  {initialsOf(candidate)}
                                </span>
                                <span className="min-w-0 flex-1">
                                  <span className="block truncate text-sm font-medium text-foreground">
                                    {displayName(candidate)}
                                  </span>
                                  <span className="block truncate text-xs text-muted-foreground">
                                    {candidate.email}
                                  </span>
                                </span>
                                {roleLabel(candidate) ? (
                                  <span
                                    className={cn(
                                      'shrink-0 rounded-md px-2 py-0.5 text-xs font-medium',
                                      statusToneClass('neutral'),
                                    )}
                                  >
                                    {roleLabel(candidate)}
                                  </span>
                                ) : null}
                              </button>
                            </li>
                          ))}
                        </ul>
                      )
                    ) : null}
                  </div>
                )}
              </AdminFormField>

              <fieldset>
                <legend className="text-sm font-medium text-foreground">
                  {copy.typeLabel}
                </legend>
                <div className="mt-2 grid grid-cols-2 gap-2.5 sm:grid-cols-4">
                  {TYPE_OPTIONS.map((option) => {
                    const OptionIcon = TYPE_ICONS[option];
                    const checked = type === option;
                    return (
                      <label
                        key={option}
                        className={cn(
                          'flex cursor-pointer items-center justify-center gap-2 rounded-lg border-2 p-2.5 text-xs font-semibold transition-colors',
                          checked
                            ? 'border-primary bg-primary/5 text-primary'
                            : 'border-border bg-background text-muted-foreground hover:bg-secondary/40 hover:text-foreground',
                        )}
                      >
                        <input
                          type="radio"
                          name="admin-notification-type"
                          value={option}
                          checked={checked}
                          onChange={() => setType(option)}
                          className="sr-only"
                        />
                        <OptionIcon
                          className={cn('h-4 w-4 shrink-0', TYPE_ICON_TONES[option])}
                          aria-hidden="true"
                        />
                        <span>{typeLabels[option]}</span>
                      </label>
                    );
                  })}
                </div>
              </fieldset>

              <AdminFormField
                label={
                  <span className="flex items-center justify-between gap-3">
                    <span>{copy.titleLabel}</span>
                    <span className="font-normal tabular-nums text-muted-foreground">
                      {formatNumber(title.length)} / {formatNumber(TITLE_MAX)}
                    </span>
                  </span>
                }
              >
                <Input
                  type="text"
                  value={title}
                  onChange={(event) => setTitle(event.target.value.slice(0, TITLE_MAX))}
                  maxLength={TITLE_MAX}
                  placeholder={copy.titlePlaceholder}
                />
              </AdminFormField>

              <AdminFormField
                label={
                  <span className="flex items-center justify-between gap-3">
                    <span>{copy.messageLabel}</span>
                    <span className="font-normal tabular-nums text-muted-foreground">
                      {formatNumber(message.length)} / {formatNumber(MESSAGE_MAX)}
                    </span>
                  </span>
                }
              >
                <Textarea
                  value={message}
                  onChange={(event) =>
                    setMessage(event.target.value.slice(0, MESSAGE_MAX))
                  }
                  maxLength={MESSAGE_MAX}
                  rows={4}
                  placeholder={copy.messagePlaceholder}
                />
              </AdminFormField>

              <AdminFormField
                label={
                  <span>
                    {copy.linkLabel}{' '}
                    <span className="font-normal text-muted-foreground">
                      {copy.linkOptional}
                    </span>
                  </span>
                }
                description={copy.linkHint}
                error={linkValid ? undefined : copy.linkInvalid}
              >
                <Input
                  type="text"
                  value={link}
                  onChange={(event) => setLink(event.target.value)}
                  placeholder={copy.linkPlaceholder}
                  inputMode="url"
                  className="font-mono text-xs"
                />
              </AdminFormField>

              <div className="space-y-2">
                <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                  <span className="flex items-center gap-1.5 font-semibold uppercase tracking-wide text-muted-foreground">
                    <Eye className="h-3.5 w-3.5 text-primary" aria-hidden="true" />
                    {copy.previewLabel}
                  </span>
                  <span className="text-muted-foreground">{copy.previewHint}</span>
                </div>
                <div className="rounded-lg border border-border/70 bg-secondary/30 p-3">
                  <div className="flex items-start gap-3 rounded-lg border border-border/70 bg-card p-3">
                    <span
                      className={cn(
                        'flex h-8 w-8 shrink-0 items-center justify-center rounded-md',
                        statusToneClass(recordTone(type)),
                      )}
                    >
                      <PreviewIcon className="h-4 w-4" aria-hidden="true" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-foreground">
                        {trimmedTitle || copy.previewNoTitle}
                      </p>
                      <p className="mt-1 line-clamp-2 text-sm leading-5 text-muted-foreground">
                        {trimmedMessage || copy.previewNoMessage}
                      </p>
                      <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                        <span className="font-medium text-primary">
                          {copy.previewJustNow}
                        </span>
                        {user ? (
                          <>
                            <span aria-hidden="true">·</span>
                            <span>
                              {copy.previewSentBy} {displayName(user)}
                            </span>
                          </>
                        ) : null}
                        {trimmedLink ? (
                          <>
                            <span aria-hidden="true">·</span>
                            <span className="font-mono text-[11px] text-primary">
                              {trimmedLink}
                            </span>
                          </>
                        ) : null}
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </CardContent>

            <div className="sticky bottom-0 z-10 flex flex-col gap-3 rounded-b-lg border-t border-border/70 bg-card/95 px-5 py-3 backdrop-blur sm:flex-row sm:items-center sm:justify-between">
              <p className="flex items-center gap-2 text-xs text-muted-foreground">
                <ShieldCheck className="h-4 w-4 shrink-0" aria-hidden="true" />
                {copy.auditHint}
              </p>
              <div className="flex items-center justify-end gap-2">
                <Button type="button" variant="ghost" onClick={clearDraft}>
                  {copy.clearDraft}
                </Button>
                <Button
                  type="submit"
                  disabled={!isValid || isSending}
                  title={!isValid ? copy.validationIncomplete : undefined}
                >
                  <Send className="mr-2 h-4 w-4" aria-hidden="true" />
                  {isSending ? copy.sending : copy.send}
                </Button>
              </div>
            </div>
          </form>
        </Card>

        {rowsError ? (
          <ErrorState
            title={copy.sentTitle}
            description={rowsError}
            onRetry={() => void loadSent()}
          />
        ) : isRowsLoading && rows.length === 0 ? (
          <LoadingState label={copy.sentLoading} />
        ) : rows.length === 0 ? (
          <EmptyState
            icon={Bell}
            title={copy.sentEmptyTitle}
            description={copy.sentEmptyDescription}
          />
        ) : (
          <AdminTableCard
            title={copy.sentTitle}
            description={copy.sentDescription}
            footer={
              <AdminPaginationFooter
                summary={pageSummary}
                page={page}
                totalPages={totalPages}
                onPrevious={() => setPage((current) => current - 1)}
                onNext={() => setPage((current) => current + 1)}
                previousLabel={copy.previousPage}
                nextLabel={copy.nextPage}
              />
            }
          >
            <div className="space-y-3 md:hidden" role="list" aria-label={copy.sentTitle}>
              {rows.map((record) => {
                const RecordIcon =
                  TYPE_ICONS[record.type as AdminNotificationType] ?? Bell;
                return (
                  <article
                    key={`${record.id}-mobile`}
                    className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                    role="listitem"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex min-w-0 items-start gap-2.5">
                        <span
                          className={cn(
                            'flex h-7 w-7 shrink-0 items-center justify-center rounded-md',
                            statusToneClass(recordTone(record.type)),
                          )}
                        >
                          <RecordIcon className="h-3.5 w-3.5" aria-hidden="true" />
                        </span>
                        <div className="min-w-0">
                          <h3 className="truncate text-sm font-semibold text-foreground">
                            {record.title}
                          </h3>
                          <p className="mt-1 truncate font-mono text-xs text-muted-foreground" title={record.userId}>
                            {record.userId}
                          </p>
                        </div>
                      </div>
                      <span
                        className={cn(
                          'shrink-0 rounded-md px-2.5 py-1 text-xs font-medium',
                          statusToneClass(recordTone(record.type)),
                        )}
                      >
                        {typeLabels[record.type as AdminNotificationType] ??
                          record.type}
                      </span>
                    </div>
                    <AdminRowActions className="mt-3 border-t border-border/60 pt-3">
                      <span className="mr-auto text-xs text-muted-foreground tabular-nums">
                        {formatDateTime(record.createdAt)}
                      </span>
                      <Button
                        type="button"
                        size="icon"
                        variant="ghost"
                        className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                        onClick={() => void handleDelete(record)}
                        aria-label={copy.deleteLabel}
                        title={copy.deleteLabel}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </AdminRowActions>
                  </article>
                );
              })}
            </div>
            <AdminTableScroll className="hidden md:block">
              <table className="w-full min-w-[720px] text-sm">
                <thead>
                  <tr className="bg-secondary text-left text-muted-foreground">
                    <th className="px-2 py-3 font-medium">{copy.sentHeaders.title}</th>
                    <th className="px-2 py-3 font-medium">{copy.sentHeaders.type}</th>
                    <th className="px-2 py-3 font-medium">{copy.sentHeaders.recipient}</th>
                    <th className="px-2 py-3 font-medium">{copy.sentHeaders.time}</th>
                    <th className="px-2 py-3 text-right font-medium">
                      {copy.sentHeaders.actions}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((record) => {
                    const RecordIcon =
                      TYPE_ICONS[record.type as AdminNotificationType] ?? Bell;
                    return (
                      <tr key={record.id} className="border-b border-border/60 last:border-0">
                        <td className="max-w-[320px] px-2 py-3">
                          <p className="truncate font-medium text-foreground">
                            {record.title}
                          </p>
                          <p className="mt-0.5 truncate text-xs text-muted-foreground">
                            {record.message}
                          </p>
                        </td>
                        <td className="px-2 py-3">
                          <span
                            className={cn(
                              'inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium',
                              statusToneClass(recordTone(record.type)),
                            )}
                          >
                            <RecordIcon className="h-3.5 w-3.5" aria-hidden="true" />
                            {typeLabels[record.type as AdminNotificationType] ??
                              record.type}
                          </span>
                        </td>
                        <td className="px-2 py-3">
                          <span
                            className="block max-w-[200px] truncate font-mono text-xs text-muted-foreground"
                            title={record.userId}
                          >
                            {record.userId}
                          </span>
                        </td>
                        <td className="px-2 py-3 tabular-nums text-muted-foreground">
                          {formatDateTime(record.createdAt)}
                        </td>
                        <td className="px-2 py-3">
                          <AdminRowActions>
                            <Button
                              type="button"
                              size="icon"
                              variant="ghost"
                              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                              onClick={() => void handleDelete(record)}
                              aria-label={copy.deleteLabel}
                              title={copy.deleteLabel}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </AdminRowActions>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </AdminTableScroll>
          </AdminTableCard>
        )}
      </div>
      {confirmationDialog}
    </AdminFrame>
  );
}
