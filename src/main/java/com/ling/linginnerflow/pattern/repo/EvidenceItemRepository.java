package com.ling.linginnerflow.pattern.repo;

import com.ling.linginnerflow.pattern.entity.EvidenceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for EvidenceItem (§9).
 *
 * findByEvidenceChainId is the primary read path: the Insight Panel evidence
 * drawer fetches all items for a chain, then sorts them by occurredAt descending.
 */
public interface EvidenceItemRepository extends JpaRepository<EvidenceItem, String> {

    /** All items belonging to a given EvidenceChain, for the evidence drawer. */
    List<EvidenceItem> findByEvidenceChainId(String chainId);
}
