package io.campuscore.finance.web;

import io.campuscore.finance.service.InvoiceService;
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
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoices;

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public FinanceDtos.InvoiceResponse create(@Valid @RequestBody FinanceDtos.CreateInvoiceRequest request) {
        return invoices.create(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Page<FinanceDtos.InvoiceResponse> findAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return invoices.findAll(page, limit);
    }

    @GetMapping("{id}")
    public FinanceDtos.InvoiceResponse findOne(@PathVariable UUID id) {
        return invoices.findOne(id);
    }

    @PutMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public FinanceDtos.InvoiceResponse update(@PathVariable UUID id, @Valid @RequestBody FinanceDtos.UpdateInvoiceRequest request) {
        return invoices.update(id, request);
    }

    @DeleteMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<FinanceDtos.DeleteResponse> remove(@PathVariable UUID id) {
        invoices.remove(id);
        return ResponseEntity.ok(new FinanceDtos.DeleteResponse("Invoice deleted successfully"));
    }
}
