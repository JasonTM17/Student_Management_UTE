package io.campuscore.restfulapi.finance.web;

import io.campuscore.restfulapi.finance.service.FinanceReadService;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.InvoiceDetail;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.InvoiceListItem;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.InvoiceListResponse;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.PaymentListResponse;
import io.campuscore.restfulapi.finance.web.FinanceReadDtos.PaymentResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feature-gated finance reads. Writes, checkout orchestration, payment provider
 * callbacks/webhooks and exports remain owned by the legacy finance-service in
 * this wave.
 */
@RestController
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.finance-read", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/finance")
public class FinanceReadController {

    private final FinanceReadService finance;

    public FinanceReadController(FinanceReadService finance) {
        this.finance = finance;
    }

    @GetMapping("my/invoices")
    @PreAuthorize("hasRole('STUDENT')")
    public List<InvoiceListItem> getMyInvoices(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String semesterId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("semesterId"));
        return finance.findStudentInvoices(jwt.getClaimAsString("studentId"), semesterId);
    }

    @GetMapping("my/invoices/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public InvoiceDetail getMyInvoiceById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {
        return finance.findStudentInvoice(jwt.getClaimAsString("studentId"), id);
    }

    @GetMapping("invoices")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public InvoiceListResponse getInvoices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String semesterId,
            @RequestParam(required = false) String studentId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit", "status", "semesterId", "studentId"));
        return finance.findInvoices(page, limit, status, semesterId, studentId);
    }

    @GetMapping("invoices/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public InvoiceDetail getInvoice(@PathVariable String id) {
        return finance.findInvoice(id);
    }

    @GetMapping("payments")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public PaymentListResponse getPayments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String invoiceId,
            @RequestParam(required = false) String studentId,
            @RequestParam MultiValueMap<String, String> queryParameters) {
        requireAllowedQuery(queryParameters, Set.of("page", "limit", "status", "invoiceId", "studentId"));
        return finance.findPayments(page, limit, status, invoiceId, studentId);
    }

    @GetMapping("payments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public PaymentResponse getPayment(@PathVariable String id) {
        return finance.findPayment(id);
    }

    private static void requireAllowedQuery(
            MultiValueMap<String, String> queryParameters,
            Set<String> allowed) {
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            if (!allowed.contains(entry.getKey()) || entry.getValue().size() != 1) {
                throw new IllegalArgumentException("Unexpected or repeated query parameter: " + entry.getKey());
            }
        }
    }
}
