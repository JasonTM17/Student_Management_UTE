package io.campuscore.restfulapi.thesis.repository;

import io.campuscore.restfulapi.thesis.domain.RoundStatus;
import io.campuscore.restfulapi.thesis.domain.ThesisRegistrationRound;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Deliberately exposes only derived read queries. Runtime credentials still
 * need to be SELECT-only before this adapter is enabled outside test fixtures.
 */
public interface ThesisRegistrationRoundRepository extends Repository<ThesisRegistrationRound, UUID> {

    List<ThesisRegistrationRound> findAllByOrderByRegistrationStartDesc();

    List<ThesisRegistrationRound> findAllByStatusOrderByRegistrationStartDesc(RoundStatus status);

    Optional<ThesisRegistrationRound> findById(UUID id);
}
