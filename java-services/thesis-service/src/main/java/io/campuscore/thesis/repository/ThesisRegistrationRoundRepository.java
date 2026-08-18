package io.campuscore.thesis.repository;

import io.campuscore.thesis.domain.RoundStatus;
import io.campuscore.thesis.domain.ThesisRegistrationRound;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThesisRegistrationRoundRepository extends JpaRepository<ThesisRegistrationRound, UUID> {

    List<ThesisRegistrationRound> findAllByOrderByRegistrationStartDesc();

    List<ThesisRegistrationRound> findAllByStatusOrderByRegistrationStartDesc(RoundStatus status);
}
