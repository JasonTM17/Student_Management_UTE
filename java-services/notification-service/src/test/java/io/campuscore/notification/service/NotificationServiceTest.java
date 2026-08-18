package io.campuscore.notification.service;

import io.campuscore.notification.domain.Notification;
import io.campuscore.notification.repository.NotificationRepository;
import io.campuscore.notification.web.NotificationDtos;
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
class NotificationServiceTest {

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private NotificationService service;

    @Test
    void findMy_returnsPagedNotifications() {
        UUID userId = UUID.randomUUID();
        repository.save(new Notification(userId, "Title", "Message", "INFO", null));
        repository.save(new Notification(userId, "Other", "Body", "INFO", null));

        Page<NotificationDtos.NotificationResponse> result = service.findMy(userId, 1, 10, null);
        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void getUnreadCount_returnsCorrectCount() {
        UUID userId = UUID.randomUUID();
        repository.save(new Notification(userId, "Title", "Message", "INFO", null));

        NotificationDtos.UnreadCountResponse result = service.getUnreadCount(userId);
        assertThat(result.unreadCount()).isEqualTo(1);
    }

    @Test
    void markRead_setsReadFlag() {
        UUID userId = UUID.randomUUID();
        Notification notification = repository.save(new Notification(userId, "Title", "Message", "INFO", null));

        NotificationDtos.MarkReadResponse result = service.markRead(userId, notification.getId());
        assertThat(result.read()).isTrue();
        assertThat(result.readAt()).isNotNull();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        NotificationService notificationService(NotificationRepository repository) {
            return new NotificationService(repository);
        }
    }
}
