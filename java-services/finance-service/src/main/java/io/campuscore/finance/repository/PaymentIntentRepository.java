package io.campuscore.finance.repository;

import io.campuscore.finance.domain.PaymentIntent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {

    Optional<PaymentIntent> findByIntentNumber(String intentNumber);

    Page<PaymentIntent> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId, Pageable pageable);

    Page<PaymentIntent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByIntentNumber(String intentNumber);
}
