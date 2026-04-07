// src/main/java/com/ling/linginnerflow/pet/PetService.java
package com.ling.linginnerflow.pet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PetService {

    private final PetRepository petRepository;

    // ===== 获取或初始化宠物 =====
    public PetStatus getOrCreate(String userId) {
        return petRepository.findByUserId(userId)
                .orElseGet(() -> {
                    PetStatus pet = new PetStatus();
                    pet.setUserId(userId);
                    return petRepository.save(pet);
                });
    }

    // ===== 对话完成，增加感知力 =====
    public PetStatus addAwareness(String userId, int emotionLevel) {
        PetStatus pet = getOrCreate(userId);

        // 情绪越深，感知力增加越多（L3-L4最有价值）
        double gain = switch (emotionLevel) {
            case 1 -> 1.0;
            case 2 -> 2.0;
            case 3 -> 3.5;
            case 4 -> 3.0;
            case 5 -> 1.0; // L5危机不加太多，保护用户
            default -> 1.0;
        };

        pet.setAwareness(pet.getAwareness()
                .add(BigDecimal.valueOf(gain)));
        pet.setGrowthPoints(pet.getGrowthPoints() + (long) gain);

        return evolveAndSave(pet, emotionLevel);
    }

    // ===== Tap，增加生命力 =====
    public PetStatus addVitality(String userId, int tapCount) {
        PetStatus pet = getOrCreate(userId);

        double gain = Math.min(tapCount * 0.1, 5.0);
        BigDecimal newVitality = pet.getVitality()
                .add(BigDecimal.valueOf(gain));

        // 上限100
        if (newVitality.compareTo(BigDecimal.valueOf(100)) > 0) {
            newVitality = BigDecimal.valueOf(100);
        }

        pet.setVitality(newVitality);
        pet.setGrowthPoints(pet.getGrowthPoints() + (long) gain);

        return evolveAndSave(pet, pet.getCurrentEmotion());
    }

    // ===== 打卡，增加稳定性 =====
    public PetStatus addStability(String userId) {
        log.info("addStability被调用: userId={}", userId);  // 加这行
        PetStatus pet = getOrCreate(userId);

        pet.setStability(pet.getStability()
                .add(BigDecimal.valueOf(2.0)));
        pet.setGrowthPoints(pet.getGrowthPoints() + 2L);

        return evolveAndSave(pet, pet.getCurrentEmotion());
    }

    // ===== 核心：计算凝聚度，触发进化 =====
    private PetStatus evolveAndSave(PetStatus pet, int emotionLevel) {


        // 改成（vitality不参与进化计算）
        BigDecimal newCohesion = pet.getAwareness()
                .multiply(BigDecimal.valueOf(0.6))
                .add(pet.getStability().multiply(BigDecimal.valueOf(0.4)));

        // 上限100
        int cohesionInt = Math.min(newCohesion
                .setScale(0, RoundingMode.HALF_UP)
                .intValue(), 100);

        pet.setCohesion(cohesionInt);
        pet.setCurrentEmotion(emotionLevel);

        // 根据凝聚度决定level
        int newLevel = cohesionToLevel(cohesionInt);
        if (newLevel > pet.getLevel()) {
            pet.setLevel(newLevel);
            pet.setLastEvolvedAt(LocalDateTime.now());
            log.info("宠物进化！userId={}, level={}, cohesion={}",
                    pet.getUserId(), newLevel, cohesionInt);
        }

        // 根据情绪更新颜色
        pet.setPrimaryColor(emotionToColor(emotionLevel));

        return petRepository.save(pet);
    }

    // 凝聚度 → 等级
    private int cohesionToLevel(int cohesion) {
        if (cohesion >= 80) return 5;  // 晶核态
        if (cohesion >= 60) return 4;  // 光环态
        if (cohesion >= 40) return 3;  // 稳定态
        if (cohesion >= 20) return 2;  // 成形态
        return 1;                       // 雾气态
    }

    // 情绪 → 颜色
    private String emotionToColor(int level) {
        return switch (level) {
            case 1 -> "#b8f0e0";  // 浅薄荷绿，平静
            case 2 -> "#b8e0ff";  // 浅蓝，轻柔
            case 3 -> "#d4b8ff";  // 浅紫，有些困扰
            case 4 -> "#ffb347";  // 橙色，沉重警示
            case 5 -> "#ff6b6b";  // 红色，危机
            default -> "#b8f0e0";
        };
    }

    // ===== 24小时衰减（Spring Task定时调用）=====
    public void applyDecay(String userId) {
        petRepository.findByUserId(userId).ifPresent(pet -> {
            // vitality每天衰减10%
            BigDecimal decayed = pet.getVitality()
                    .multiply(BigDecimal.valueOf(0.9))
                    .setScale(2, RoundingMode.HALF_UP);
            pet.setVitality(decayed);
            petRepository.save(pet);
            log.info("宠物衰减: userId={}, vitality={}", userId, decayed);
        });
    }
}