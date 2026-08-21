'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  ArrowUpRight,
  Bell,
  CreditCard,
  Gauge,
  RefreshCw,
  Server,
  TrendingUp,
  Users,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { analyticsApi, type AnalyticsCockpit } from '@/lib/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import {
  AdminMetricCard,
  AdminTableCard,
  AdminTableScroll,
} from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { useI18n } from '@/i18n';
import {
  getLocalizedFlatLabel,
  getLocalizedName,
} from '@/lib/academic-content';

const gradeColors: Record<string, string> = {
  A: 'bg-emerald-500',
  'A-': 'bg-emerald-400',
  'B+': 'bg-sky-500',
  B: 'bg-sky-400',
  'B-': 'bg-sky-300',
  'C+': 'bg-amber-500',
  C: 'bg-amber-400',
  'C-': 'bg-amber-300',
  'D+': 'bg-orange-500',
  D: 'bg-orange-400',
  'D-': 'bg-orange-300',
  F: 'bg-red-500',
};

function formatTrendLabel(locale: 'en' | 'vi', trend: AnalyticsCockpit['enrollmentTrends'][number]) {
  if (locale === 'vi' && trend.labelVi) {
    return trend.labelVi;
  }
  if (locale === 'en' && trend.labelEn) {
    return trend.labelEn;
  }

  const match = trend.month.match(/^(\d{4})-(\d{2})$/);
  if (!match) {
    return trend.month;
  }

  return new Intl.DateTimeFormat(locale === 'vi' ? 'vi-VN' : 'en-US', {
    month: 'short',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, 1)));
}

function normalizeStatus(value: string) {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function formatProviderLabel(provider: string) {
  const providers: Record<string, string> = {
    MOMO: 'MoMo',
    ZALOPAY: 'ZaloPay',
    VNPAY: 'VNPay',
    PAYPAL: 'PayPal',
    CARD: 'Card',
    CARD_SANDBOX: 'Card',
  };

  return providers[provider.toUpperCase()] ?? normalizeStatus(provider);
}

function formatStatusLabel(locale: 'en' | 'vi', status: string) {
  const normalized = status.toUpperCase();
  const en: Record<string, string> = {
    CANCELLED: 'Cancelled',
    COMPLETED: 'Completed',
    ERROR: 'Error',
    FAILED: 'Failed',
    INFO: 'Info',
    OVERDUE: 'Overdue',
    PAID: 'Paid',
    PARTIALLY_PAID: 'Partially paid',
    PENDING: 'Pending',
    REFUNDED: 'Refunded',
    SUCCESS: 'Success',
    WARNING: 'Warning',
  };
  const vi: Record<string, string> = {
    CANCELLED: 'Đã hủy',
    COMPLETED: 'Hoàn tất',
    ERROR: 'Lỗi',
    FAILED: 'Lỗi',
    INFO: 'Thông tin',
    OVERDUE: 'Quá hạn',
    PAID: 'Đã thanh toán',
    PARTIALLY_PAID: 'Thanh toán một phần',
    PENDING: 'Đang chờ',
    REFUNDED: 'Đã hoàn tiền',
    SUCCESS: 'Thành công',
    WARNING: 'Cảnh báo',
  };

  return (locale === 'vi' ? vi : en)[normalized] ?? normalizeStatus(status);
}

function localizeAttentionText(locale: 'en' | 'vi', text: string) {
  if (locale !== 'vi') {
    return text;
  }

  const viKnownEvents: Record<string, string> = {
    'A provider callback could not be reconciled automatically.':
      'Callback từ provider chưa thể đối soát tự động.',
    'A section is close to capacity and the waitlist has changed.':
      'Một lớp gần đạt sức chứa và danh sách chờ vừa thay đổi.',
    'Enrollment summary updated':
      'Tổng hợp đăng ký đã cập nhật',
    'Payment callback needs review':
      'Callback thanh toán cần rà soát',
    'Registration waitlist changed':
      'Danh sách chờ đăng ký đã thay đổi',
    'Your latest enrollment and billing summary is ready.':
      'Tổng hợp đăng ký và học phí mới nhất đã sẵn sàng.',
  };

  return viKnownEvents[text] ?? text;
}

function statusTone(status: string) {
  if (['PAID', 'COMPLETED', 'SUCCESS', 'INFO'].includes(status)) {
    return 'text-emerald-600 dark:text-emerald-400';
  }
  if (['FAILED', 'OVERDUE', 'ERROR', 'CANCELLED'].includes(status)) {
    return 'text-red-600 dark:text-red-400';
  }
  if (['PENDING', 'PARTIALLY_PAID', 'WARNING'].includes(status)) {
    return 'text-amber-600 dark:text-amber-400';
  }
  return 'text-muted-foreground';
}

export default function AdminAnalyticsPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { formatCurrency, formatDateTime, formatNumber, href, locale, messages } =
    useI18n();
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [cockpit, setCockpit] = useState<AnalyticsCockpit | null>(null);
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const cockpitCopy = messages.adminAnalytics.cockpit;

  useEffect(() => {
    if (isAuthLoading || isLoggingOut) {
      return;
    }

    if (!user) {
      router.replace(`${href('/login')}?reason=session-expired`);
      return;
    }

    if (!isAdmin && !isSuperAdmin) {
      router.replace(href('/dashboard'));
    }
  }, [href, isAdmin, isSuperAdmin, isAuthLoading, isLoggingOut, router, user]);

  const fetchAnalytics = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      setCockpit(await analyticsApi.getCockpit());
    } catch {
      setError(messages.adminAnalytics.unavailableDescription);
    } finally {
      setIsLoading(false);
    }
  }, [messages.adminAnalytics.unavailableDescription]);

  useEffect(() => {
    if (canAccess) {
      void fetchAnalytics();
    }
  }, [canAccess, fetchAnalytics]);

  const attentionQueue = useMemo(() => {
    if (!cockpit) {
      return [];
    }

    const items: Array<{
      title: string;
      detail: string;
      tone: string;
    }> = [];

    if (cockpit.registrationPressure.atCapacity > 0) {
      items.push({
        title:
          locale === 'vi'
            ? `${cockpit.registrationPressure.atCapacity} lớp đã đầy`
            : `${cockpit.registrationPressure.atCapacity} section(s) at capacity`,
        detail:
          locale === 'vi'
            ? 'Kiểm tra waitlist, capacity và phương án mở thêm section.'
            : 'Review waitlists, capacity, and whether another section is needed.',
        tone: 'border-red-500/30 bg-red-500/10',
      });
    }

    if (cockpit.finance.totals.overdueInvoices > 0) {
      items.push({
        title:
          locale === 'vi'
            ? `${cockpit.finance.totals.overdueInvoices} hóa đơn quá hạn`
            : `${cockpit.finance.totals.overdueInvoices} overdue invoice(s)`,
        detail:
          locale === 'vi'
            ? 'Ưu tiên đối soát và nhắc thanh toán trước kỳ đăng ký kế tiếp.'
            : 'Prioritize reconciliation and payment follow-up before the next registration window.',
        tone: 'border-amber-500/30 bg-amber-500/10',
      });
    }

    for (const notification of cockpit.notifications.recentAttention.slice(0, 3)) {
      items.push({
        title: localizeAttentionText(locale, notification.title),
        detail: localizeAttentionText(locale, notification.message),
        tone:
          notification.type === 'ERROR'
            ? 'border-red-500/30 bg-red-500/10'
            : 'border-amber-500/30 bg-amber-500/10',
      });
    }

    return items;
  }, [cockpit, locale]);

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={messages.adminAnalytics.loading} className="m-8" />;
  }

  const trendMax = Math.max(
    ...(cockpit?.enrollmentTrends.map(
      (trend) =>
        trend.totalActivity ??
        trend.enrolled + trend.completed + trend.dropped,
    ) ?? [1]),
    1,
  );
  const gradeTotal =
    cockpit?.gradeDistribution.reduce((sum, grade) => sum + grade.count, 0) ?? 0;
  const registrationRisk =
    (cockpit?.registrationPressure.atCapacity ?? 0) +
    (cockpit?.registrationPressure.nearCapacity ?? 0);
  const paymentRisk =
    (cockpit?.finance.totals.failedPayments ?? 0) +
    (cockpit?.finance.totals.overdueInvoices ?? 0);

  return (
    <AdminFrame
      title={messages.adminAnalytics.title}
      description={messages.adminAnalytics.description}
      backHref="/admin"
      backLabel={messages.adminShell.backToDashboard}
      actions={
        <Button
          type="button"
          variant="outline"
          onClick={() => void fetchAnalytics()}
          disabled={isLoading}
        >
          <RefreshCw className={`mr-2 h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
          {messages.adminAnalytics.refreshData}
        </Button>
      }
    >
      {error ? (
        <ErrorState
          title={messages.adminAnalytics.unavailableTitle}
          description={error || messages.adminAnalytics.unavailableDescription}
          onRetry={() => void fetchAnalytics()}
        />
      ) : isLoading && !cockpit ? (
        <LoadingState label={messages.adminAnalytics.loading} />
      ) : cockpit ? (
        <div className="space-y-8">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <AdminMetricCard
              label={cockpitCopy.kpis.serviceHealth}
              value={`${formatNumber(cockpit.operator.serviceCount)}/8`}
              icon={<Server className="h-5 w-5" />}
              detail={`${cockpit.operator.dependencyDown === 0 ? cockpitCopy.labels.healthy : `${cockpit.operator.dependencyDown} down`} · ${cockpitCopy.kpis.serviceHealthDetail}`}
              toneClassName="bg-emerald-500/12 text-emerald-600 dark:text-emerald-400"
              compact
            />
            <AdminMetricCard
              label={cockpitCopy.kpis.registrationPressure}
              value={formatNumber(registrationRisk)}
              icon={<Gauge className="h-5 w-5" />}
              detail={`${cockpitCopy.labels.sectionsAtRisk} · ${cockpitCopy.kpis.registrationPressureDetail}`}
              toneClassName="bg-amber-500/12 text-amber-600 dark:text-amber-400"
              compact
            />
            <AdminMetricCard
              label={cockpitCopy.kpis.paymentRisk}
              value={formatNumber(paymentRisk)}
              icon={<CreditCard className="h-5 w-5" />}
              detail={`${cockpitCopy.labels.failedPayments} · ${cockpitCopy.kpis.paymentRiskDetail}`}
              toneClassName="bg-red-500/12 text-red-600 dark:text-red-400"
              compact
            />
            <AdminMetricCard
              label={cockpitCopy.kpis.notificationHealth}
              value={formatNumber(cockpit.notifications.unread)}
              icon={<Bell className="h-5 w-5" />}
              detail={`${cockpitCopy.labels.unreadNotifications} · ${cockpitCopy.kpis.notificationHealthDetail}`}
              toneClassName="bg-sky-500/12 text-sky-600 dark:text-sky-400"
              compact
            />
          </div>

          <div className="grid gap-6 2xl:grid-cols-[minmax(0,1.3fr)_minmax(380px,0.7fr)]">
            <AdminTableCard
              title={cockpitCopy.panels.enrollmentFlow.title}
              description={cockpitCopy.panels.enrollmentFlow.description}
              className="h-full min-w-0"
            >
              <div className="min-w-0 max-w-full overflow-x-auto pb-2 md:overflow-x-visible">
                <div className="flex min-h-[300px] w-max min-w-full items-end gap-3 md:grid md:w-full md:grid-cols-12 md:gap-2">
                  {cockpit.enrollmentTrends.map((trend) => {
                    const total =
                      trend.totalActivity ??
                      trend.enrolled + trend.completed + trend.dropped;
                    const enrolledHeight = Math.max(
                      (trend.enrolled / trendMax) * 220,
                      trend.enrolled > 0 ? 10 : 0,
                    );
                    const completedHeight = Math.max(
                      (trend.completed / trendMax) * 220,
                      trend.completed > 0 ? 10 : 0,
                    );
                    const droppedHeight = Math.max(
                      (trend.dropped / trendMax) * 220,
                      trend.dropped > 0 ? 10 : 0,
                    );

                    return (
                      <div
                        key={trend.month}
                        className="flex w-[78px] shrink-0 flex-col items-center gap-3 md:w-auto md:min-w-0"
                      >
                        <div className="flex h-[230px] w-full items-end justify-center gap-1 rounded-lg border border-border/70 bg-secondary/20 px-2 py-2 md:gap-0 md:px-1">
                          <div
                            className="w-3 rounded-t bg-sky-500 md:w-2"
                            title={`${messages.adminAnalytics.tableHeaders.grades.enrolled}: ${trend.enrolled}`}
                            style={{ height: enrolledHeight }}
                          />
                          <div
                            className="w-3 rounded-t bg-emerald-500 md:w-2"
                            title={`${messages.adminAnalytics.tableHeaders.grades.completed}: ${trend.completed}`}
                            style={{ height: completedHeight }}
                          />
                          <div
                            className="w-3 rounded-t bg-red-500 md:w-2"
                            title={`${messages.adminAnalytics.tableHeaders.grades.dropped}: ${trend.dropped}`}
                            style={{ height: droppedHeight }}
                          />
                        </div>
                        <div className="space-y-1 text-center">
                          <div className="text-xs font-medium text-foreground">
                            {formatTrendLabel(locale, trend)}
                          </div>
                          <div className="text-xs text-muted-foreground">
                            {formatNumber(total)}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
              <div className="mt-4 flex flex-wrap gap-3 text-xs text-muted-foreground">
                <span className="inline-flex items-center gap-2">
                  <span className="h-2.5 w-2.5 rounded-full bg-sky-500" />
                  {messages.adminAnalytics.tableHeaders.grades.enrolled}
                </span>
                <span className="inline-flex items-center gap-2">
                  <span className="h-2.5 w-2.5 rounded-full bg-emerald-500" />
                  {messages.adminAnalytics.tableHeaders.grades.completed}
                </span>
                <span className="inline-flex items-center gap-2">
                  <span className="h-2.5 w-2.5 rounded-full bg-red-500" />
                  {messages.adminAnalytics.tableHeaders.grades.dropped}
                </span>
              </div>
            </AdminTableCard>

            <div data-testid="admin-analytics-action-queue" className="min-w-0">
              <AdminTableCard
                title={cockpitCopy.panels.actionQueue.title}
                description={cockpitCopy.panels.actionQueue.description}
                className="h-full min-w-0"
              >
                {attentionQueue.length === 0 ? (
                  <EmptyState
                    title={cockpitCopy.labels.noAttention}
                    description={
                      locale === 'vi'
                        ? 'Các tín hiệu đăng ký, thanh toán và thông báo đang ở trạng thái ổn định.'
                        : 'Registration, payment, and notification signals are currently steady.'
                    }
                    className="border-none bg-transparent px-0 py-0"
                  />
                ) : (
                  <div className="grid gap-3 sm:grid-cols-2 2xl:grid-cols-1">
                    {attentionQueue.map((item) => (
                      <div
                        key={`${item.title}-${item.detail}`}
                        className={`rounded-lg border p-4 ${item.tone}`}
                      >
                        <div className="font-medium text-foreground">{item.title}</div>
                        <p className="mt-2 text-sm leading-6 text-muted-foreground">
                          {item.detail}
                        </p>
                      </div>
                    ))}
                  </div>
                )}
              </AdminTableCard>
            </div>
          </div>

          <div className="grid gap-6 xl:grid-cols-2">
            <AdminTableCard
              title={cockpitCopy.panels.registrationPressure.title}
              description={cockpitCopy.panels.registrationPressure.description}
              className="h-full"
            >
              <div className="mb-5 grid gap-3 sm:grid-cols-3">
                <div className="rounded-lg border border-border/70 bg-secondary/20 p-4">
                  <div className="text-sm text-muted-foreground">
                    {cockpitCopy.labels.full}
                  </div>
                  <div className="mt-1 text-2xl font-semibold text-foreground">
                    {formatNumber(cockpit.registrationPressure.atCapacity)}
                  </div>
                </div>
                <div className="rounded-lg border border-border/70 bg-secondary/20 p-4">
                  <div className="text-sm text-muted-foreground">
                    {cockpitCopy.labels.nearCapacity}
                  </div>
                  <div className="mt-1 text-2xl font-semibold text-foreground">
                    {formatNumber(cockpit.registrationPressure.nearCapacity)}
                  </div>
                </div>
                <div className="rounded-lg border border-border/70 bg-secondary/20 p-4">
                  <div className="text-sm text-muted-foreground">
                    {cockpitCopy.labels.waitlist}
                  </div>
                  <div className="mt-1 text-2xl font-semibold text-foreground">
                    {formatNumber(cockpit.registrationPressure.waitlistActive)}
                  </div>
                </div>
              </div>

              <div className="space-y-3">
                {cockpit.registrationPressure.highestPressure.slice(0, 6).map((section) => (
                  <div
                    key={section.sectionId}
                    className="rounded-lg border border-border/70 bg-background/60 p-4"
                  >
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <div className="font-semibold text-foreground">
                          {section.courseCode} · {getLocalizedName(
                            locale,
                            {
                              code: section.courseCode,
                              name: section.courseName,
                              nameEn: section.courseNameEn,
                              nameVi: section.courseNameVi,
                            },
                            section.courseName,
                          )}
                        </div>
                        <div className="mt-1 text-sm text-muted-foreground">
                          {getLocalizedFlatLabel(
                            locale,
                            section.semesterName,
                            section.semesterNameEn,
                            section.semesterNameVi,
                            section.semesterName,
                          )}{' '}
                          · {messages.adminAnalytics.tableHeaders.section} {section.sectionNumber}
                        </div>
                      </div>
                      <div className="text-right text-sm">
                        <div className="font-medium text-foreground">
                          {section.enrolledCount}/{section.capacity}
                        </div>
                        <div className="text-muted-foreground">
                          {section.waitlistCount} {cockpitCopy.labels.waitlist.toLowerCase()}
                        </div>
                      </div>
                    </div>
                    <div className="mt-3 h-2.5 rounded-full bg-secondary">
                      <div
                        className={`h-2.5 rounded-full ${
                          section.occupancyRate >= 100
                            ? 'bg-red-500'
                            : section.occupancyRate >= 80
                              ? 'bg-amber-500'
                              : 'bg-emerald-500'
                        }`}
                        style={{ width: `${Math.min(section.occupancyRate, 100)}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            </AdminTableCard>

            <AdminTableCard
              title={cockpitCopy.panels.financeFunnel.title}
              description={cockpitCopy.panels.financeFunnel.description}
              className="h-full"
            >
              <div className="grid gap-3 sm:grid-cols-3">
                <div className="rounded-lg border border-border/70 bg-secondary/20 p-4">
                  <div className="text-sm text-muted-foreground">
                    {cockpitCopy.labels.totalInvoiced}
                  </div>
                  <div className="mt-1 text-lg font-semibold text-foreground">
                    {formatCurrency(cockpit.finance.totals.totalInvoiced, 'USD')}
                  </div>
                </div>
                <div className="rounded-lg border border-border/70 bg-secondary/20 p-4">
                  <div className="text-sm text-muted-foreground">
                    {cockpitCopy.labels.paidAmount}
                  </div>
                  <div className="mt-1 text-lg font-semibold text-emerald-600 dark:text-emerald-400">
                    {formatCurrency(cockpit.finance.totals.paidAmount, 'USD')}
                  </div>
                </div>
                <div className="rounded-lg border border-border/70 bg-secondary/20 p-4">
                  <div className="text-sm text-muted-foreground">
                    {cockpitCopy.labels.outstanding}
                  </div>
                  <div className="mt-1 text-lg font-semibold text-amber-600 dark:text-amber-400">
                    {formatCurrency(cockpit.finance.totals.outstandingAmount, 'USD')}
                  </div>
                </div>
              </div>

              <div
                className="mt-5 space-y-3 md:hidden"
                role="list"
                aria-label={locale === 'vi' ? 'Tóm tắt kênh thanh toán' : 'Payment provider summary'}
              >
                {cockpit.finance.providerFunnel.length === 0 ? (
                  <div
                    role="status"
                    className="rounded-lg border border-border/70 bg-secondary/20 px-4 py-6 text-sm text-muted-foreground"
                  >
                    {locale === 'vi'
                      ? 'Chưa có hoạt động thanh toán theo provider.'
                      : 'No provider payment activity yet.'}
                  </div>
                ) : (
                  cockpit.finance.providerFunnel.map((item) => (
                    <article
                      key={`mobile-${item.provider}-${item.status}`}
                      role="listitem"
                      className="rounded-lg border border-border/70 bg-background/60 p-4 shadow-sm"
                    >
                      <div className="flex flex-wrap items-start justify-between gap-3">
                        <div className="min-w-0">
                          <div className="break-words font-medium text-foreground">
                            {formatProviderLabel(item.provider)}
                          </div>
                          <div className="mt-1 text-xs text-muted-foreground">
                            {locale === 'vi' ? 'Kênh thanh toán' : 'Provider'}
                          </div>
                        </div>
                        <span
                          className={`shrink-0 text-sm font-semibold ${statusTone(item.status)}`}
                        >
                          {formatStatusLabel(locale, item.status)}
                        </span>
                      </div>
                      <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
                        <div className="min-w-0">
                          <dt className="text-xs text-muted-foreground">
                            {locale === 'vi' ? 'Số lượng' : 'Count'}
                          </dt>
                          <dd className="mt-1 font-medium text-foreground">
                            {formatNumber(item.count)}
                          </dd>
                        </div>
                        <div className="min-w-0 text-right">
                          <dt className="text-xs text-muted-foreground">
                            {locale === 'vi' ? 'Số tiền' : 'Amount'}
                          </dt>
                          <dd className="mt-1 break-words font-medium text-foreground">
                            {formatCurrency(item.amount, 'USD')}
                          </dd>
                        </div>
                      </dl>
                    </article>
                  ))
                )}
              </div>

              <AdminTableScroll className="mt-5 hidden md:block">
                <table className="w-full min-w-[520px] text-sm">
                  <thead>
                    <tr className="border-b border-border/70 text-left text-muted-foreground">
                      <th className="px-2 py-3 font-medium">
                        {locale === 'vi' ? 'Kênh thanh toán' : 'Provider'}
                      </th>
                      <th className="px-2 py-3 font-medium">
                        {locale === 'vi' ? 'Trạng thái' : 'Status'}
                      </th>
                      <th className="px-2 py-3 text-right font-medium">
                        {locale === 'vi' ? 'Số lượng' : 'Count'}
                      </th>
                      <th className="px-2 py-3 text-right font-medium">
                        {locale === 'vi' ? 'Số tiền' : 'Amount'}
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {cockpit.finance.providerFunnel.length === 0 ? (
                      <tr>
                        <td className="px-2 py-6 text-muted-foreground" colSpan={4}>
                          {locale === 'vi'
                            ? 'Chưa có hoạt động thanh toán theo provider.'
                            : 'No provider payment activity yet.'}
                        </td>
                      </tr>
                    ) : (
                      cockpit.finance.providerFunnel.map((item) => (
                        <tr key={`${item.provider}-${item.status}`}>
                          <td className="px-2 py-3 font-medium text-foreground">
                            {formatProviderLabel(item.provider)}
                          </td>
                          <td className={`px-2 py-3 font-medium ${statusTone(item.status)}`}>
                            {formatStatusLabel(locale, item.status)}
                          </td>
                          <td className="px-2 py-3 text-right text-foreground">
                            {formatNumber(item.count)}
                          </td>
                          <td className="px-2 py-3 text-right text-muted-foreground">
                            {formatCurrency(item.amount, 'USD')}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </AdminTableScroll>
            </AdminTableCard>
          </div>

          <div className="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
            <AdminTableCard
              title={messages.adminAnalytics.panels.gradeDistribution.title}
              description={messages.adminAnalytics.panels.gradeDistribution.description}
              className="h-full"
            >
              {gradeTotal === 0 ? (
                <EmptyState
                  title={messages.adminAnalytics.panels.gradeDistribution.emptyTitle}
                  description={messages.adminAnalytics.panels.gradeDistribution.emptyDescription}
                  className="border-none bg-transparent px-0 py-0"
                />
              ) : (
                <div className="space-y-3">
                  {cockpit.gradeDistribution.map((grade) => (
                    <div
                      key={grade.grade}
                      className="grid grid-cols-[44px_1fr_auto] items-center gap-3"
                    >
                      <span className="text-sm font-medium text-foreground">
                        {grade.grade}
                      </span>
                      <div className="h-3 rounded-full bg-secondary">
                        <div
                          className={`h-3 rounded-full ${gradeColors[grade.grade] || 'bg-muted'}`}
                          style={{ width: `${grade.percentage}%` }}
                        />
                      </div>
                      <span className="text-sm text-muted-foreground">
                        {formatNumber(grade.count)} ({grade.percentage}%)
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </AdminTableCard>

            <AdminTableCard
              title={cockpitCopy.panels.notificationDelivery.title}
              description={cockpitCopy.panels.notificationDelivery.description}
              className="h-full"
            >
              <div className="mb-5 flex flex-wrap gap-3">
                {cockpit.notifications.byType.map((item) => (
                  <div
                    key={item.type}
                    className="rounded-full border border-border/70 px-3 py-1 text-sm"
                  >
                    <span className={statusTone(item.type)}>
                      {formatStatusLabel(locale, item.type)}
                    </span>{' '}
                    <span className="text-muted-foreground">
                      {formatNumber(item.count)}
                    </span>
                  </div>
                ))}
              </div>
              {cockpit.notifications.recentAttention.length === 0 ? (
                <EmptyState
                  title={cockpitCopy.labels.noAttention}
                  description={
                    locale === 'vi'
                      ? 'Không có thông báo cảnh báo hoặc lỗi gần đây.'
                      : 'No recent warning or error notifications.'
                  }
                  className="border-none bg-transparent px-0 py-0"
                />
              ) : (
                <div className="space-y-3">
                  {cockpit.notifications.recentAttention.map((item) => (
                    <div
                      key={item.id}
                      className="rounded-lg border border-border/70 bg-background/60 p-4"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-3">
                        <div className="font-medium text-foreground">
                          {localizeAttentionText(locale, item.title)}
                        </div>
                        <div className={`text-xs font-semibold ${statusTone(item.type)}`}>
                          {formatStatusLabel(locale, item.type)}
                        </div>
                      </div>
                      <p className="mt-2 text-sm leading-6 text-muted-foreground">
                        {localizeAttentionText(locale, item.message)}
                      </p>
                      <div className="mt-3 text-xs text-muted-foreground">
                        {formatDateTime(item.createdAt)}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </AdminTableCard>
          </div>

          <AdminTableCard
            title={cockpitCopy.panels.operatorLinks.title}
            description={cockpitCopy.panels.operatorLinks.description}
          >
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              {cockpit.operator.dashboards.map((link) => (
                <a
                  key={link.label}
                  href={link.url}
                  target="_blank"
                  rel="noreferrer"
                  className="group rounded-lg border border-border/70 bg-background/60 p-4 transition hover:border-primary/40 hover:bg-background"
                  aria-label={`${link.label} ${cockpitCopy.actions.opensInNewTab}`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="font-medium text-foreground">{link.label}</div>
                      <div className="mt-2 text-sm text-muted-foreground">
                        {cockpitCopy.actions.open}
                      </div>
                    </div>
                    <ArrowUpRight className="mt-0.5 h-4 w-4 text-muted-foreground transition group-hover:text-foreground" />
                  </div>
                </a>
              ))}
            </div>
          </AdminTableCard>
        </div>
      ) : null}
    </AdminFrame>
  );
}
