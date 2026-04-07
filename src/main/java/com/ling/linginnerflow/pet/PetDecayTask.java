// src/main/java/com/ling/linginnerflow/pet/PetDecayTask.java
package com.ling.linginnerflow.pet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PetDecayTask {

    private final PetRepository petRepository;
    private final PetService petService;

    // 每天凌晨3点执行
    @Scheduled(cron = "0 0 3 * * *")
    public void dailyDecay() {
        log.info("开始执行宠物每日衰减...");
        List<PetStatus> allPets = petRepository.findAll();
        allPets.forEach(pet ->
                petService.applyDecay(pet.getUserId()));
        log.info("宠物衰减完成，共处理{}只", allPets.size());
    }
}