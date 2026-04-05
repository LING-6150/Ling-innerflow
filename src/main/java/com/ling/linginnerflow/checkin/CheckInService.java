package com.ling.linginnerflow.checkin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final CheckInProducer checkInProducer;

    /**
     * 提交打卡（异步版）
     * needAI=true → 发Kafka异步分析
     * needAI=false → 直接保存，不调用AI
     */
    public CheckIn submitCheckIn(String userId, String content,
                                 String visibility, Boolean needAI) {

        CheckIn checkIn = new CheckIn();
        checkIn.setUserId(userId);
        checkIn.setContent(content);
        checkIn.setVisibility(visibility);
        checkIn.setNeedAI(needAI != null ? needAI : true);

        if (Boolean.TRUE.equals(needAI)) {
            checkIn.setEmotionLevel(0);
            checkIn.setAiResponse("AI正在分析中，请稍后刷新查看...");
        } else {
            checkIn.setEmotionLevel(-1);  // -1表示不需要AI
            checkIn.setAiResponse(null);
        }

        CheckIn saved = checkInRepository.save(checkIn);
        log.info("打卡记录已保存: id={}, needAI={}", saved.getId(), needAI);

        // 只有needAI=true才发Kafka
        if (Boolean.TRUE.equals(needAI)) {
            CheckInEvent event = new CheckInEvent(
                    saved.getId(), userId, content);
            checkInProducer.sendCheckInEvent(event);
        }

        return saved;
    }

    public List<CheckIn> getUserHistory(String userId) {
        return checkInRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<CheckIn> getPublicWall() {
        return checkInRepository
                .findByVisibilityOrderByCreatedAtDesc("public");
    }
}