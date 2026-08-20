package io.campuscore.finance.web;

import io.campuscore.finance.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public FinanceDtos.PaymentResponse create(@Valid @RequestBody FinanceDtos.CreatePaymentRequest request) {
        return payments.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Page<FinanceDtos.PaymentResponse> findAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return payments.findAll(page, limit);
    }

    @GetMapping("{id}")
    public FinanceDtos.PaymentResponse findOne(@PathVariable UUID id) {
        return payments.findOne(id);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public FinanceDtos.PaymentResponse update(@PathVariable UUID id, @Valid @RequestBody FinanceDtos.UpdatePaymentRequest request) {
        return payments.update(id, request);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<FinanceDtos.DeleteResponse> remove(@PathVariable UUID id) {
        payments.remove(id);
        return ResponseEntity.ok(new FinanceDtos.DeleteResponse("Payment deleted successfully"));
    }
}
