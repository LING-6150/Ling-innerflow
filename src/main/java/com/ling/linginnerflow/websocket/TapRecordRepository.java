package com.ling.linginnerflow.websocket;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TapRecordRepository
        extends JpaRepository<TapRecord, Long> {

    List<TapRecord> findByUserIdAndCreatedAtAfter(
            String userId, LocalDateTime after);
}