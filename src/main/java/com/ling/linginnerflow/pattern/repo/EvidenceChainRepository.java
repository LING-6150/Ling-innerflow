package com.ling.linginnerflow.pattern.repo;

import com.ling.linginnerflow.pattern.entity.EvidenceChain;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for EvidenceChain (§9).
 *
 * No custom finders needed at the foundation layer — callers look up chains
 * via the evidenceChainId stored on PatternInstance.
 */
public interface EvidenceChainRepository extends JpaRepository<EvidenceChain, String> {
}
