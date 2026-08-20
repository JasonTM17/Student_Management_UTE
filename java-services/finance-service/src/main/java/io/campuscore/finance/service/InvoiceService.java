package io.campuscore.finance.service;

import io.campuscore.finance.domain.Invoice;
import io.campuscore.finance.domain.InvoiceItem;
import io.campuscore.finance.repository.InvoiceItemRepository;
import io.campuscore.finance.repository.InvoiceRepository;
import io.campuscore.finance.web.FinanceDtos;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;

    public InvoiceService(InvoiceRepository invoices, InvoiceItemRepository items) {
        this.invoices = invoices;
        this.items = items;
    }

    @Transactional
    public FinanceDtos.InvoiceResponse create(FinanceDtos.CreateInvoiceRequest request) {
        if (invoices.existsByInvoiceNumber(request.invoiceNumber())) {
            throw new IllegalArgumentException("Invoice already exists");
        }
        Invoice invoice = new Invoice(
                request.invoiceNumber(), request.studentId(), request.studentUserId(),
                request.studentDisplayName(), request.studentEmail(), request.studentCode(),
                request.semesterId(), request.semesterName(), request.subtotal(),
                request.discount(), request.total(), request.dueDate());
        invoice.updateFields("DRAFT", request.subtotal(), request.discount(), request.total(),
                request.dueDate(), null, request.notes());
        Invoice saved = invoices.save(invoice);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<FinanceDtos.InvoiceResponse> findAll(int page, int limit) {
        return invoices.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100)))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FinanceDtos.InvoiceResponse findOne(UUID id) {
        return toResponse(invoices.findById(id).orElseThrow(() -> new IllegalArgumentException("Invoice not found")));
    }

    @Transactional
    public FinanceDtos.InvoiceResponse update(UUID id, FinanceDtos.UpdateInvoiceRequest request) {
        Invoice existing = invoices.findById(id).orElseThrow(() -> new IllegalArgumentException("Invoice not found"));
        existing.updateFields(request.status(), request.subtotal(), request.discount(), request.total(),
                request.dueDate(), null, request.notes());
        return toResponse(invoices.save(existing));
    }

    @Transactional
    public void remove(UUID id) {
        invoices.deleteById(id);
    }

    private FinanceDtos.InvoiceResponse toResponse(Invoice invoice) {
        return new FinanceDtos.InvoiceResponse(
                invoice.getId(), invoice.getInvoiceNumber(), invoice.getStudentId(),
                invoice.getStudentUserId(), invoice.getStudentDisplayName(), invoice.getStudentEmail(),
                invoice.getStudentCode(), invoice.getSemesterId(), invoice.getSemesterName(),
                invoice.getSemesterNameEn(), invoice.getSemesterNameVi(), invoice.getStatus(),
                invoice.getSubtotal(), invoice.getDiscount(), invoice.getTotal(),
                invoice.getDueDate(), invoice.getPaidAt(), invoice.getNotes(),
                invoice.getCreatedAt(), invoice.getUpdatedAt(), null);
    }
}
