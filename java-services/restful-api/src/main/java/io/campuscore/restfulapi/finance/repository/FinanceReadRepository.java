package io.campuscore.restfulapi.finance.repository;

import io.campuscore.restfulapi.finance.web.FinanceReadDtos.InvoiceItemResponse;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.InvoiceSummary;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.PaymentResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read adapter for the Prisma-owned finance schema.
 *
 * <p>This candidate intentionally issues SELECT statements only. Invoice and
 * payment writes, checkout intents, provider callbacks/webhooks, CSV exports
 * and reconciliation remain owned by the Nest finance-service until a separate
 * cutover gate proves parity and rollback.</p>
 */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.finance-read", name = "enabled", havingValue = "true")
public class FinanceReadRepository {

    private static final String INVOICE_TABLE = "\"finance\".\"Invoice\"";
    private static final String ITEM_TABLE = "\"finance\".\"InvoiceItem\"";
    private static final String PAYMENT_TABLE = "\"finance\".\"Payment\"";

    private final NamedParameterJdbcTemplate jdbc;

    public FinanceReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<InvoiceRecord> findInvoices(
            long offset,
            int limit,
            String status,
            String semesterId,
            String studentId) {
        return jdbc.query(
                invoiceSelect()
                        + " WHERE (:status IS NULL OR invoice.\"status\" = :status)"
                        + " AND (:semesterId IS NULL OR invoice.\"semesterId\" = :semesterId)"
                        + " AND (:studentId IS NULL OR invoice.\"studentId\" = :studentId)"
                        + " ORDER BY invoice.\"createdAt\" DESC LIMIT :limit OFFSET :offset",
                pageParameters(offset, limit)
                        .addValue("status", status)
                        .addValue("semesterId", semesterId)
                        .addValue("studentId", studentId),
                FinanceReadRepository::mapInvoice);
    }

    public long countInvoices(String status, String semesterId, String studentId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + INVOICE_TABLE + " invoice"
                        + " WHERE (:status IS NULL OR invoice.\"status\" = :status)"
                        + " AND (:semesterId IS NULL OR invoice.\"semesterId\" = :semesterId)"
                        + " AND (:studentId IS NULL OR invoice.\"studentId\" = :studentId)",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("semesterId", semesterId)
                        .addValue("studentId", studentId),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<InvoiceRecord> findInvoiceById(String id) {
        return queryOne(
                invoiceSelect() + " WHERE invoice.\"id\" = :id",
                new MapSqlParameterSource("id", id),
                FinanceReadRepository::mapInvoice);
    }

    public List<InvoiceRecord> findStudentInvoices(String studentId, String semesterId) {
        return jdbc.query(
                invoiceSelect()
                        + " WHERE invoice.\"studentId\" = :studentId"
                        + " AND (:semesterId IS NULL OR invoice.\"semesterId\" = :semesterId)"
                        + " ORDER BY invoice.\"createdAt\" DESC",
                new MapSqlParameterSource()
                        .addValue("studentId", studentId)
                        .addValue("semesterId", semesterId),
                FinanceReadRepository::mapInvoice);
    }

    public Optional<InvoiceRecord> findStudentInvoiceById(String studentId, String invoiceId) {
        return queryOne(
                invoiceSelect() + " WHERE invoice.\"studentId\" = :studentId AND invoice.\"id\" = :invoiceId",
                new MapSqlParameterSource()
                        .addValue("studentId", studentId)
                        .addValue("invoiceId", invoiceId),
                FinanceReadRepository::mapInvoice);
    }

    public List<InvoiceItemResponse> findInvoiceItems(String invoiceId) {
        return jdbc.query(
                "SELECT \"id\", \"invoiceId\", \"description\", \"quantity\", \"unitPrice\", \"total\""
                        + " FROM " + ITEM_TABLE
                        + " WHERE \"invoiceId\" = :invoiceId ORDER BY \"id\" ASC",
                new MapSqlParameterSource("invoiceId", invoiceId),
                FinanceReadRepository::mapInvoiceItem);
    }

    public List<PaymentResponse> findInvoicePayments(String invoiceId) {
        return jdbc.query(
                paymentSelect()
                        + " WHERE payment.\"invoiceId\" = :invoiceId"
                        + " ORDER BY payment.\"createdAt\" DESC",
                new MapSqlParameterSource("invoiceId", invoiceId),
                FinanceReadRepository::mapPayment);
    }

    public List<PaymentResponse> findPayments(
            long offset,
            int limit,
            String status,
            String invoiceId,
            String studentId) {
        return jdbc.query(
                paymentSelect()
                        + " WHERE (:status IS NULL OR payment.\"status\" = :status)"
                        + " AND (:invoiceId IS NULL OR payment.\"invoiceId\" = :invoiceId)"
                        + " AND (:studentId IS NULL OR payment.\"studentId\" = :studentId)"
                        + " ORDER BY payment.\"createdAt\" DESC LIMIT :limit OFFSET :offset",
                pageParameters(offset, limit)
                        .addValue("status", status)
                        .addValue("invoiceId", invoiceId)
                        .addValue("studentId", studentId),
                FinanceReadRepository::mapPayment);
    }

    public long countPayments(String status, String invoiceId, String studentId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + PAYMENT_TABLE + " payment"
                        + " WHERE (:status IS NULL OR payment.\"status\" = :status)"
                        + " AND (:invoiceId IS NULL OR payment.\"invoiceId\" = :invoiceId)"
                        + " AND (:studentId IS NULL OR payment.\"studentId\" = :studentId)",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("invoiceId", invoiceId)
                        .addValue("studentId", studentId),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public Optional<PaymentResponse> findPaymentById(String id) {
        return queryOne(
                paymentSelect() + " WHERE payment.\"id\" = :id",
                new MapSqlParameterSource("id", id),
                FinanceReadRepository::mapPayment);
    }

    private static String invoiceSelect() {
        return "SELECT invoice.\"id\", invoice.\"invoiceNumber\", invoice.\"studentId\","
                + " invoice.\"studentUserId\", invoice.\"studentDisplayName\", invoice.\"studentEmail\","
                + " invoice.\"studentCode\", invoice.\"semesterId\", invoice.\"semesterName\","
                + " invoice.\"semesterNameEn\", invoice.\"semesterNameVi\", invoice.\"status\","
                + " invoice.\"subtotal\", invoice.\"discount\", invoice.\"total\", invoice.\"dueDate\","
                + " invoice.\"paidAt\", invoice.\"notes\", invoice.\"createdAt\", invoice.\"updatedAt\","
                + " COALESCE(payment_totals.\"paidAmount\", 0) AS \"paidAmount\""
                + " FROM " + INVOICE_TABLE + " invoice"
                + " LEFT JOIN (SELECT \"invoiceId\", SUM(\"amount\") AS \"paidAmount\""
                + " FROM " + PAYMENT_TABLE
                + " WHERE \"status\" = 'COMPLETED' GROUP BY \"invoiceId\") payment_totals"
                + " ON payment_totals.\"invoiceId\" = invoice.\"id\"";
    }

    private static String paymentSelect() {
        return "SELECT payment.\"id\", payment.\"paymentNumber\", payment.\"invoiceId\","
                + " payment.\"studentId\", payment.\"amount\", payment.\"method\", payment.\"status\","
                + " payment.\"paidAt\", payment.\"transactionId\", payment.\"paymentIntentId\","
                + " payment.\"notes\", payment.\"createdAt\", payment.\"updatedAt\","
                + " invoice.\"id\" AS \"invoice_id\", invoice.\"invoiceNumber\" AS \"invoice_invoiceNumber\","
                + " invoice.\"studentId\" AS \"invoice_studentId\","
                + " invoice.\"studentDisplayName\" AS \"invoice_studentDisplayName\","
                + " invoice.\"studentEmail\" AS \"invoice_studentEmail\","
                + " invoice.\"studentCode\" AS \"invoice_studentCode\","
                + " invoice.\"semesterId\" AS \"invoice_semesterId\","
                + " invoice.\"semesterName\" AS \"invoice_semesterName\","
                + " invoice.\"status\" AS \"invoice_status\", invoice.\"total\" AS \"invoice_total\""
                + " FROM " + PAYMENT_TABLE + " payment"
                + " JOIN " + INVOICE_TABLE + " invoice ON invoice.\"id\" = payment.\"invoiceId\"";
    }

    private static MapSqlParameterSource pageParameters(long offset, int limit) {
        return new MapSqlParameterSource()
                .addValue("offset", offset)
                .addValue("limit", limit);
    }

    private static InvoiceRecord mapInvoice(ResultSet resultSet, int ignored) throws SQLException {
        return new InvoiceRecord(
                resultSet.getString("id"),
                resultSet.getString("invoiceNumber"),
                resultSet.getString("studentId"),
                resultSet.getString("studentUserId"),
                resultSet.getString("studentDisplayName"),
                resultSet.getString("studentEmail"),
                resultSet.getString("studentCode"),
                resultSet.getString("semesterId"),
                resultSet.getString("semesterName"),
                resultSet.getString("semesterNameEn"),
                resultSet.getString("semesterNameVi"),
                resultSet.getString("status"),
                resultSet.getBigDecimal("subtotal"),
                resultSet.getBigDecimal("discount"),
                resultSet.getBigDecimal("total"),
                instant(resultSet.getTimestamp("dueDate")),
                instant(resultSet.getTimestamp("paidAt")),
                resultSet.getString("notes"),
                instant(resultSet.getTimestamp("createdAt")),
                instant(resultSet.getTimestamp("updatedAt")),
                resultSet.getBigDecimal("paidAmount"));
    }

    private static InvoiceItemResponse mapInvoiceItem(ResultSet resultSet, int ignored) throws SQLException {
        return new InvoiceItemResponse(
                resultSet.getString("id"),
                resultSet.getString("invoiceId"),
                resultSet.getString("description"),
                resultSet.getInt("quantity"),
                resultSet.getBigDecimal("unitPrice"),
                resultSet.getBigDecimal("total"));
    }

    private static PaymentResponse mapPayment(ResultSet resultSet, int ignored) throws SQLException {
        InvoiceSummary invoice = new InvoiceSummary(
                resultSet.getString("invoice_id"),
                resultSet.getString("invoice_invoiceNumber"),
                resultSet.getString("invoice_studentId"),
                resultSet.getString("invoice_studentDisplayName"),
                resultSet.getString("invoice_studentEmail"),
                resultSet.getString("invoice_studentCode"),
                resultSet.getString("invoice_semesterId"),
                resultSet.getString("invoice_semesterName"),
                resultSet.getString("invoice_status"),
                resultSet.getBigDecimal("invoice_total"));
        return new PaymentResponse(
                resultSet.getString("id"),
                resultSet.getString("paymentNumber"),
                resultSet.getString("invoiceId"),
                resultSet.getString("studentId"),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("method"),
                resultSet.getString("status"),
                instant(resultSet.getTimestamp("paidAt")),
                resultSet.getString("transactionId"),
                resultSet.getString("paymentIntentId"),
                resultSet.getString("notes"),
                instant(resultSet.getTimestamp("createdAt")),
                instant(resultSet.getTimestamp("updatedAt")),
                invoice);
    }

    private <T> Optional<T> queryOne(
            String sql,
            MapSqlParameterSource parameters,
            org.springframework.jdbc.core.RowMapper<T> mapper) {
        List<T> rows = jdbc.query(sql, parameters, mapper);
        return rows.stream().findFirst();
    }

    private static Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        LocalDateTime localDateTime = timestamp.toLocalDateTime();
        return localDateTime.toInstant(ZoneOffset.UTC);
    }

    public record InvoiceRecord(
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
            java.math.BigDecimal subtotal,
            java.math.BigDecimal discount,
            java.math.BigDecimal total,
            Instant dueDate,
            Instant paidAt,
            String notes,
            Instant createdAt,
            Instant updatedAt,
            java.math.BigDecimal paidAmount) {
    }
}
