package io.campuscore.finance.service;

import io.campuscore.finance.domain.PaymentIntent;
import io.campuscore.finance.repository.PaymentIntentRepository;
import io.campuscore.finance.web.FinanceDtos;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentIntentService {

    private final PaymentIntentRepository intents;

    public PaymentIntentService(PaymentIntentRepository intents) {
        this.intents = intents;
    }

    @Transactional
    public FinanceDtos.PaymentIntentResponse create(FinanceDtos.CreatePaymentIntentRequest request) {
        if (intents.existsByIntentNumber(request.intentNumber())) {
            throw new IllegalArgumentException("Payment intent already exists");
        }
        PaymentIntent intent = new PaymentIntent(request.intentNumber(), request.invoiceId(),
                request.studentId(), request.provider(), request.amount(), request.expiresAt());
        intent.updateFields("REQUIRES_ACTION", null);
        return toResponse(intents.save(intent));
    }

    @Transactional(readOnly = true)
    public Page<FinanceDtos.PaymentIntentResponse> findAll(int page, int limit) {
        return intents.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(page - 1, 0), Math.min(Math.max(limit, 1), 100)))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FinanceDtos.PaymentIntentResponse findOne(UUID id) {
        return toResponse(intents.findById(id).orElseThrow(() -> new IllegalArgumentException("Payment intent not found")));
    }

    @Transactional
    public FinanceDtos.PaymentIntentResponse update(UUID id, FinanceDtos.UpdatePaymentIntentRequest request) {
        PaymentIntent existing = intents.findById(id).orElseThrow(() -> new IllegalArgumentException("Payment intent not found"));
        existing.updateFields(request.status(), request.finalizedAt());
        return toResponse(intents.save(existing));
    }

    @Transactional
    public void remove(UUID id) {
        intents.deleteById(id);
    }

    private FinanceDtos.PaymentIntentResponse toResponse(PaymentIntent intent) {
        return new FinanceDtos.PaymentIntentResponse(
                intent.getId(), intent.getIntentNumber(), intent.getInvoiceId(),
                intent.getStudentId(), intent.getProvider(), intent.getStatus(),
                intent.getAmount(), intent.getCurrency(), intent.getExpiresAt(),
                intent.getFinalizedAt(), intent.getCreatedAt(), intent.getUpdatedAt());
    }
}
