package io.campuscore.finance.service;

import io.campuscore.finance.domain.Payment;
import io.campuscore.finance.repository.PaymentRepository;
import io.campuscore.finance.web.FinanceDtos;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository payments;

    public PaymentService(PaymentRepository payments) {
        this.payments = payments;
    }

    @Transactional
    public FinanceDtos.PaymentResponse create(FinanceDtos.CreatePaymentRequest request) {
        if (payments.existsByPaymentNumber(request.paymentNumber())) {
            throw new IllegalArgumentException("Payment already exists");
        }
        Payment payment = new Payment(request.paymentNumber(), request.invoiceId(), request.studentId(),
                request.amount(), request.method());
        payment.updateFields("PENDING", null, null, request.notes());
        return toResponse(payments.save(payment));
    }

    @Transactional(readOnly = true)
    public Page<FinanceDtos.PaymentResponse> findAll(int page, int limit) {
        return payments.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100)))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FinanceDtos.PaymentResponse findOne(UUID id) {
        return toResponse(payments.findById(id).orElseThrow(() -> new IllegalArgumentException("Payment not found")));
    }

    @Transactional
    public FinanceDtos.PaymentResponse update(UUID id, FinanceDtos.UpdatePaymentRequest request) {
        Payment existing = payments.findById(id).orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        existing.updateFields(request.status(), request.paidAt(), request.transactionId(), request.notes());
        return toResponse(payments.save(existing));
    }

    @Transactional
    public void remove(UUID id) {
        payments.deleteById(id);
    }

    private FinanceDtos.PaymentResponse toResponse(Payment payment) {
        return new FinanceDtos.PaymentResponse(
                payment.getId(), payment.getPaymentNumber(), payment.getInvoiceId(),
                payment.getStudentId(), payment.getAmount(), payment.getMethod(),
                payment.getStatus(), payment.getPaidAt(), payment.getTransactionId(),
                payment.getNotes(), payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
