'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import {
  ArrowUpRight,
  CreditCard,
  Eye,
  Globe,
  Landmark,
  Receipt,
  RefreshCw,
  ShieldCheck,
  Smartphone,
  Wallet,
  XCircle,
} from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import {
  WorkspaceMetricCard,
  WorkspacePanel,
} from '@/components/dashboard/WorkspaceSurface';
import { useRequireAuth } from '@/context/AuthContext';
import {
  financeApi,
  semestersApi,
  type FinanceStudentInvoice,
  type FinanceStudentInvoiceDetail,
  type StudentCheckoutFlow,
  type StudentCheckoutIntent,
  type StudentCheckoutNextAction,
  type StudentCheckoutProvider,
  type StudentCheckoutSession,
} from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Modal } from '@/components/ui/modal';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useI18n } from '@/i18n';
import type { Semester } from '@/types/api';
import { getLocalizedFlatLabel, getLocalizedName } from '@/lib/academic-content';
import { getLocalizedInvoiceItemDescription } from '@/lib/finance-content';
import { toast } from 'sonner';

const invoiceStatusTone: Record<string, string> = {
  DRAFT: 'bg-secondary text-foreground',
  PENDING: 'bg-amber-500/12 text-amber-700 dark:text-amber-300',
  PAID: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
  OVERDUE: 'bg-rose-500/12 text-rose-700 dark:text-rose-300',
  PARTIALLY_PAID: 'bg-blue-500/12 text-blue-700 dark:text-blue-300',
  CANCELLED: 'bg-secondary text-muted-foreground',
};

const checkoutStatusTone: Record<string, string> = {
  REQUIRES_ACTION: 'bg-blue-500/12 text-blue-700 dark:text-blue-300',
  PROCESSING: 'bg-amber-500/12 text-amber-700 dark:text-amber-300',
  SUCCEEDED: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
  FAILED: 'bg-rose-500/12 text-rose-700 dark:text-rose-300',
  CANCELLED: 'bg-secondary text-muted-foreground',
  EXPIRED: 'bg-secondary text-muted-foreground',
};

function isCheckoutIntent(
  session: StudentCheckoutSession | null,
): session is StudentCheckoutIntent {
  return Boolean(session && 'intentNumber' in session);
}

function toFriendlyError(error: unknown, fallback: string) {
  if (error instanceof AxiosError) {
    const message = error.response?.data;

    if (typeof message === 'string' && message.trim()) {
      return message;
    }

    if (
      message &&
      typeof message === 'object' &&
      'message' in message &&
      typeof message.message === 'string' &&
      message.message.trim()
    ) {
      return message.message;
    }

    if (
      message &&
      typeof message === 'object' &&
      'message' in message &&
      Array.isArray(message.message) &&
      message.message.length > 0 &&
      typeof message.message[0] === 'string'
    ) {
      return message.message[0];
    }
  }

  return fallback;
}

function summarizeInvoice(detail: FinanceStudentInvoiceDetail): FinanceStudentInvoice {
  return {
    id: detail.id,
    invoiceNumber: detail.invoiceNumber,
    semesterName: detail.semesterName,
    semesterNameEn: detail.semesterNameEn,
    semesterNameVi: detail.semesterNameVi,
    semesterId: detail.semesterId,
    status: detail.status,
    subtotal: detail.subtotal,
    discount: detail.discount,
    total: detail.total,
    dueDate: detail.dueDate,
    paidAt: detail.paidAt,
    createdAt: detail.createdAt,
    paidAmount: detail.paidAmount,
    balance: detail.balance,
  };
}

function getInvoiceStatusLabel(locale: 'en' | 'vi', status: string) {
  const vi: Record<string, string> = {
    DRAFT: 'Bản nháp',
    PENDING: 'Chờ thanh toán',
    PAID: 'Đã thanh toán',
    OVERDUE: 'Quá hạn',
    PARTIALLY_PAID: 'Thanh toán một phần',
    CANCELLED: 'Đã hủy',
  };
  const en: Record<string, string> = {
    DRAFT: 'Draft',
    PENDING: 'Awaiting payment',
    PAID: 'Paid',
    OVERDUE: 'Overdue',
    PARTIALLY_PAID: 'Partially paid',
    CANCELLED: 'Cancelled',
  };

  return (locale === 'vi' ? vi : en)[status] ?? status;
}

function getCheckoutStatusLabel(locale: 'en' | 'vi', status: string) {
  const vi: Record<string, string> = {
    REQUIRES_ACTION: 'Chờ chọn bước tiếp theo',
    PROCESSING: 'Đang xử lý',
    SUCCEEDED: 'Đã xác nhận',
    FAILED: 'Thất bại',
    CANCELLED: 'Đã hủy',
    EXPIRED: 'Hết hạn',
    CREATED: 'Đã tạo',
    REDIRECT_REQUIRED: 'Chờ xác nhận',
  };
  const en: Record<string, string> = {
    REQUIRES_ACTION: 'Ready for payment',
    PROCESSING: 'Processing',
    SUCCEEDED: 'Confirmed',
    FAILED: 'Failed',
    CANCELLED: 'Cancelled',
    EXPIRED: 'Expired',
    CREATED: 'Created',
    REDIRECT_REQUIRED: 'Awaiting confirmation',
  };

  return (locale === 'vi' ? vi : en)[status] ?? status;
}

function getTimelineEventLabel(locale: 'en' | 'vi', type: string) {
  const vi: Record<string, string> = {
    INTENT_CREATED: 'Phiên thanh toán đã được tạo',
    ATTEMPT_CREATED: 'Đã tạo lượt thử thanh toán',
    SIGNAL_RECEIVED: 'Đã nhận tín hiệu từ cổng thanh toán',
    VERIFICATION_PASSED: 'Cổng thanh toán đã xác nhận',
    VERIFICATION_FAILED: 'Xác nhận từ cổng thanh toán thất bại',
    STATE_TRANSITION: 'Trạng thái đã thay đổi',
    PAYMENT_RECORDED: 'Thanh toán đã được ghi nhận',
    MANUAL_REVIEW_REQUIRED: 'Cần rà soát thủ công',
  };
  const en: Record<string, string> = {
    INTENT_CREATED: 'Checkout created',
    ATTEMPT_CREATED: 'Payment attempt started',
    SIGNAL_RECEIVED: 'Provider signal received',
    VERIFICATION_PASSED: 'Provider confirmation received',
    VERIFICATION_FAILED: 'Provider confirmation failed',
    STATE_TRANSITION: 'Status changed',
    PAYMENT_RECORDED: 'Payment recorded',
    MANUAL_REVIEW_REQUIRED: 'Manual review required',
  };

  return (locale === 'vi' ? vi : en)[type] ?? type;
}

function getPaymentMethodLabel(locale: 'en' | 'vi', method: string) {
  const normalized = method.toUpperCase();
  const labels: Record<string, { en: string; vi: string }> = {
    MOMO_SANDBOX: { en: 'MoMo', vi: 'MoMo' },
    ZALOPAY_SANDBOX: { en: 'ZaloPay', vi: 'ZaloPay' },
    VNPAY_SANDBOX: { en: 'VNPay', vi: 'VNPay' },
    PAYPAL_SANDBOX: { en: 'PayPal', vi: 'PayPal' },
    CARD_SANDBOX: { en: 'Card checkout', vi: 'Thanh toán thẻ' },
  };

  return labels[normalized]?.[locale] ?? method;
}

function getProviderDefinitions(locale: 'en' | 'vi') {
  return [
    {
      provider: 'MOMO' as const,
      label: 'MoMo',
      description:
        locale === 'vi'
          ? 'Ví điện tử phổ biến cho sinh viên nội địa.'
          : 'Mobile wallet flow for local tuition checkout.',
      icon: Smartphone,
      toneClassName: 'bg-rose-500/12 text-rose-700 dark:text-rose-300',
    },
    {
      provider: 'ZALOPAY' as const,
      label: 'ZaloPay',
      description:
        locale === 'vi'
          ? 'Lựa chọn ví điện tử nhanh với trải nghiệm quen thuộc.'
          : 'Wallet checkout with a familiar mobile-first flow.',
      icon: Wallet,
      toneClassName: 'bg-sky-500/12 text-sky-700 dark:text-sky-300',
    },
    {
      provider: 'VNPAY' as const,
      label: 'VNPay',
      description:
        locale === 'vi'
          ? 'Luồng redirect phù hợp cho ngân hàng và ứng dụng nội địa.'
          : 'Redirect-based local banking and VNPay checkout.',
      icon: Landmark,
      toneClassName: 'bg-indigo-500/12 text-indigo-700 dark:text-indigo-300',
    },
    {
      provider: 'PAYPAL' as const,
      label: 'PayPal',
      description:
        locale === 'vi'
          ? 'Tùy chọn ví và phê duyệt quốc tế cho học viên phù hợp.'
          : 'International approval flow for supported learners.',
      icon: Globe,
      toneClassName: 'bg-blue-500/12 text-blue-700 dark:text-blue-300',
    },
    {
      provider: 'CARD' as const,
      label: locale === 'vi' ? 'Visa / thẻ quốc tế' : 'Visa / international card',
      description:
        locale === 'vi'
          ? 'Thanh toán an toàn qua cổng thẻ được xác minh cho học phí và lệ phí.'
          : 'A verified card checkout for tuition and service payments.',
      icon: CreditCard,
      toneClassName: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
    },
  ];
}

function getCheckoutActionHref(nextAction?: StudentCheckoutNextAction | null) {
  if (!nextAction) {
    return null;
  }

  return (
    nextAction.redirectUrl ??
    nextAction.approvalUrl ??
    nextAction.hostedCheckoutUrl ??
    nextAction.deeplinkUrl ??
    null
  );
}

function getCheckoutActionLabel(
  locale: 'en' | 'vi',
  flow?: StudentCheckoutFlow | null,
) {
  const vi: Record<StudentCheckoutFlow, string> = {
    REDIRECT: 'Tiếp tục tới cổng thanh toán',
    QR: 'Mở bước xác nhận QR',
    APPROVAL: 'Tiếp tục sang bước phê duyệt',
    HOSTED_CARD: 'Mở cổng thanh toán thẻ',
  };
  const en: Record<StudentCheckoutFlow, string> = {
    REDIRECT: 'Continue to provider',
    QR: 'Open QR confirmation',
    APPROVAL: 'Continue to approval',
    HOSTED_CARD: 'Open secure card checkout',
  };

  if (!flow) {
    return locale === 'vi' ? 'Tiếp tục thanh toán' : 'Continue checkout';
  }

  return (locale === 'vi' ? vi : en)[flow];
}

function getCheckoutActionDescription(
  locale: 'en' | 'vi',
  flow?: StudentCheckoutFlow | null,
) {
  const vi: Record<StudentCheckoutFlow, string> = {
    REDIRECT:
      'Bạn sẽ được chuyển sang bước xác nhận an toàn của nhà cung cấp rồi quay lại CampusCore.',
    QR:
      'Dùng mã xác nhận hoặc liên kết mở ứng dụng bên dưới để hoàn tất bước xác nhận với nhà cung cấp.',
    APPROVAL:
      'Mở bước phê duyệt của nhà cung cấp và quay lại để xem trạng thái đối soát.',
    HOSTED_CARD:
      'Bạn sẽ hoàn tất bước xác nhận trên cổng thẻ an toàn rồi quay lại CampusCore.',
  };
  const en: Record<StudentCheckoutFlow, string> = {
    REDIRECT:
      'You will continue through the provider confirmation step and then return to CampusCore.',
    QR:
      'Use the confirmation code or app link below to complete the provider confirmation step.',
    APPROVAL:
      'Open the approval step in the provider handoff and then return to review reconciliation status.',
    HOSTED_CARD:
      'You will finish the secure card confirmation step and then return to CampusCore.',
  };

  if (!flow) {
    return locale === 'vi'
      ? 'Phiên thanh toán sẽ tiếp tục trên bước xác nhận bên ngoài rồi quay lại hóa đơn.'
      : 'This payment session will continue through an external confirmation step before returning to the invoice.';
  }

  return (locale === 'vi' ? vi : en)[flow];
}

function getCheckoutReturnMessage(
  locale: 'en' | 'vi',
  status?: string | null,
  provider?: string | null,
) {
  if (!status) {
    return null;
  }

  const providerLabel = provider ? provider.toUpperCase() : null;
  const vi: Record<string, string> = {
    success: providerLabel
      ? `Phiên ${providerLabel} đã xác nhận thanh toán.`
      : 'Phiên thanh toán đã được xác nhận.',
    processing: providerLabel
      ? `Phiên ${providerLabel} đang được xác nhận.`
      : 'Phiên thanh toán đang được xác nhận.',
    failed: providerLabel
      ? `Phiên ${providerLabel} chưa hoàn tất.`
      : 'Phiên thanh toán chưa hoàn tất.',
    cancelled: providerLabel
      ? `Phiên ${providerLabel} đã bị hủy.`
      : 'Phiên thanh toán đã bị hủy.',
  };
  const en: Record<string, string> = {
    success: providerLabel
      ? `${providerLabel} confirmed the payment.`
      : 'The payment session was confirmed.',
    processing: providerLabel
      ? `${providerLabel} is still processing the checkout.`
      : 'The payment session is still processing.',
    failed: providerLabel
      ? `${providerLabel} did not complete the payment.`
      : 'The payment session did not complete.',
    cancelled: providerLabel
      ? `${providerLabel} cancelled the payment session.`
      : 'The payment session was cancelled.',
  };

  return (locale === 'vi' ? vi : en)[status] ?? null;
}

export default function StudentInvoicesPage() {
  const { hasAccess, isLoading: authLoading } = useRequireAuth(['STUDENT']);
  const { locale, formatCurrency, formatDate, formatDateTime, formatNumber } =
    useI18n();
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const [invoices, setInvoices] = useState<FinanceStudentInvoice[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<string | null>(
    null,
  );
  const [selectedInvoice, setSelectedInvoice] =
    useState<FinanceStudentInvoiceDetail | null>(null);
  const [activeCheckout, setActiveCheckout] =
    useState<StudentCheckoutSession | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [isCheckoutLoading, setIsCheckoutLoading] =
    useState<StudentCheckoutProvider | null>(null);
  const [error, setError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [handledReturnKey, setHandledReturnKey] = useState<string | null>(null);

  const formatMoney = useCallback(
    (value: number) =>
      formatCurrency(value, 'VND', {
        maximumFractionDigits: 0,
      }),
    [formatCurrency],
  );

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Không gian sinh viên',
          title: 'Học phí và thanh toán',
          description:
            'Theo dõi số dư học phí, mở chi tiết hóa đơn và chọn phương thức thanh toán phù hợp trong một luồng rõ ràng.',
          selectSemester: 'Chọn học kỳ cho hóa đơn',
          allSemesters: 'Tất cả học kỳ',
          loading: 'Đang tải dữ liệu học phí',
          unavailableTitle: 'Khu vực thanh toán chưa sẵn sàng',
          unavailableDescription:
            'Hiện chưa thể tải dữ liệu hóa đơn. Hãy thử lại sau ít phút.',
          emptyTitle: 'Chưa có hóa đơn nào',
          emptyDescription:
            'Khi học phí hoặc phí dịch vụ được phát hành cho tài khoản của bạn, chúng sẽ xuất hiện tại đây.',
          metrics: {
            outstanding: 'Số dư cần theo dõi',
            invoices: 'Hóa đơn trong màn hình',
            dueSoon: 'Hóa đơn cần xử lý sớm',
          },
          details: 'Xem chi tiết',
          detailTitle: 'Chi tiết hóa đơn',
          closeDetail: 'Đóng chi tiết hóa đơn',
          viewDetailsLabel: (invoiceNumber: string) =>
            `Xem chi tiết hóa đơn ${invoiceNumber}`,
          billingRecords: 'Hồ sơ học phí',
          table: {
            invoice: 'Hóa đơn',
            semester: 'Học kỳ',
            total: 'Tổng cộng',
            paid: 'Đã thanh toán',
            balance: 'Còn lại',
            dueDate: 'Hạn thanh toán',
            status: 'Trạng thái',
            actions: 'Tác vụ',
          },
          modal: {
            detailLoading: 'Đang tải chi tiết hóa đơn',
            semester: 'Học kỳ',
            due: 'Hạn thanh toán',
            created: 'Ngày phát hành',
            balance: 'Số dư còn lại',
            paidAt: 'Đã ghi nhận thanh toán',
            items: 'Khoản mục học phí',
            payments: 'Lịch sử thanh toán',
            noPayments:
              'Chưa có giao dịch hoàn tất nào cho hóa đơn này.',
            itemQuantity: 'x',
          },
          checkout: {
            title: 'Phương thức thanh toán',
            description:
              'Chọn cổng thanh toán phù hợp để tiếp tục xử lý hóa đơn này.',
            sandboxNotice:
              'Bạn sẽ hoàn tất bước xác nhận tại cổng thanh toán rồi quay lại CampusCore để xem trạng thái mới nhất.',
            chooseProvider: 'Chọn phương thức',
            readyToPay: 'Sẵn sàng thanh toán',
            settled: 'Hóa đơn này đã được thanh toán đủ.',
            pendingProvider: 'Đang chờ cổng thanh toán phản hồi',
            processing: 'Đang mở phiên',
            started: (provider: string) =>
              `Đã mở phiên thanh toán cho ${provider}.`,
            startFailed:
              'Không thể khởi tạo phiên thanh toán cho hóa đơn này.',
            activeTitle: 'Phiên thanh toán đang hoạt động',
            provider: 'Cổng thanh toán',
            checkoutStatus: 'Trạng thái checkout',
            outstandingAmount: 'Giá trị đang xử lý',
            expiresAt: 'Hết hạn lúc',
            reference: 'Mã tham chiếu',
            timeline: 'Dòng thời gian thanh toán',
            noTimeline:
              'Dòng thời gian sẽ xuất hiện khi phiên thanh toán được tạo.',
            simulateTitle: 'Theo dõi tiến trình thanh toán',
            simulateDescription:
              'Các bước dưới đây mô phỏng phản hồi từ cổng thanh toán để bạn kiểm tra luồng xử lý mà không dùng tiền thật.',
            simulateProcessing: 'Đánh dấu đang xử lý',
            simulateSuccess: 'Xác nhận đã thanh toán',
            simulateFailure: 'Giả lập thất bại',
            simulateCancel: 'Hủy phiên thanh toán',
            actionSuccess: 'Đã cập nhật trạng thái thanh toán.',
            actionFailed:
              'Không thể tiếp tục phiên thanh toán này.',
          },
          actions: {
            openCourses: 'Mở môn học của tôi',
            openRegistration: 'Mở đăng ký học phần',
            retry: 'Thử lại',
          },
        }
      : {
          eyebrow: 'Student workspace',
          title: 'Tuition and payments',
          description:
            'Track tuition balances, open invoice detail, and choose a payment method in one clear flow.',
          selectSemester: 'Select semester for invoices',
          allSemesters: 'All semesters',
          loading: 'Loading billing data',
          unavailableTitle: 'Billing is unavailable',
          unavailableDescription:
            'Invoice data could not be loaded right now. Please try again shortly.',
          emptyTitle: 'No invoices yet',
          emptyDescription:
            'When tuition or service fees are issued to your account, they will appear here.',
          metrics: {
            outstanding: 'Outstanding balance',
            invoices: 'Invoices in view',
            dueSoon: 'Needs attention soon',
          },
          details: 'View details',
          detailTitle: 'Invoice details',
          closeDetail: 'Close invoice details',
          viewDetailsLabel: (invoiceNumber: string) =>
            `View details for invoice ${invoiceNumber}`,
          billingRecords: 'Billing records',
          table: {
            invoice: 'Invoice',
            semester: 'Semester',
            total: 'Total',
            paid: 'Paid',
            balance: 'Balance',
            dueDate: 'Due date',
            status: 'Status',
            actions: 'Actions',
          },
          modal: {
            detailLoading: 'Loading invoice details',
            semester: 'Semester',
            due: 'Due date',
            created: 'Issued on',
            balance: 'Remaining balance',
            paidAt: 'Recorded payment',
            items: 'Invoice items',
            payments: 'Payment history',
            noPayments:
              'No completed payment record is attached to this invoice yet.',
            itemQuantity: 'x',
          },
          checkout: {
            title: 'Payment methods',
            description:
              'Choose the provider that best fits this invoice.',
            sandboxNotice:
              'You will complete the confirmation step with the provider and then return here to review the latest status.',
            chooseProvider: 'Choose a provider',
            readyToPay: 'Ready to pay',
            settled: 'This invoice is already fully settled.',
            pendingProvider: 'Waiting for provider confirmation',
            processing: 'Opening checkout',
            started: (provider: string) =>
              `Started a payment session for ${provider}.`,
            startFailed: 'This payment session could not be started.',
            activeTitle: 'Active payment session',
            provider: 'Provider',
            checkoutStatus: 'Checkout status',
            outstandingAmount: 'Amount in flow',
            expiresAt: 'Expires at',
            reference: 'Reference',
            timeline: 'Payment timeline',
            noTimeline:
              'Timeline events will appear after a checkout session is created.',
            simulateTitle: 'Track payment progress',
            simulateDescription:
              'These demo updates simulate provider callbacks so you can review the payment flow without using real money.',
            simulateProcessing: 'Mark processing',
            simulateSuccess: 'Confirm paid',
            simulateFailure: 'Simulate failure',
            simulateCancel: 'Cancel session',
            actionSuccess: 'Payment status updated.',
            actionFailed:
              'This payment session could not continue.',
          },
          actions: {
            openCourses: 'Open my courses',
            openRegistration: 'Open registration',
            retry: 'Retry',
          },
        };

  const providerDefinitions = useMemo(
    () => getProviderDefinitions(locale),
    [locale],
  );
  const invoiceQueryId = searchParams.get('invoice');
  const intentQueryId = searchParams.get('intent');
  const paymentStatusQuery = searchParams.get('paymentStatus');
  const paymentProviderQuery = searchParams.get('provider');

  const fetchSemesters = useCallback(async () => {
    const response = await semestersApi.getAll();
    setSemesters(response.data ?? []);
  }, []);

  const fetchInvoices = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const data = await financeApi.getMyInvoices(selectedSemester || undefined);
      setInvoices(data);
    } catch (nextError) {
      setError(toFriendlyError(nextError, copy.unavailableDescription));
    } finally {
      setIsLoading(false);
    }
  }, [copy.unavailableDescription, selectedSemester]);

  const syncInvoiceInList = useCallback((detail: FinanceStudentInvoiceDetail) => {
    setInvoices((current) => {
      const next = summarizeInvoice(detail);
      const found = current.some((invoice) => invoice.id === detail.id);

      if (!found) {
        return [next, ...current];
      }

      return current.map((invoice) =>
        invoice.id === detail.id ? next : invoice,
      );
    });
  }, []);

  const loadInvoiceDetail = useCallback(
    async (invoiceId: string) => {
      setIsDetailLoading(true);
      setDetailError('');

      try {
        const detail = await financeApi.getMyInvoiceById(invoiceId);
        setSelectedInvoice(detail);
        syncInvoiceInList(detail);
        return detail;
      } catch (nextError) {
        const message = toFriendlyError(
          nextError,
          copy.unavailableDescription,
        );
        setDetailError(message);
        return null;
      } finally {
        setIsDetailLoading(false);
      }
    },
    [copy.unavailableDescription, syncInvoiceInList],
  );

  useEffect(() => {
    if (!hasAccess) {
      return;
    }

    void fetchSemesters();
    void fetchInvoices();
  }, [fetchInvoices, fetchSemesters, hasAccess]);

  const openInvoiceDetail = useCallback(
    async (
      invoice: FinanceStudentInvoice,
      options?: { checkoutIntentId?: string | null },
    ) => {
      setSelectedInvoiceId(invoice.id);
      setSelectedInvoice(null);
      setActiveCheckout(null);
      setDetailOpen(true);
      await loadInvoiceDetail(invoice.id);

      if (options?.checkoutIntentId) {
        try {
          const intent = await financeApi.getMyPaymentIntent(
            options.checkoutIntentId,
          );
          setActiveCheckout(intent);
        } catch {
          // Let the invoice detail stay open even if the checkout lookup failed.
        }
      }
    },
    [loadInvoiceDetail],
  );

  useEffect(() => {
    if (!invoiceQueryId || isLoading || invoices.length === 0) {
      return;
    }

    const returnKey = [
      invoiceQueryId,
      intentQueryId ?? '',
      paymentStatusQuery ?? '',
      paymentProviderQuery ?? '',
    ].join(':');

    if (handledReturnKey === returnKey) {
      return;
    }

    const invoice = invoices.find((item) => item.id === invoiceQueryId);
    if (!invoice) {
      return;
    }

    setHandledReturnKey(returnKey);

    void (async () => {
      await openInvoiceDetail(invoice, {
        checkoutIntentId: intentQueryId,
      });

      const nextMessage = getCheckoutReturnMessage(
        locale,
        paymentStatusQuery,
        paymentProviderQuery,
      );
      if (nextMessage) {
        if (paymentStatusQuery === 'success') {
          toast.success(nextMessage);
        } else if (paymentStatusQuery === 'processing') {
          toast.message(nextMessage);
        } else {
          toast.error(nextMessage);
        }
      }

      const nextParams = new URLSearchParams(searchParams.toString());
      nextParams.delete('invoice');
      nextParams.delete('intent');
      nextParams.delete('provider');
      nextParams.delete('paymentStatus');
      const nextQuery = nextParams.toString();

      router.replace(nextQuery ? `${pathname}?${nextQuery}` : pathname, {
        scroll: false,
      });
    })();
  }, [
    handledReturnKey,
    intentQueryId,
    invoiceQueryId,
    invoices,
    isLoading,
    locale,
    openInvoiceDetail,
    pathname,
    paymentProviderQuery,
    paymentStatusQuery,
    router,
    searchParams,
  ]);

  const refreshCurrentInvoice = useCallback(async () => {
    if (!selectedInvoiceId) {
      return null;
    }

    return loadInvoiceDetail(selectedInvoiceId);
  }, [loadInvoiceDetail, selectedInvoiceId]);

  const handleStartCheckout = async (provider: StudentCheckoutProvider) => {
    if (!selectedInvoice) {
      return;
    }

    setIsCheckoutLoading(provider);

    try {
      const currentUrl =
        typeof window !== 'undefined'
          ? new URL(window.location.href)
          : undefined;
      currentUrl?.searchParams.set('invoice', selectedInvoice.id);
      const session = await financeApi.startMyCheckout({
        invoiceId: selectedInvoice.id,
        provider,
        amount: selectedInvoice.balance,
        returnUrl: currentUrl?.toString(),
        cancelUrl: currentUrl?.toString(),
        locale,
      });

      setActiveCheckout(session);
      await refreshCurrentInvoice();

      const providerLabel =
        providerDefinitions.find((item) => item.provider === provider)?.label ??
        provider;
      toast.success(copy.checkout.started(providerLabel));
    } catch (nextError) {
      toast.error(toFriendlyError(nextError, copy.checkout.startFailed));
    } finally {
      setIsCheckoutLoading(null);
    }
  };

  const handleContinueCheckout = () => {
    if (!isCheckoutIntent(activeCheckout)) {
      return;
    }

    const target = getCheckoutActionHref(activeCheckout.nextAction);
    if (!target || typeof window === 'undefined') {
      toast.error(copy.checkout.actionFailed);
      return;
    }

    window.location.assign(target);
  };

  const selectedSemesterName = useMemo(() => {
    const semester = semesters.find((item) => item.id === selectedSemester);
    return semester
      ? getLocalizedName(locale, semester, semester.name)
      : copy.allSemesters;
  }, [copy.allSemesters, locale, selectedSemester, semesters]);

  const semestersById = useMemo(
    () => new Map(semesters.map((semester) => [semester.id, semester] as const)),
    [semesters],
  );

  const totalOutstanding = useMemo(
    () =>
      invoices
        .filter((invoice) => invoice.status !== 'PAID')
        .reduce((sum, invoice) => sum + invoice.balance, 0),
    [invoices],
  );

  const attentionCount = useMemo(
    () =>
      invoices.filter((invoice) =>
        ['OVERDUE', 'PARTIALLY_PAID', 'PENDING'].includes(invoice.status),
      ).length,
    [invoices],
  );

  if (authLoading || !hasAccess) {
    return <LoadingState label={copy.loading} />;
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={`${copy.description} ${selectedSemesterName}.`}
        actions={
          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="min-w-[220px]">
              <Select
                aria-label={copy.selectSemester}
                value={selectedSemester}
                onChange={(event) => setSelectedSemester(event.target.value)}
                options={[
                   { value: '', label: copy.allSemesters },
                   ...semesters.map((semester) => ({
                     value: semester.id,
                     label: getLocalizedName(locale, semester, semester.name),
                   })),
                 ]}
              />
            </div>
            <LocalizedLink href="/dashboard/register">
              <Button type="button" variant="outline">
                {copy.actions.openRegistration}
              </Button>
            </LocalizedLink>
          </div>
        }
      />

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
          action={
            <LocalizedLink href="/dashboard/enrollments">
              <Button type="button" variant="outline">
                {copy.actions.openCourses}
              </Button>
            </LocalizedLink>
          }
        />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            <WorkspaceMetricCard
              label={copy.metrics.outstanding}
              value={formatMoney(totalOutstanding)}
              icon={<Wallet className="h-5 w-5" />}
              detail={
                locale === 'vi'
                  ? 'Tổng các khoản còn lại trong phạm vi hóa đơn đang xem.'
                  : 'Total remaining balance across the invoices in view.'
              }
              toneClassName="bg-amber-500/12 text-amber-700 dark:text-amber-300"
            />
            <WorkspaceMetricCard
              label={copy.metrics.invoices}
              value={formatNumber(invoices.length)}
              icon={<Receipt className="h-5 w-5" />}
              detail={
                locale === 'vi'
                  ? 'Bao gồm các hóa đơn đã thanh toán, đang chờ và quá hạn.'
                  : 'Includes paid, pending, and overdue billing records.'
              }
              toneClassName="bg-blue-500/12 text-blue-700 dark:text-blue-300"
            />
            <WorkspaceMetricCard
              label={copy.metrics.dueSoon}
              value={formatNumber(attentionCount)}
              icon={<ShieldCheck className="h-5 w-5" />}
              detail={
                locale === 'vi'
                  ? 'Những hóa đơn đang cần thao tác hoặc theo dõi thêm.'
                  : 'Invoices that still require action or closer follow-up.'
              }
              toneClassName="bg-emerald-500/12 text-emerald-700 dark:text-emerald-300"
            />
          </div>

          <WorkspacePanel
            title={copy.billingRecords}
            description={
              locale === 'vi'
                ? 'Mở chi tiết để xem khoản mục, lịch sử thanh toán và các lựa chọn thanh toán hiện có.'
                : 'Open any invoice to review line items, payment history, and the available payment options.'
            }
            contentClassName="space-y-4"
          >
            <div
              className="space-y-3 md:hidden"
              role="list"
              aria-label={copy.billingRecords}
            >
              {invoices.map((invoice) => (
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
                      <p className="mt-1 break-words text-sm text-muted-foreground">
                        {getLocalizedName(
                          locale,
                          semestersById.get(invoice.semesterId),
                          getLocalizedFlatLabel(
                            locale,
                            invoice.semesterName,
                            invoice.semesterNameEn,
                            invoice.semesterNameVi,
                            invoice.semesterName,
                          ),
                        )}
                      </p>
                    </div>
                    <span
                      className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-medium ${
                        invoiceStatusTone[invoice.status] ??
                        'bg-secondary text-foreground'
                      }`}
                    >
                      {getInvoiceStatusLabel(locale, invoice.status)}
                    </span>
                  </div>
                  <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-border/60 pt-3 text-sm">
                    <div>
                      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                        {copy.table.total}
                      </dt>
                      <dd className="mt-1 text-foreground">{formatMoney(invoice.total)}</dd>
                    </div>
                    <div>
                      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                        {copy.table.paid}
                      </dt>
                      <dd className="mt-1 text-foreground">{formatMoney(invoice.paidAmount)}</dd>
                    </div>
                    <div>
                      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                        {copy.table.balance}
                      </dt>
                      <dd className="mt-1 font-medium text-foreground">
                        {formatMoney(invoice.balance)}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                        {copy.table.dueDate}
                      </dt>
                      <dd className="mt-1 text-foreground">{formatDate(invoice.dueDate)}</dd>
                    </div>
                  </dl>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="mt-4 w-full"
                    onClick={() => void openInvoiceDetail(invoice)}
                    aria-label={copy.viewDetailsLabel(invoice.invoiceNumber)}
                    title={copy.viewDetailsLabel(invoice.invoiceNumber)}
                  >
                    <Eye className="mr-2 h-4 w-4" />
                    {copy.details}
                  </Button>
                </article>
              ))}
            </div>
            <div className="hidden overflow-x-auto md:block">
              <table className="w-full min-w-[920px] text-sm">
                <thead>
                  <tr className="border-b border-border/70 text-left text-muted-foreground">
                    <th className="px-2 py-3 font-medium">{copy.table.invoice}</th>
                    <th className="px-2 py-3 font-medium">{copy.table.semester}</th>
                    <th className="px-2 py-3 text-right font-medium">{copy.table.total}</th>
                    <th className="px-2 py-3 text-right font-medium">{copy.table.paid}</th>
                    <th className="px-2 py-3 text-right font-medium">{copy.table.balance}</th>
                    <th className="px-2 py-3 font-medium">{copy.table.dueDate}</th>
                    <th className="px-2 py-3 text-center font-medium">{copy.table.status}</th>
                    <th className="px-2 py-3 text-right font-medium">{copy.table.actions}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {invoices.map((invoice) => (
                    <tr key={invoice.id}>
                      <td className="px-2 py-4 font-medium text-foreground">
                        {invoice.invoiceNumber}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {getLocalizedName(
                            locale,
                            semestersById.get(invoice.semesterId),
                            getLocalizedFlatLabel(
                              locale,
                              invoice.semesterName,
                              invoice.semesterNameEn,
                              invoice.semesterNameVi,
                              invoice.semesterName,
                            ),
                          )}
                        </td>
                      <td className="px-2 py-4 text-right text-foreground">
                        {formatMoney(invoice.total)}
                      </td>
                      <td className="px-2 py-4 text-right text-foreground">
                        {formatMoney(invoice.paidAmount)}
                      </td>
                      <td className="px-2 py-4 text-right font-medium text-foreground">
                        {formatMoney(invoice.balance)}
                      </td>
                      <td className="px-2 py-4 text-muted-foreground">
                        {formatDate(invoice.dueDate)}
                      </td>
                      <td className="px-2 py-4 text-center">
                        <span
                          className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                            invoiceStatusTone[invoice.status] ??
                            'bg-secondary text-foreground'
                          }`}
                        >
                          {getInvoiceStatusLabel(locale, invoice.status)}
                        </span>
                      </td>
                      <td className="px-2 py-4 text-right">
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={() => void openInvoiceDetail(invoice)}
                          aria-label={copy.viewDetailsLabel(invoice.invoiceNumber)}
                          title={copy.viewDetailsLabel(invoice.invoiceNumber)}
                        >
                          <Eye className="mr-2 h-4 w-4" />
                          {copy.details}
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </WorkspacePanel>
        </>
      )}

      <Modal
        isOpen={detailOpen}
        onClose={() => {
          setDetailOpen(false);
          setSelectedInvoice(null);
          setSelectedInvoiceId(null);
          setActiveCheckout(null);
          setDetailError('');
        }}
        title={copy.detailTitle}
        closeLabel={copy.closeDetail}
        className="max-w-5xl"
      >
        {detailError ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={detailError}
            onRetry={() => void refreshCurrentInvoice()}
          />
        ) : isDetailLoading && !selectedInvoice ? (
          <LoadingState
            label={copy.modal.detailLoading}
            className="min-h-[220px] border-none bg-transparent px-0 py-0"
          />
        ) : selectedInvoice ? (
          <div className="space-y-6">
            <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
              <div className="space-y-4 rounded-lg border border-border/70 bg-secondary/30 px-5 py-5">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <div className="text-lg font-semibold text-foreground">
                      {selectedInvoice.invoiceNumber}
                    </div>
                      <p className="mt-1 text-sm leading-6 text-muted-foreground">
                      {copy.modal.semester}:{' '}
                      {getLocalizedName(
                        locale,
                        semestersById.get(selectedInvoice.semesterId),
                        getLocalizedFlatLabel(
                          locale,
                          selectedInvoice.semesterName,
                          selectedInvoice.semesterNameEn,
                          selectedInvoice.semesterNameVi,
                          selectedInvoice.semesterName,
                        ),
                      )}
                      </p>
                  </div>
                  <span
                    className={`rounded-full px-3 py-1 text-xs font-medium ${
                      invoiceStatusTone[selectedInvoice.status] ??
                      'bg-secondary text-foreground'
                    }`}
                  >
                    {getInvoiceStatusLabel(locale, selectedInvoice.status)}
                  </span>
                </div>

                <div className="grid gap-3 md:grid-cols-3">
                  <div className="rounded-lg border border-border/70 bg-card px-4 py-4">
                    <div className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                      {copy.table.total}
                    </div>
                    <div className="mt-2 text-2xl font-semibold tracking-tight text-foreground">
                      {formatMoney(selectedInvoice.total)}
                    </div>
                  </div>
                  <div className="rounded-lg border border-border/70 bg-card px-4 py-4">
                    <div className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                      {copy.table.paid}
                    </div>
                    <div className="mt-2 text-2xl font-semibold tracking-tight text-foreground">
                      {formatMoney(selectedInvoice.paidAmount)}
                    </div>
                  </div>
                  <div className="rounded-lg border border-border/70 bg-card px-4 py-4">
                    <div className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                      {copy.modal.balance}
                    </div>
                    <div className="mt-2 text-2xl font-semibold tracking-tight text-foreground">
                      {formatMoney(selectedInvoice.balance)}
                    </div>
                  </div>
                </div>

                <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm text-muted-foreground">
                  <span>
                    {copy.modal.due}: {formatDate(selectedInvoice.dueDate)}
                  </span>
                  <span>
                    {copy.modal.created}: {formatDate(selectedInvoice.createdAt)}
                  </span>
                  {selectedInvoice.paidAt ? (
                    <span>
                      {copy.modal.paidAt}: {formatDate(selectedInvoice.paidAt)}
                    </span>
                  ) : null}
                </div>
              </div>

              <div className="rounded-lg border border-border/70 bg-card px-5 py-5">
                <div className="space-y-2">
                  <div className="flex items-center gap-2 text-sm font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    <ShieldCheck className="h-4 w-4" />
                    {copy.checkout.title}
                  </div>
                  <h3 className="text-lg font-semibold text-foreground">
                    {selectedInvoice.balance > 0
                      ? copy.checkout.readyToPay
                      : copy.checkout.settled}
                  </h3>
                  <p className="text-sm leading-6 text-muted-foreground">
                    {selectedInvoice.balance > 0
                      ? copy.checkout.description
                      : copy.checkout.settled}
                  </p>
                </div>
                {selectedInvoice.balance > 0 ? (
                  <div className="mt-4 rounded-lg border border-border/70 bg-secondary/35 px-4 py-4 text-sm leading-6 text-muted-foreground">
                    {copy.checkout.sandboxNotice}
                  </div>
                ) : null}
              </div>
            </div>

            <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
              <div className="space-y-6">
                <section className="space-y-3">
                  <div className="flex items-center justify-between gap-3">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                      {copy.modal.items}
                    </h3>
                    <LocalizedLink href="/dashboard/register">
                      <Button type="button" variant="ghost" size="sm">
                        {copy.actions.openRegistration}
                        <ArrowUpRight className="ml-2 h-4 w-4" />
                      </Button>
                    </LocalizedLink>
                  </div>
                  <div className="space-y-3">
                    {selectedInvoice.items.map((item) => (
                      <div
                        key={item.id}
                        className="rounded-lg border border-border/70 bg-card px-4 py-4"
                      >
                        <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                          <div className="min-w-0">
                            <div className="font-medium text-foreground">
                              {getLocalizedInvoiceItemDescription(
                                locale,
                                item.description,
                              )}
                            </div>
                            <div className="mt-1 text-sm text-muted-foreground">
                              {formatNumber(item.quantity)} {copy.modal.itemQuantity}{' '}
                              {formatMoney(item.unitPrice)}
                            </div>
                          </div>
                          <div className="font-medium text-foreground">
                            {formatMoney(item.total)}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </section>

                <section className="space-y-3">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    {copy.modal.payments}
                  </h3>
                  {selectedInvoice.payments.length > 0 ? (
                    <div className="space-y-3">
                      {selectedInvoice.payments.map((payment) => (
                        <div
                          key={payment.id}
                          className="rounded-lg border border-border/70 bg-card px-4 py-4"
                        >
                          <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                            <div className="min-w-0">
                              <div className="font-medium text-foreground">
                                {payment.paymentNumber}
                              </div>
                              <div className="mt-1 text-sm leading-6 text-muted-foreground">
                                {getPaymentMethodLabel(locale, payment.method)} -{' '}
                                {formatDateTime(payment.paidAt || payment.createdAt)}
                              </div>
                            </div>
                            <div className="text-left sm:text-right">
                              <div className="font-medium text-foreground">
                                {formatMoney(payment.amount)}
                              </div>
                              <div className="mt-1 text-sm text-muted-foreground">
                                {getCheckoutStatusLabel(locale, payment.status)}
                              </div>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="rounded-lg border border-border/70 bg-secondary/20 px-4 py-4 text-sm leading-6 text-muted-foreground">
                      {copy.modal.noPayments}
                    </div>
                  )}
                </section>
              </div>

              <div className="space-y-6">
                {selectedInvoice.balance > 0 ? (
                  <section className="space-y-3">
                    <h3 className="text-sm font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                      {copy.checkout.chooseProvider}
                    </h3>
                    <div className="space-y-3">
                      {providerDefinitions.map((provider) => {
                        const Icon = provider.icon;
                        const providerActive =
                          isCheckoutIntent(activeCheckout) &&
                          activeCheckout.provider === provider.provider;

                        return (
                          <div
                            key={provider.provider}
                            className="rounded-lg border border-border/70 bg-card px-4 py-4"
                          >
                            <div className="flex flex-col gap-4">
                              <div className="flex items-start gap-3">
                                <div
                                  className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-lg ${provider.toneClassName}`}
                                >
                                  <Icon className="h-5 w-5" />
                                </div>
                                <div className="min-w-0">
                                  <div className="font-medium text-foreground">
                                    {provider.label}
                                  </div>
                                  <p className="mt-1 text-sm leading-6 text-muted-foreground">
                                    {provider.description}
                                  </p>
                                </div>
                              </div>
                              <Button
                                type="button"
                                variant={providerActive ? 'secondary' : 'outline'}
                                onClick={() => void handleStartCheckout(provider.provider)}
                                disabled={Boolean(isCheckoutLoading)}
                              >
                                {isCheckoutLoading === provider.provider ? (
                                  <>
                                    <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
                                    {copy.checkout.processing}
                                  </>
                                ) : providerActive ? (
                                  copy.checkout.pendingProvider
                                ) : (
                                  provider.label
                                )}
                              </Button>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </section>
                ) : null}

                <section className="space-y-3 rounded-lg border border-border/70 bg-card px-4 py-4">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    {copy.checkout.activeTitle}
                  </h3>
                  {isCheckoutIntent(activeCheckout) ? (
                    <div className="space-y-4">
                      <div className="flex flex-wrap items-center gap-2">
                        <span
                          className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                            checkoutStatusTone[activeCheckout.status] ??
                            'bg-secondary text-foreground'
                          }`}
                        >
                          {getCheckoutStatusLabel(locale, activeCheckout.status)}
                        </span>
                        <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                          {providerDefinitions.find(
                            (provider) => provider.provider === activeCheckout.provider,
                          )?.label ?? activeCheckout.provider}
                        </span>
                      </div>

                      <div className="space-y-2 text-sm text-muted-foreground">
                        <div className="flex items-start justify-between gap-4">
                          <span>{copy.checkout.provider}</span>
                          <span className="font-medium text-foreground">
                            {providerDefinitions.find(
                              (provider) => provider.provider === activeCheckout.provider,
                            )?.label ?? activeCheckout.provider}
                          </span>
                        </div>
                        <div className="flex items-start justify-between gap-4">
                          <span>{copy.checkout.checkoutStatus}</span>
                          <span className="font-medium text-foreground">
                            {getCheckoutStatusLabel(locale, activeCheckout.status)}
                          </span>
                        </div>
                        <div className="flex items-start justify-between gap-4">
                          <span>{copy.checkout.outstandingAmount}</span>
                          <span className="font-medium text-foreground">
                            {formatMoney(activeCheckout.outstandingAmount)}
                          </span>
                        </div>
                        {activeCheckout.expiresAt ? (
                          <div className="flex items-start justify-between gap-4">
                            <span>{copy.checkout.expiresAt}</span>
                            <span className="font-medium text-foreground">
                              {formatDateTime(activeCheckout.expiresAt)}
                            </span>
                          </div>
                        ) : null}
                        {activeCheckout.latestAttempt?.providerReference ? (
                          <div className="flex items-start justify-between gap-4">
                            <span>{copy.checkout.reference}</span>
                            <span className="font-medium text-foreground">
                              {activeCheckout.latestAttempt.providerReference}
                            </span>
                          </div>
                        ) : null}
                      </div>

                      {!['SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED'].includes(
                        activeCheckout.status,
                      ) ? (
                        <div className="space-y-3 rounded-lg border border-border/70 bg-secondary/20 px-4 py-4">
                          <div className="space-y-1">
                            <div className="font-medium text-foreground">
                              {getCheckoutActionLabel(
                                locale,
                                activeCheckout.nextAction?.flow,
                              )}
                            </div>
                            <p className="text-sm leading-6 text-muted-foreground">
                              {getCheckoutActionDescription(
                                locale,
                                activeCheckout.nextAction?.flow,
                              )}
                            </p>
                          </div>
                          {activeCheckout.nextAction?.qrPayload ? (
                            <div className="rounded-lg border border-border/70 bg-background/50 px-4 py-3">
                              <div className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">
                                {locale === 'vi'
                                  ? 'Mã xác nhận QR'
                                  : 'QR confirmation code'}
                              </div>
                              <p className="mt-2 text-sm leading-6 text-foreground">
                                {locale === 'vi'
                                  ? 'Đã tạo mã xác nhận cho bước thanh toán với nhà cung cấp.'
                                  : 'A provider confirmation code is ready for the payment step.'}
                              </p>
                            </div>
                          ) : null}
                          {activeCheckout.nextAction?.deeplinkUrl ? (
                            <div className="rounded-lg border border-border/70 bg-background/50 px-4 py-3">
                              <div className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">
                                {locale === 'vi'
                                  ? 'Liên kết mở ứng dụng'
                                  : 'App payment link'}
                              </div>
                              <p className="mt-2 text-sm leading-6 text-foreground">
                                {locale === 'vi'
                                  ? 'Liên kết ứng dụng đã sẵn sàng và sẽ được dùng khi bạn tiếp tục.'
                                  : 'The app payment link is ready and will be used when you continue.'}
                              </p>
                            </div>
                          ) : null}
                          <div className="grid gap-2 sm:grid-cols-2">
                            <Button
                              type="button"
                              variant="secondary"
                              onClick={handleContinueCheckout}
                            >
                              {getCheckoutActionLabel(
                                locale,
                                activeCheckout.nextAction?.flow,
                              )}
                              <ArrowUpRight className="ml-2 h-4 w-4" />
                            </Button>
                            <Button
                              type="button"
                              variant="outline"
                              onClick={() => void refreshCurrentInvoice()}
                            >
                              <RefreshCw className="mr-2 h-4 w-4" />
                              {copy.actions.retry}
                            </Button>
                          </div>
                        </div>
                      ) : null}
                    </div>
                  ) : (
                    <div className="rounded-lg border border-dashed border-border/80 bg-secondary/20 px-4 py-5 text-sm leading-6 text-muted-foreground">
                      {copy.checkout.noTimeline}
                    </div>
                  )}
                </section>

                <section className="space-y-3 rounded-lg border border-border/70 bg-card px-4 py-4">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    {copy.checkout.timeline}
                  </h3>
                  {isCheckoutIntent(activeCheckout) &&
                  activeCheckout.timeline.length > 0 ? (
                    <div className="space-y-3">
                      {[...activeCheckout.timeline]
                        .reverse()
                        .map((event) => (
                          <div
                            key={event.id}
                            className="rounded-lg border border-border/70 bg-secondary/20 px-4 py-4"
                          >
                            <div className="flex items-start gap-3">
                              <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                                {event.type === 'VERIFICATION_FAILED' ? (
                                  <XCircle className="h-4 w-4" />
                                ) : (
                                  <ShieldCheck className="h-4 w-4" />
                                )}
                              </div>
                              <div className="min-w-0">
                                <div className="font-medium text-foreground">
                                  {getTimelineEventLabel(locale, event.type)}
                                </div>
                                <div className="mt-1 text-sm leading-6 text-muted-foreground">
                                  {formatDateTime(event.createdAt)}
                                </div>
                                {(event.toIntentStatus || event.toAttemptStatus) ? (
                                  <div className="mt-2 flex flex-wrap gap-2">
                                    {event.toIntentStatus ? (
                                      <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                                        {getCheckoutStatusLabel(
                                          locale,
                                          event.toIntentStatus,
                                        )}
                                      </span>
                                    ) : null}
                                    {event.toAttemptStatus ? (
                                      <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                                        {getCheckoutStatusLabel(
                                          locale,
                                          event.toAttemptStatus,
                                        )}
                                      </span>
                                    ) : null}
                                  </div>
                                ) : null}
                              </div>
                            </div>
                          </div>
                        ))}
                    </div>
                  ) : (
                    <div className="rounded-lg border border-dashed border-border/80 bg-secondary/20 px-4 py-5 text-sm leading-6 text-muted-foreground">
                      {copy.checkout.noTimeline}
                    </div>
                  )}
                </section>
              </div>
            </div>
          </div>
        ) : (
          <LoadingState
            label={copy.modal.detailLoading}
            className="min-h-[220px] border-none bg-transparent px-0 py-0"
          />
        )}
      </Modal>
    </div>
  );
}
