package io.campuscore.finance.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record InvoiceItemResponse(
            UUID id,
            UUID invoiceId,
            String description,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal total) {
    }

    public record InvoiceResponse(
            UUID id,
            String invoiceNumber,
            UUID studentId,
            UUID studentUserId,
            String studentDisplayName,
            String studentEmail,
            String studentCode,
            UUID semesterId,
            String semesterName,
            String semesterNameEn,
            String semesterNameVi,
            String status,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal total,
            Instant dueDate,
            Instant paidAt,
            String notes,
            Instant createdAt,
            Instant updatedAt,
            List<InvoiceItemResponse> items) {
    }

    public record CreateInvoiceItemRequest(
            @NotBlank String description,
            int quantity,
            @NotNull BigDecimal unitPrice,
            @NotNull BigDecimal total) {
    }

    public record CreateInvoiceRequest(
            @NotBlank String invoiceNumber,
            @NotNull UUID studentId,
            @NotNull UUID studentUserId,
            @NotBlank String studentDisplayName,
            @NotBlank String studentEmail,
            @NotBlank String studentCode,
            @NotNull UUID semesterId,
            @NotBlank String semesterName,
            String semesterNameEn,
            String semesterNameVi,
            @NotNull BigDecimal subtotal,
            @NotNull BigDecimal discount,
            @NotNull BigDecimal total,
            @NotNull Instant dueDate,
            String notes,
            List<CreateInvoiceItemRequest> items) {
    }

    public record UpdateInvoiceRequest(
            String status,
            @NotNull BigDecimal subtotal,
            @NotNull BigDecimal discount,
            @NotNull BigDecimal total,
            @NotNull Instant dueDate,
            String notes) {
    }

    public record PaymentResponse(
            UUID id,
            String paymentNumber,
            UUID invoiceId,
            UUID studentId,
            BigDecimal amount,
            String method,
            String status,
            Instant paidAt,
            String transactionId,
            String notes,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreatePaymentRequest(
            @NotBlank String paymentNumber,
            @NotNull UUID invoiceId,
            @NotNull UUID studentId,
            @NotNull BigDecimal amount,
            @NotBlank String method,
            String notes) {
    }

    public record UpdatePaymentRequest(
            String status,
            Instant paidAt,
            String transactionId,
            String notes) {
    }

    public record PaymentIntentResponse(
            UUID id,
            String intentNumber,
            UUID invoiceId,
            UUID studentId,
            String provider,
            String status,
            BigDecimal amount,
            String currency,
            Instant expiresAt,
            Instant finalizedAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreatePaymentIntentRequest(
            @NotBlank String intentNumber,
            @NotNull UUID invoiceId,
            @NotNull UUID studentId,
            @NotBlank String provider,
            @NotNull BigDecimal amount,
            @NotNull Instant expiresAt) {
    }

    public record UpdatePaymentIntentRequest(
            String status,
            Instant finalizedAt) {
    }

    public record DeleteResponse(String message) {
    }
}
