'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Download,
  Eye,
  Plus,
  Receipt,
  RefreshCw,
  Trash2,
} from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { financeApi, semestersApi } from '@/lib/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import {
  AdminDialogFooter,
  AdminPaginationFooter,
  AdminRowActions,
  AdminTableCard,
  AdminTableScroll,
  AdminToolbarCard,
} from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { Modal } from '@/components/ui/modal';
import { Select } from '@/components/ui/select';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import { getLocalizedName } from '@/lib/academic-content';
import { getLocalizedInvoiceItemDescription } from '@/lib/finance-content';

interface Invoice {
  id: string;
  invoiceNumber: string;
  studentId: string;
  semesterId: string;
  status: string;
  total: number;
  dueDate: string;
  paidAt?: string;
  createdAt: string;
  student?: {
    user?: { firstName?: string; lastName?: string; email?: string };
    studentId?: string;
  };
  semester?: { name: string; nameEn?: string; nameVi?: string; type?: string };
}

interface InvoiceDetail extends Invoice {
  items: {
    id: string;
    description: string;
    quantity: number;
    unitPrice: number;
    total: number;
  }[];
  payments: {
    id: string;
    paymentNumber: string;
    amount: number;
    method: string;
    status: string;
    paidAt: string;
    createdAt: string;
  }[];
}

interface Semester {
  id: string;
  name: string;
  nameEn?: string;
  nameVi?: string;
  type?: string;
}

const statusColors: Record<string, string> = {
  DRAFT: 'bg-secondary text-foreground',
  PENDING: 'bg-yellow-100 text-yellow-700',
  PAID: 'bg-green-100 text-green-700',
  OVERDUE: 'bg-red-100 text-red-700',
  PARTIALLY_PAID: 'bg-blue-100 text-blue-700',
  CANCELLED: 'bg-muted text-muted-foreground',
};

export default function AdminInvoicesPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { formatCurrency, formatDate, formatDateTime, formatNumber, href, locale, messages } =
    useI18n();
  const router = useRouter();
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedInvoice, setSelectedInvoice] = useState<InvoiceDetail | null>(
    null,
  );
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const [filters, setFilters] = useState({
    semesterId: '',
    studentId: '',
    status: '',
  });
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const { confirm, confirmationDialog } = useConfirmationDialog();

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

  const fetchSemesters = useCallback(async () => {
    try {
      const response = await semestersApi.getAll();
      setSemesters(response.data || []);
    } catch {
      // Optional filter data.
    }
  }, []);

  const fetchInvoices = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const params: {
        page: number;
        limit: number;
        semesterId?: string;
        studentId?: string;
        status?: string;
      } = { page, limit: 20 };
      if (filters.semesterId) params.semesterId = filters.semesterId;
      if (filters.studentId) params.studentId = filters.studentId;
      if (filters.status) params.status = filters.status;

      const response = await financeApi.getAllInvoices(params);
      setInvoices(response.data || []);
      setTotalPages(response.meta?.totalPages || 1);
      setTotal(response.meta?.total || 0);
    } catch {
      setError(
        locale === 'vi'
          ? 'Hiện chưa thể tải danh sách hóa đơn.'
          : 'Invoices could not be loaded.',
      );
    } finally {
      setIsLoading(false);
    }
  }, [filters.semesterId, filters.status, filters.studentId, locale, page]);

  const copy = useMemo(
    () =>
      locale === 'vi'
        ? {
          loading: 'Đang tải hóa đơn',
          title: 'Hóa đơn',
          description:
            'Theo dõi sức khỏe hóa đơn, tạo billing theo học kỳ có kiểm soát và giữ phần follow-up thanh toán luôn rõ ràng.',
          refresh: 'Làm mới',
          exportCsv: 'Xuất CSV',
          generate: 'Tạo hóa đơn',
          semester: 'Học kỳ',
          status: 'Trạng thái',
          allSemesters: 'Tất cả học kỳ',
          allStatuses: 'Tất cả trạng thái',
          statusOptions: {
            DRAFT: 'Nháp',
            PENDING: 'Chờ thanh toán',
            PAID: 'Đã thanh toán',
            OVERDUE: 'Quá hạn',
            PARTIALLY_PAID: 'Thanh toán một phần',
            CANCELLED: 'Đã hủy',
          },
          pageSummaryEmpty: '0 hóa đơn',
          pageSummary: (count: number) => `${formatNumber(count)} hóa đơn`,
          clearFilters: 'Xóa bộ lọc',
          unavailableTitle: 'Hóa đơn chưa sẵn sàng',
          emptyTitle: 'Không có hóa đơn phù hợp',
          emptyDescription:
            'Khi học kỳ sẵn sàng để billing, hóa đơn sẽ xuất hiện ở đây cùng trạng thái thanh toán và hạn thanh toán.',
          tableTitle: 'Bản ghi hóa đơn',
          headers: {
            invoice: 'Số hóa đơn',
            student: 'Sinh viên',
            semester: 'Học kỳ',
            amount: 'Số tiền',
            dueDate: 'Hạn thanh toán',
            status: 'Trạng thái',
            actions: 'Tác vụ',
          },
          noEmail: 'Chưa có email',
          unassigned: 'Chưa gán',
          deleteTitle: 'Xóa hóa đơn',
          deleteMessage: (invoiceNumber: string) =>
            `Xóa ${invoiceNumber}? Hành động này sẽ gỡ hóa đơn khỏi màn hình quản trị hiện tại.`,
          deleteConfirm: 'Xóa hóa đơn',
          deleted: 'Đã xóa hóa đơn',
          deleteFailed: 'Hiện chưa thể xóa hóa đơn này.',
          statusUpdated: 'Đã cập nhật trạng thái hóa đơn',
          statusUpdateFailed: 'Hiện chưa thể cập nhật trạng thái hóa đơn.',
          selectSemesterFirst: 'Hãy chọn học kỳ trước khi tạo hóa đơn.',
          generateTitle: 'Tạo hóa đơn theo học kỳ',
          generateMessage:
            'Tạo hóa đơn cho toàn bộ sinh viên có đăng ký đã xác nhận trong học kỳ đang chọn?',
          generateConfirm: 'Tạo hóa đơn',
          generateSuccess: (generated: number, skipped: number) =>
            `Đã tạo ${formatNumber(generated)} hóa đơn và bỏ qua ${formatNumber(skipped)} bản ghi.`,
          generateFailed: 'Hiện chưa thể tạo hóa đơn theo học kỳ.',
          exportStarted: 'Đã bắt đầu xuất dữ liệu hóa đơn',
          exportFailed: 'Hiện chưa thể xuất dữ liệu hóa đơn.',
          detailFailed: 'Hiện chưa thể tải chi tiết hóa đơn.',
          viewLabel: (invoiceNumber: string) => `Xem hóa đơn ${invoiceNumber}`,
          deleteLabel: (invoiceNumber: string) => `Xóa hóa đơn ${invoiceNumber}`,
          detailTitle: (invoiceNumber?: string) =>
            invoiceNumber ? `Hóa đơn ${invoiceNumber}` : 'Chi tiết hóa đơn',
          closeDetail: 'Đóng chi tiết hóa đơn',
          studentInformation: 'Thông tin sinh viên',
          name: 'Tên',
          email: 'Email',
          invoiceItems: 'Khoản mục hóa đơn',
          descriptionLabel: 'Mô tả',
          quantity: 'SL',
          unitPrice: 'Đơn giá',
          totalLabel: 'Tổng cộng',
          statusActions: 'Tác vụ trạng thái',
          markPending: 'Đánh dấu chờ thanh toán',
          markPaid: 'Đánh dấu đã thanh toán',
          cancelInvoice: 'Hủy hóa đơn',
          close: 'Đóng',
        }
        : {
          loading: 'Loading invoices',
          title: 'Invoices',
          description:
            'Review invoice health, generate semester billing in a controlled way, and keep payment follow-up readable.',
          refresh: 'Refresh',
          exportCsv: 'Export CSV',
          generate: 'Generate invoices',
          semester: 'Semester',
          status: 'Status',
          allSemesters: 'All semesters',
          allStatuses: 'All statuses',
          statusOptions: {
            DRAFT: 'Draft',
            PENDING: 'Pending',
            PAID: 'Paid',
            OVERDUE: 'Overdue',
            PARTIALLY_PAID: 'Partially paid',
            CANCELLED: 'Cancelled',
          },
          pageSummaryEmpty: '0 invoices',
          pageSummary: (count: number) => `${formatNumber(count)} invoices`,
          clearFilters: 'Clear filters',
          unavailableTitle: 'Invoices unavailable',
          emptyTitle: 'No matching invoices',
          emptyDescription:
            'Once a semester is ready for billing, generated invoices will appear here with payment status and due dates.',
          tableTitle: 'Invoice records',
          headers: {
            invoice: 'Invoice #',
            student: 'Student',
            semester: 'Semester',
            amount: 'Amount',
            dueDate: 'Due date',
            status: 'Status',
            actions: 'Actions',
          },
          noEmail: 'No email',
          unassigned: 'Unassigned',
          deleteTitle: 'Delete invoice',
          deleteMessage: (invoiceNumber: string) =>
            `Delete ${invoiceNumber}? This removes the invoice from the current admin view.`,
          deleteConfirm: 'Delete invoice',
          deleted: 'Invoice deleted',
          deleteFailed: 'We could not delete that invoice.',
          statusUpdated: 'Invoice status updated',
          statusUpdateFailed: 'The invoice status could not be updated.',
          selectSemesterFirst: 'Select a semester before generating invoices.',
          generateTitle: 'Generate semester invoices',
          generateMessage:
            'Generate invoices for all students with confirmed enrollments in the selected semester?',
          generateConfirm: 'Generate invoices',
          generateSuccess: (generated: number, skipped: number) =>
            `Generated ${formatNumber(generated)} invoices and skipped ${formatNumber(skipped)}.`,
          generateFailed: 'Semester invoices could not be generated.',
          exportStarted: 'Invoice export started',
          exportFailed: 'Invoices could not be exported.',
          detailFailed: 'Invoice details could not be loaded.',
          viewLabel: (invoiceNumber: string) => `View invoice ${invoiceNumber}`,
          deleteLabel: (invoiceNumber: string) => `Delete invoice ${invoiceNumber}`,
          detailTitle: (invoiceNumber?: string) =>
            invoiceNumber ? `Invoice ${invoiceNumber}` : 'Invoice details',
          closeDetail: 'Close invoice details',
          studentInformation: 'Student information',
          name: 'Name',
          email: 'Email',
          invoiceItems: 'Invoice items',
          descriptionLabel: 'Description',
          quantity: 'Qty',
          unitPrice: 'Unit price',
          totalLabel: 'Total',
          statusActions: 'Status actions',
          markPending: 'Mark as pending',
          markPaid: 'Mark as paid',
          cancelInvoice: 'Cancel invoice',
          close: 'Close',
        },
    [formatNumber, locale],
  );

  useEffect(() => {
    if (canAccess) {
      void fetchSemesters();
      void fetchInvoices();
    }
  }, [canAccess, fetchInvoices, fetchSemesters]);

  const semesterOptions = useMemo(
    () => [
      { value: '', label: copy.allSemesters },
      ...semesters.map((semester) => ({
        value: semester.id,
        label: getLocalizedName(locale, semester, semester.name),
      })),
    ],
    [copy.allSemesters, locale, semesters],
  );

  const semesterLookup = useMemo(
    () =>
      new Map(
        semesters.map((semester) => [semester.id, semester] as const),
      ),
    [semesters],
  );

  const statusOptions = useMemo(
    () => [
      { value: '', label: copy.allStatuses },
      { value: 'DRAFT', label: copy.statusOptions.DRAFT },
      { value: 'PENDING', label: copy.statusOptions.PENDING },
      { value: 'PAID', label: copy.statusOptions.PAID },
      { value: 'OVERDUE', label: copy.statusOptions.OVERDUE },
      { value: 'PARTIALLY_PAID', label: copy.statusOptions.PARTIALLY_PAID },
      { value: 'CANCELLED', label: copy.statusOptions.CANCELLED },
    ],
    [copy.allStatuses, copy.statusOptions],
  );

  const pageSummary = useMemo(() => {
    if (invoices.length === 0) {
      return copy.pageSummaryEmpty;
    }

    return copy.pageSummary(total);
  }, [copy, invoices.length, total]);

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={copy.loading} className="m-8" />;
  }

  const handleViewDetail = async (invoice: Invoice) => {
    try {
      const detail = await financeApi.getInvoiceById(invoice.id);
      setSelectedInvoice(detail);
      setIsDetailOpen(true);
    } catch {
      toast.error(copy.detailFailed);
    }
  };

  const handleDelete = async (invoice: Invoice) => {
    const shouldDelete = await confirm({
      title: copy.deleteTitle,
      message: copy.deleteMessage(invoice.invoiceNumber),
      confirmText: copy.deleteConfirm,
      variant: 'destructive',
    });

    if (!shouldDelete) {
      return;
    }

    try {
      await financeApi.deleteInvoice(invoice.id);
      toast.success(copy.deleted);
      await fetchInvoices();
    } catch {
      toast.error(copy.deleteFailed);
    }
  };

  const handleUpdateStatus = async (invoiceId: string, status: string) => {
    try {
      await financeApi.updateInvoice(invoiceId, { status });
      toast.success(copy.statusUpdated);
      await fetchInvoices();

      if (selectedInvoice?.id === invoiceId) {
        const detail = await financeApi.getInvoiceById(invoiceId);
        setSelectedInvoice(detail);
      }
    } catch {
      toast.error(copy.statusUpdateFailed);
    }
  };

  const handleGenerateInvoices = async () => {
    if (!filters.semesterId) {
      toast.error(copy.selectSemesterFirst);
      return;
    }

    const shouldGenerate = await confirm({
      title: copy.generateTitle,
      message: copy.generateMessage,
      confirmText: copy.generateConfirm,
    });

    if (!shouldGenerate) {
      return;
    }

    try {
      const result = await financeApi.generateSemesterInvoices(filters.semesterId);
      toast.success(
        copy.generateSuccess(Number(result.generated), Number(result.skipped)),
      );
      await fetchInvoices();
    } catch {
      toast.error(copy.generateFailed);
    }
  };

  const handleExportCsv = async () => {
    try {
      const csvData = await financeApi.exportInvoicesCsv(filters);
      const blob = new Blob([csvData], { type: 'text/csv;charset=utf-8;' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = `invoices_${new Date().toISOString().split('T')[0]}.csv`;
      link.click();
      toast.success(copy.exportStarted);
    } catch {
      toast.error(copy.exportFailed);
    }
  };

  return (
    <AdminFrame
      title={copy.title}
      description={copy.description}
      actions={
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" onClick={() => void fetchInvoices()}>
            <RefreshCw className="mr-2 h-4 w-4" />
            {copy.refresh}
          </Button>
          <Button variant="outline" onClick={() => void handleExportCsv()}>
            <Download className="mr-2 h-4 w-4" />
            {copy.exportCsv}
          </Button>
          <Button onClick={() => void handleGenerateInvoices()}>
            <Plus className="mr-2 h-4 w-4" />
            {copy.generate}
          </Button>
        </div>
      }
    >
      <div className="space-y-6">
        <AdminToolbarCard>
            <div className="grid gap-4 md:grid-cols-3">
              <Select
                label={copy.semester}
                value={filters.semesterId}
                onChange={(event) => {
                  setFilters((current) => ({
                    ...current,
                    semesterId: event.target.value,
                  }));
                  setPage(1);
                }}
                options={semesterOptions}
              />
              <Select
                label={copy.status}
                value={filters.status}
                onChange={(event) => {
                  setFilters((current) => ({
                    ...current,
                    status: event.target.value,
                  }));
                  setPage(1);
                }}
                options={statusOptions}
              />
              <div className="flex items-end gap-2">
                <Button
                  variant="outline"
                  onClick={() => {
                    setFilters({ semesterId: '', studentId: '', status: '' });
                    setPage(1);
                  }}
                >
                  {copy.clearFilters}
                </Button>
                <div className="text-sm text-muted-foreground">{pageSummary}</div>
              </div>
            </div>
        </AdminToolbarCard>

        {error ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={error}
            onRetry={() => void fetchInvoices()}
          />
        ) : isLoading ? (
          <LoadingState label={copy.loading} />
        ) : invoices.length === 0 ? (
          <EmptyState
            icon={Receipt}
            title={copy.emptyTitle}
            description={copy.emptyDescription}
          />
        ) : (
          <AdminTableCard
            title={copy.tableTitle}
            footer={
              <AdminPaginationFooter
                summary={`${messages.common.states.page} ${page} ${messages.common.states.of} ${totalPages}`}
                page={page}
                totalPages={totalPages}
                onPrevious={() => setPage((current) => current - 1)}
                onNext={() => setPage((current) => current + 1)}
              />
            }
          >
              <div className="space-y-3 md:hidden" role="list" aria-label={copy.tableTitle}>
                {invoices.map((invoice) => {
                  const studentLabel = invoice.student?.user
                    ? `${invoice.student.user.firstName} ${invoice.student.user.lastName}`
                    : invoice.studentId;
                  const semesterLabel = getLocalizedName(
                    locale,
                    semesterLookup.get(invoice.semesterId) ?? invoice.semester,
                    invoice.semester?.name || copy.unassigned,
                  );
                  const statusLabel =
                    copy.statusOptions[invoice.status as keyof typeof copy.statusOptions] ??
                    invoice.status.replace(/_/g, ' ');

                  return (
                    <article
                      key={`${invoice.id}-mobile`}
                      className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                      role="listitem"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                            {invoice.invoiceNumber}
                          </p>
                          <h3 className="mt-1 truncate font-semibold text-foreground">
                            {studentLabel}
                          </h3>
                          <p className="mt-1 truncate text-sm text-muted-foreground">
                            {invoice.student?.user?.email || copy.noEmail}
                          </p>
                        </div>
                        <span
                          className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-medium ${statusColors[invoice.status] || 'bg-secondary text-foreground'}`}
                        >
                          {statusLabel}
                        </span>
                      </div>
                      <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-border/60 pt-3 text-sm">
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.headers.semester}
                          </dt>
                          <dd className="mt-1 text-foreground">{semesterLabel}</dd>
                        </div>
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.headers.amount}
                          </dt>
                          <dd className="mt-1 font-medium text-foreground">
                            {formatCurrency(Number(invoice.total))}
                          </dd>
                        </div>
                        <div className="col-span-2">
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.headers.dueDate}
                          </dt>
                          <dd className="mt-1 text-foreground">{formatDate(invoice.dueDate)}</dd>
                        </div>
                      </dl>
                      <AdminRowActions className="mt-4 border-t border-border/60 pt-3">
                        <Button
                          size="icon"
                          variant="ghost"
                          onClick={() => void handleViewDetail(invoice)}
                          aria-label={copy.viewLabel(invoice.invoiceNumber)}
                          title={copy.viewLabel(invoice.invoiceNumber)}
                        >
                          <Eye className="h-4 w-4" />
                        </Button>
                        <Button
                          size="icon"
                          variant="ghost"
                          className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                          onClick={() => void handleDelete(invoice)}
                          aria-label={copy.deleteLabel(invoice.invoiceNumber)}
                          title={copy.deleteLabel(invoice.invoiceNumber)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </AdminRowActions>
                    </article>
                  );
                })}
              </div>
              <AdminTableScroll className="hidden md:block">
                <table className="w-full min-w-[900px] text-sm">
                  <thead>
                    <tr className="border-b border-border/70 text-left text-muted-foreground">
                      <th className="px-2 py-3 font-medium">{copy.headers.invoice}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.student}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.semester}</th>
                      <th className="px-2 py-3 font-medium text-right">{copy.headers.amount}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.dueDate}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.status}</th>
                      <th className="px-2 py-3 text-right font-medium">{copy.headers.actions}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {invoices.map((invoice) => (
                      <tr key={invoice.id}>
                        <td className="px-2 py-4 font-medium text-foreground">
                          {invoice.invoiceNumber}
                        </td>
                        <td className="px-2 py-4">
                          <div className="space-y-1">
                            <p className="font-medium text-foreground">
                              {invoice.student?.user
                                ? `${invoice.student.user.firstName} ${invoice.student.user.lastName}`
                                : invoice.studentId}
                            </p>
                            <p className="text-xs text-muted-foreground">
                              {invoice.student?.user?.email || copy.noEmail}
                            </p>
                          </div>
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {getLocalizedName(
                            locale,
                            semesterLookup.get(invoice.semesterId) ?? invoice.semester,
                            invoice.semester?.name || copy.unassigned,
                          )}
                        </td>
                        <td className="px-2 py-4 text-right font-medium text-foreground">
                          {formatCurrency(Number(invoice.total))}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {formatDate(invoice.dueDate)}
                        </td>
                        <td className="px-2 py-4">
                          <span
                            className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${statusColors[invoice.status] || 'bg-secondary text-foreground'}`}
                          >
                            {copy.statusOptions[invoice.status as keyof typeof copy.statusOptions] ??
                              invoice.status.replace(/_/g, ' ')}
                          </span>
                        </td>
                        <td className="px-2 py-4">
                          <AdminRowActions>
                            <Button
                              size="icon"
                              variant="ghost"
                              onClick={() => void handleViewDetail(invoice)}
                              aria-label={copy.viewLabel(invoice.invoiceNumber)}
                              title={copy.viewLabel(invoice.invoiceNumber)}
                            >
                              <Eye className="h-4 w-4" />
                            </Button>
                            <Button
                              size="icon"
                              variant="ghost"
                              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                              onClick={() => void handleDelete(invoice)}
                              aria-label={copy.deleteLabel(invoice.invoiceNumber)}
                              title={copy.deleteLabel(invoice.invoiceNumber)}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </AdminRowActions>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </AdminTableScroll>
          </AdminTableCard>
        )}
      </div>

      <Modal
        isOpen={isDetailOpen}
        onClose={() => setIsDetailOpen(false)}
        title={copy.detailTitle(selectedInvoice?.invoiceNumber)}
        closeLabel={copy.closeDetail}
        className="max-w-3xl"
      >
        {selectedInvoice ? (
          <div className="space-y-6">
            <div className="rounded-lg border border-border/70 bg-secondary/30 p-4">
              <h4 className="font-semibold text-foreground">{copy.studentInformation}</h4>
              <div className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <span className="text-muted-foreground">{copy.name}:</span>{' '}
                  <span className="font-medium text-foreground">
                    {selectedInvoice.student?.user
                      ? `${selectedInvoice.student.user.firstName} ${selectedInvoice.student.user.lastName}`
                      : selectedInvoice.studentId}
                  </span>
                </div>
                <div>
                  <span className="text-muted-foreground">{copy.email}:</span>{' '}
                  <span className="font-medium text-foreground">
                    {selectedInvoice.student?.user?.email || copy.noEmail}
                  </span>
                </div>
              </div>
            </div>

            <div>
              <h4 className="font-semibold text-foreground">{copy.invoiceItems}</h4>
              <div className="mt-3 space-y-3 md:hidden" role="list" aria-label={copy.invoiceItems}>
                {selectedInvoice.items?.map((item) => (
                  <article
                    key={`${item.id}-mobile`}
                    className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                    role="listitem"
                  >
                    <p className="font-medium text-foreground">
                      {getLocalizedInvoiceItemDescription(locale, item.description)}
                    </p>
                    <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
                      <div>
                        <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          {copy.quantity}
                        </dt>
                        <dd className="mt-1 text-foreground">{item.quantity}</dd>
                      </div>
                      <div>
                        <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          {copy.unitPrice}
                        </dt>
                        <dd className="mt-1 text-foreground">{formatCurrency(item.unitPrice)}</dd>
                      </div>
                      <div className="col-span-2 border-t border-border/60 pt-3">
                        <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          {copy.totalLabel}
                        </dt>
                        <dd className="mt-1 font-semibold text-foreground">
                          {formatCurrency(item.total)}
                        </dd>
                      </div>
                    </dl>
                  </article>
                ))}
                <div className="flex items-center justify-between border-t border-border/70 pt-3 text-sm">
                  <span className="font-semibold text-foreground">{copy.totalLabel}</span>
                  <span className="font-semibold text-foreground">
                    {formatCurrency(Number(selectedInvoice.total))}
                  </span>
                </div>
              </div>
              <AdminTableScroll className="mt-3 hidden md:block">
                <table className="w-full min-w-[560px] text-sm">
                  <thead>
                    <tr className="border-b border-border/70 text-left text-muted-foreground">
                      <th className="py-2 pr-4 font-medium">{copy.descriptionLabel}</th>
                      <th className="py-2 pr-4 text-right font-medium">{copy.quantity}</th>
                      <th className="py-2 pr-4 text-right font-medium">{copy.unitPrice}</th>
                      <th className="py-2 text-right font-medium">{copy.totalLabel}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {selectedInvoice.items?.map((item) => (
                      <tr key={item.id}>
                        <td className="py-3 pr-4 text-foreground">
                          {getLocalizedInvoiceItemDescription(locale, item.description)}
                        </td>
                        <td className="py-3 pr-4 text-right text-muted-foreground">
                          {item.quantity}
                        </td>
                        <td className="py-3 pr-4 text-right text-muted-foreground">
                          {formatCurrency(item.unitPrice)}
                        </td>
                        <td className="py-3 text-right font-medium text-foreground">
                          {formatCurrency(item.total)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr className="border-t border-border/70">
                      <td
                        colSpan={3}
                        className="py-3 pr-4 text-right font-semibold text-foreground"
                      >
                        {copy.totalLabel}
                      </td>
                      <td className="py-3 text-right font-semibold text-foreground">
                        {formatCurrency(Number(selectedInvoice.total))}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </AdminTableScroll>
            </div>

            <div className="space-y-3">
              <h4 className="font-semibold text-foreground">{copy.statusActions}</h4>
              <div className="flex flex-wrap gap-2">
                {selectedInvoice.status === 'DRAFT' ? (
                  <Button
                    size="sm"
                    onClick={() =>
                      void handleUpdateStatus(selectedInvoice.id, 'PENDING')
                    }
                  >
                    {copy.markPending}
                  </Button>
                ) : null}
                {selectedInvoice.status === 'PENDING' ? (
                  <Button
                    size="sm"
                    onClick={() => void handleUpdateStatus(selectedInvoice.id, 'PAID')}
                  >
                    {copy.markPaid}
                  </Button>
                ) : null}
                {selectedInvoice.status !== 'CANCELLED' &&
                selectedInvoice.status !== 'PAID' ? (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() =>
                      void handleUpdateStatus(selectedInvoice.id, 'CANCELLED')
                    }
                  >
                    {copy.cancelInvoice}
                  </Button>
                ) : null}
              </div>
            </div>

            <AdminDialogFooter className="pt-0">
              <Button variant="outline" onClick={() => setIsDetailOpen(false)}>
                {copy.close}
              </Button>
            </AdminDialogFooter>
          </div>
        ) : null}
      </Modal>

      {confirmationDialog}
    </AdminFrame>
  );
}
