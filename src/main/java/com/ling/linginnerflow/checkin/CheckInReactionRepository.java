package com.ling.linginnerflow.checkin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckInReactionRepository
        extends JpaRepository<CheckInReaction, Long> {

    long countByCheckInId(Long checkInId);

    boolean existsByCheckInIdAndUserId(Long checkInId, String userId);

    void deleteByCheckInIdAndUserId(Long checkInId, String userId);
}