package io.campuscore.engagement.repository;

import io.campuscore.engagement.domain.Announcement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    Page<Announcement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<Announcement> findById(UUID id);
}
