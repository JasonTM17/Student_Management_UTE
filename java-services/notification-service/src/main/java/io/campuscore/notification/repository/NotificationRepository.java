package io.campuscore.notification.repository;

import io.campuscore.notification.domain.Notification;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying
    @Query(value = "UPDATE notifications.notification SET is_read = true, read_at = now() WHERE user_id = :userId AND is_read = false", nativeQuery = true)
    int markAllAsRead(UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);
}
