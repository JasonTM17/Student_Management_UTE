package io.campuscore.finance.web;

import io.campuscore.finance.service.PaymentIntentService;
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
@RequestMapping("/api/v1/payment-intents")
public class PaymentIntentController {

    private final PaymentIntentService intents;

    public PaymentIntentController(PaymentIntentService intents) {
        this.intents = intents;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public FinanceDtos.PaymentIntentResponse create(@Valid @RequestBody FinanceDtos.CreatePaymentIntentRequest request) {
        return intents.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Page<FinanceDtos.PaymentIntentResponse> findAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return intents.findAll(page, limit);
    }

    @GetMapping("{id}")
    public FinanceDtos.PaymentIntentResponse findOne(@PathVariable UUID id) {
        return intents.findOne(id);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public FinanceDtos.PaymentIntentResponse update(@PathVariable UUID id, @Valid @RequestBody FinanceDtos.UpdatePaymentIntentRequest request) {
        return intents.update(id, request);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<FinanceDtos.DeleteResponse> remove(@PathVariable UUID id) {
        intents.remove(id);
        return ResponseEntity.ok(new FinanceDtos.DeleteResponse("Payment intent deleted successfully"));
    }
}
