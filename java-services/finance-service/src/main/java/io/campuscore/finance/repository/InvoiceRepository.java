package io.campuscore.finance.repository;

import io.campuscore.finance.domain.Invoice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Page<Invoice> findByStudentIdOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    Page<Invoice> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByInvoiceNumber(String invoiceNumber);
}
