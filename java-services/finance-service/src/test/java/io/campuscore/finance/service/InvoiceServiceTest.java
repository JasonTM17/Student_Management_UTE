package io.campuscore.finance.service;

import io.campuscore.finance.domain.Invoice;
import io.campuscore.finance.repository.InvoiceRepository;
import io.campuscore.finance.web.FinanceDtos;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Transactional
class InvoiceServiceTest {

    @Autowired
    private InvoiceRepository repository;

    @Autowired
    private InvoiceService service;

    @Test
    void create_persistsInvoice() {
        FinanceDtos.CreateInvoiceRequest request = new FinanceDtos.CreateInvoiceRequest(
                "INV-001", UUID.randomUUID(), UUID.randomUUID(), "John Doe", "john@test.com",
                "STU001", UUID.randomUUID(), "Spring 2026", null, null,
                new BigDecimal("1000.00"), new BigDecimal("0.00"), new BigDecimal("1000.00"),
                Instant.now().plusSeconds(86400), null, null);

        FinanceDtos.InvoiceResponse response = service.create(request);
        assertThat(response.id()).isNotNull();
        assertThat(response.invoiceNumber()).isEqualTo("INV-001");
    }

    @Test
    void findAll_returnsPagedInvoices() {
        repository.save(new Invoice("INV-002", UUID.randomUUID(), UUID.randomUUID(), "Jane Doe",
                "jane@test.com", "STU002", UUID.randomUUID(), "Spring 2026",
                new BigDecimal("500.00"), new BigDecimal("0.00"), new BigDecimal("500.00"),
                Instant.now().plusSeconds(86400)));

        Page<FinanceDtos.InvoiceResponse> result = service.findAll(1, 10);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void findOne_returnsInvoice() {
        Invoice invoice = repository.save(new Invoice("INV-003", UUID.randomUUID(), UUID.randomUUID(),
                "Find Me", "find@test.com", "STU003", UUID.randomUUID(), "Spring 2026",
                new BigDecimal("750.00"), new BigDecimal("0.00"), new BigDecimal("750.00"),
                Instant.now().plusSeconds(86400)));

        FinanceDtos.InvoiceResponse result = service.findOne(invoice.getId());
        assertThat(result.invoiceNumber()).isEqualTo("INV-003");
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        InvoiceService invoiceService(InvoiceRepository repository) {
            return new InvoiceService(repository, null);
        }
    }
}
