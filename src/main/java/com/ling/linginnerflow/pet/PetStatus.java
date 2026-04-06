// src/main/java/com/ling/linginnerflow/pet/PetStatus.java
package com.ling.linginnerflow.pet;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "pet_status")
public class PetStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String userId;

    // 三元能量
    @Column(precision = 10, scale = 2)
    private BigDecimal awareness = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal vitality = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal stability = BigDecimal.ZERO;

    // 进化状态
    private Integer level = 1;
    private Integer cohesion = 0;
    private Long growthPoints = 0L;

    // 视觉参数
    @Column(length = 20)
    // 改成
    private String primaryColor = "#b8f0e0";

    private Integer currentEmotion = 1;
    private LocalDateTime lastEvolvedAt;
}