package io.campuscore.restfulapi.finance.service;

import io.campuscore.restfulapi.finance.repository.FinanceReadRepository;
import io.campuscore.restfulapi.finance.repository.FinanceReadRepository.InvoiceRecord;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.InvoiceDetail;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.InvoiceListItem;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.InvoiceListResponse;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.PageMeta;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.PaymentCore;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.PaymentListResponse;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.PaymentResponse;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.SemesterSnapshot;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.StudentInvoiceListItem;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.StudentSnapshot;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.UserSnapshot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only application service for finance invoices and payments. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.finance-read", name = "enabled", havingValue = "true")
public class FinanceReadService {

    public static final int MAX_PAGE_SIZE = 100;

    private final FinanceReadRepository finance;
    private final Clock clock;

    @Autowired
    public FinanceReadService(FinanceReadRepository finance) {
        this(finance, Clock.systemUTC());
    }

    FinanceReadService(FinanceReadRepository finance, Clock clock) {
        this.finance = finance;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InvoiceListResponse findInvoices(
            int page,
            int limit,
            String status,
            String semesterId,
            String studentId) {
        requirePage(page, limit);
        String normalizedStatus = normalizeOptional("status", status);
        String normalizedSemesterId = normalizeOptional("semesterId", semesterId);
        String normalizedStudentId = normalizeOptional("studentId", studentId);
        long total = finance.countInvoices(normalizedStatus, normalizedSemesterId, normalizedStudentId);
        List<InvoiceListItem> data = finance.findInvoices(
                        offset(page, limit),
                        limit,
                        normalizedStatus,
                        normalizedSemesterId,
                        normalizedStudentId)
                .stream()
                .map(this::toInvoiceListItem)
                .toList();
        return new InvoiceListResponse(data, meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public InvoiceDetail findInvoice(String id) {
        InvoiceRecord invoice = finance.findInvoiceById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        return detail(invoice);
    }

    @Transactional(readOnly = true)
    public List<StudentInvoiceListItem> findStudentInvoices(String studentId, String semesterId) {
        String normalizedStudentId = requireStudentId(studentId);
        String normalizedSemesterId = normalizeOptional("semesterId", semesterId);
        return finance.findStudentInvoices(normalizedStudentId, normalizedSemesterId)
                .stream()
                .map(this::toStudentInvoiceListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceDetail findStudentInvoice(String studentId, String invoiceId) {
        InvoiceRecord invoice = finance.findStudentInvoiceById(requireStudentId(studentId), invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        return detail(invoice);
    }

    @Transactional(readOnly = true)
    public PaymentListResponse findPayments(
            int page,
            int limit,
            String status,
            String invoiceId,
            String studentId) {
        requirePage(page, limit);
        String normalizedStatus = normalizeOptional("status", status);
        String normalizedInvoiceId = normalizeOptional("invoiceId", invoiceId);
        String normalizedStudentId = normalizeOptional("studentId", studentId);
        long total = finance.countPayments(normalizedStatus, normalizedInvoiceId, normalizedStudentId);
        List<PaymentResponse> data = finance.findPayments(
                offset(page, limit),
                limit,
                normalizedStatus,
                normalizedInvoiceId,
                normalizedStudentId);
        return new PaymentListResponse(data, meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public PaymentResponse findPayment(String id) {
        return finance.findPaymentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    private InvoiceDetail detail(InvoiceRecord invoice) {
        InvoiceListItem base = toInvoiceListItem(invoice);
        return new InvoiceDetail(
                base.id(),
                base.invoiceNumber(),
                base.studentId(),
                base.studentUserId(),
                base.studentDisplayName(),
                base.studentEmail(),
                base.studentCode(),
                base.semesterId(),
                base.semesterName(),
                base.semesterNameEn(),
                base.semesterNameVi(),
                base.status(),
                base.subtotal(),
                base.discount(),
                base.total(),
                base.dueDate(),
                base.paidAt(),
                base.notes(),
                base.createdAt(),
                base.updatedAt(),
                base.paidAmount(),
                base.balance(),
                base.student(),
                base.semester(),
                finance.findInvoiceItems(invoice.id()),
                base.payments());
    }

    private InvoiceListItem toInvoiceListItem(InvoiceRecord invoice) {
        BigDecimal paidAmount = amount(invoice.paidAmount());
        BigDecimal total = amount(invoice.total());
        String status = displayStatus(invoice, paidAmount, total);
        return new InvoiceListItem(
                invoice.id(),
                invoice.invoiceNumber(),
                invoice.studentId(),
                invoice.studentUserId(),
                invoice.studentDisplayName(),
                invoice.studentEmail(),
                invoice.studentCode(),
                invoice.semesterId(),
                invoice.semesterName(),
                semesterNameEn(invoice),
                invoice.semesterNameVi(),
                status,
                amount(invoice.subtotal()),
                amount(invoice.discount()),
                total,
                invoice.dueDate(),
                displayPaidAt(invoice, status),
                invoice.notes(),
                invoice.createdAt(),
                invoice.updatedAt(),
                paymentCoreList(invoice.id()),
                paidAmount,
                total.subtract(paidAmount).max(BigDecimal.ZERO),
                student(invoice),
                semester(invoice));
    }

    private StudentInvoiceListItem toStudentInvoiceListItem(InvoiceRecord invoice) {
        BigDecimal paidAmount = amount(invoice.paidAmount());
        BigDecimal total = amount(invoice.total());
        String status = displayStatus(invoice, paidAmount, total);
        return new StudentInvoiceListItem(
                invoice.id(),
                invoice.invoiceNumber(),
                invoice.semesterName(),
                semesterNameEn(invoice),
                invoice.semesterNameVi(),
                invoice.semesterId(),
                status,
                amount(invoice.subtotal()),
                amount(invoice.discount()),
                total,
                invoice.dueDate(),
                displayPaidAt(invoice, status),
                invoice.createdAt(),
                paidAmount,
                total.subtract(paidAmount).max(BigDecimal.ZERO));
    }

    private List<PaymentCore> paymentCoreList(String invoiceId) {
        return finance.findInvoicePayments(invoiceId)
                .stream()
                .map(FinanceReadService::toPaymentCore)
                .toList();
    }

    private static PaymentCore toPaymentCore(PaymentResponse payment) {
        return new PaymentCore(
                payment.id(),
                payment.paymentNumber(),
                payment.invoiceId(),
                payment.studentId(),
                payment.amount(),
                payment.method(),
                payment.status(),
                payment.paidAt(),
                payment.transactionId(),
                payment.paymentIntentId(),
                payment.notes(),
                payment.createdAt(),
                payment.updatedAt());
    }

    private String displayStatus(InvoiceRecord invoice, BigDecimal paidAmount, BigDecimal total) {
        if ("CANCELLED".equals(invoice.status())) {
            return "CANCELLED";
        }
        if (paidAmount.compareTo(total) >= 0) {
            return "PAID";
        }
        if (paidAmount.signum() > 0) {
            return "PARTIALLY_PAID";
        }
        if ("DRAFT".equals(invoice.status())) {
            return "DRAFT";
        }
        if (invoice.dueDate() != null && invoice.dueDate().isBefore(Instant.now(clock))) {
            return "OVERDUE";
        }
        return "PENDING";
    }

    private Instant displayPaidAt(InvoiceRecord invoice, String status) {
        if (!"PAID".equals(status)) {
            return null;
        }
        return invoice.paidAt();
    }

    private static StudentSnapshot student(InvoiceRecord invoice) {
        String[] name = splitName(invoice.studentDisplayName());
        return new StudentSnapshot(
                new UserSnapshot(name[0], name[1], invoice.studentEmail()),
                invoice.studentCode());
    }

    private static SemesterSnapshot semester(InvoiceRecord invoice) {
        return new SemesterSnapshot(
                invoice.semesterName(),
                semesterNameEn(invoice),
                invoice.semesterNameVi());
    }

    private static String semesterNameEn(InvoiceRecord invoice) {
        return invoice.semesterNameEn() == null ? invoice.semesterName() : invoice.semesterNameEn();
    }

    private static String[] splitName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return new String[]{"", ""};
        }
        String trimmed = displayName.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new String[]{"", trimmed};
        }
        return new String[]{trimmed.substring(0, lastSpace), trimmed.substring(lastSpace + 1)};
    }

    private static String requireStudentId(String studentId) {
        String normalized = normalizeOptional("studentId", studentId);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Student profile not found");
        }
        return normalized;
    }

    private static String normalizeOptional(String name, String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }

    private static BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static PageMeta meta(long total, int page, int limit) {
        long totalPages = total == 0 ? 0 : ((total - 1) / limit) + 1;
        if (totalPages > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Finance result is too large");
        }
        return new PageMeta(total, page, limit, (int) totalPages);
    }

    private static long offset(int page, int limit) {
        return (long) (page - 1) * limit;
    }

    private static void requirePage(int page, int limit) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
