package io.campuscore.restfulapi.finance.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Legacy-compatible DTOs for the feature-gated finance read candidate. */
public final class FinanceReadDtos {

    private FinanceReadDtos() {
    }

    public record PageMeta(long total, int page, int limit, int totalPages) {
    }

    public record StudentSnapshot(
            UserSnapshot user,
            String studentId) {
    }

    public record UserSnapshot(
            String firstName,
            String lastName,
            String email) {
    }

    public record SemesterSnapshot(
            String name,
            String nameEn,
            String nameVi) {
    }

    public record InvoiceListItem(
            String id,
            String invoiceNumber,
            String studentId,
            String studentUserId,
            String studentDisplayName,
            String studentEmail,
            String studentCode,
            String semesterId,
            String semesterName,
            String semesterNameEn,
            String semesterNameVi,
            String status,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal total,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant dueDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant paidAt,
            String notes,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            BigDecimal paidAmount,
            BigDecimal balance,
            StudentSnapshot student,
            SemesterSnapshot semester) {
    }

    public record InvoiceItemResponse(
            String id,
            String invoiceId,
            String description,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal total) {
    }

    public record PaymentResponse(
            String id,
            String paymentNumber,
            String invoiceId,
            String studentId,
            BigDecimal amount,
            String method,
            String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant paidAt,
            String transactionId,
            String paymentIntentId,
            String notes,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            InvoiceSummary invoice) {
    }

    public record InvoiceSummary(
            String id,
            String invoiceNumber,
            String studentId,
            String studentDisplayName,
            String studentEmail,
            String studentCode,
            String semesterId,
            String semesterName,
            String status,
            BigDecimal total) {
    }

    public record InvoiceDetail(
            String id,
            String invoiceNumber,
            String studentId,
            String studentUserId,
            String studentDisplayName,
            String studentEmail,
            String studentCode,
            String semesterId,
            String semesterName,
            String semesterNameEn,
            String semesterNameVi,
            String status,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal total,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant dueDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant paidAt,
            String notes,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            BigDecimal paidAmount,
            BigDecimal balance,
            StudentSnapshot student,
            SemesterSnapshot semester,
            List<InvoiceItemResponse> items,
            List<PaymentResponse> payments) {
    }

    public record InvoiceListResponse(List<InvoiceListItem> data, PageMeta meta) {
    }

    public record PaymentListResponse(List<PaymentResponse> data, PageMeta meta) {
    }
}
