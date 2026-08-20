package io.campuscore.restfulapi.thesis.repository;

import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.domain.ThesisRegistrationRound;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThesisRegistrationRoundRepository extends JpaRepository<ThesisRegistrationRound, UUID> {

    List<ThesisRegistrationRound> findAllByOrderByRegistrationStartDesc();

    List<ThesisRegistrationRound> findAllByStatusOrderByRegistrationStartDesc(RoundStatus status);
}
