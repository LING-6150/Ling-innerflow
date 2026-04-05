package com.ling.linginnerflow.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserMemoryRepository
        extends JpaRepository<UserMemory, Long> {

    // 根据userId查询长期记忆
    Optional<UserMemory> findByUserId(String userId);
}