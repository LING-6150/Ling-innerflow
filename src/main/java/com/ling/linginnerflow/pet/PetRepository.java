// src/main/java/com/ling/linginnerflow/pet/PetRepository.java
package com.ling.linginnerflow.pet;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PetRepository extends JpaRepository<PetStatus, Long> {
    Optional<PetStatus> findByUserId(String userId);
}