package com.ling.linginnerflow.pattern.repo;

import com.ling.linginnerflow.pattern.domain.Domain;
import com.ling.linginnerflow.pattern.domain.PatternStatus;
import com.ling.linginnerflow.pattern.entity.PatternInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for PatternInstance (§8.2).
 *
 * The findByUserIdAndPatternKeyAndDomainAndStatusNot query is the primary
 * deduplication guard: before creating a new instance for (user, patternKey,
 * domain), call this to find any non-rejected active instance and update it
 * instead of inserting a duplicate.
 */
public interface PatternInstanceRepository extends JpaRepository<PatternInstance, String> {

    /** All instances for a user, regardless of status — used for the Insight Panel full load. */
    List<PatternInstance> findByUserId(String userId);

    /** Instances filtered by status — used for domain-tab + status-filter queries in the UI. */
    List<PatternInstance> findByUserIdAndStatus(String userId, PatternStatus status);

    /**
     * Returns the single active (non-excluded-status) instance for a
     * (user, patternKey, domain) triple, if one exists.
     *
     * Primary deduplication query: if this returns a value, the engine must
     * update that instance rather than inserting a new one.
     *
     * Typical call: findByUserIdAndPatternKeyAndDomainAndStatusNot(userId, key, domain, PatternStatus.rejected)
     */
    Optional<PatternInstance> findByUserIdAndPatternKeyAndDomainAndStatusNot(
            String userId,
            String patternKey,
            Domain domain,
            PatternStatus status
    );

    /**
     * Returns all instances (any status) for a (user, patternKey, domain) triple.
     * Used for the 90-day rejected-cooldown check and history views.
     */
    List<PatternInstance> findByUserIdAndPatternKeyAndDomain(
            String userId,
            String patternKey,
            Domain domain
    );
}
