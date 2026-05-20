package com.ling.linginnerflow.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * P2-7: Daily sweep that hard-archives triggers whose confidence score has
 * decayed below 0.1 (seen too few times and/or too long ago).
 *
 * Threshold 0.1 means approximately: 1 observation older than ~3 months,
 * or 5 observations older than ~9 months (exp decay with 90-day half-life).
 *
 * Archived triggers are moved to archivedTriggers JSON (not deleted) so
 * clinical history is preserved for audit purposes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriggerArchiveScheduler {

    private final UserMemoryRepository userMemoryRepository;
    private final MemoryService memoryService;

    // 每天凌晨4点执行 (PetDecayTask 跑凌晨3点，错开避免同时压DB)
    @Scheduled(cron = "0 0 4 * * *")
    public void runArchiveSweep() {
        List<UserMemory> allUsers = userMemoryRepository.findAll();
        int totalArchived = 0;
        for (UserMemory mem : allUsers) {
            try {
                totalArchived += memoryService.archiveDecayedTriggers(mem.getUserId());
            } catch (Exception e) {
                log.warn("[TriggerArchive] Failed for userId={}: {}", mem.getUserId(), e.getMessage());
            }
        }
        log.info("[TriggerArchive] Sweep complete: {} users scanned, {} triggers archived",
                allUsers.size(), totalArchived);
    }
}
