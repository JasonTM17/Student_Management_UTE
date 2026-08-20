package io.campuscore.finance.repository;

import io.campuscore.finance.domain.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByPaymentNumber(String paymentNumber);

    Page<Payment> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId, Pageable pageable);

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByPaymentNumber(String paymentNumber);
}
