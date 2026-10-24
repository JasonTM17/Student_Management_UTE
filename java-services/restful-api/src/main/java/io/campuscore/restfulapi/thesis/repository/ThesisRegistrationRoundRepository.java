package io.campuscore.restfulapi.thesis.repository;

import io.campuscore.restfulapi.thesis.model.RoundStatus;
import io.campuscore.restfulapi.thesis.entity.ThesisRegistrationRound;
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

    /**
     * The unfiltered "active" list: archived (CLOSED) rounds stay out of
     * default pickers while remaining reachable through the explicit status
     * query above.
     */
    List<ThesisRegistrationRound> findAllByStatusNotOrderByRegistrationStartDesc(RoundStatus status);

    Optional<ThesisRegistrationRound> findById(UUID id);
}
