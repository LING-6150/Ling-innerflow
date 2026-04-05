package com.ling.linginnerflow.checkin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 打卡树洞业务逻辑（异步版本）
 *
 * 新流程：
 * 1. 先把打卡记录存入MySQL（状态：pending）
 * 2. 发Kafka消息，立刻返回给用户
 * 3. Consumer异步处理，更新情绪等级和AI回复
 *
 * 好处：用户不需要等LLM响应（原来要3-5秒）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final CheckInProducer checkInProducer;

    /**
     * 提交打卡（异步版）
     * 立刻返回，AI回复异步生成
     */
    public CheckIn submitCheckIn(String userId, String content,
                                 String visibility) {

        // 第一步：先存入MySQL，状态pending
        CheckIn checkIn = new CheckIn();
        checkIn.setUserId(userId);
        checkIn.setContent(content);
        checkIn.setEmotionLevel(0); // 0表示待处理
        checkIn.setAiResponse("AI正在分析中，请稍后刷新查看...");
        checkIn.setVisibility(visibility);

        CheckIn saved = checkInRepository.save(checkIn);
        log.info("打卡记录已保存: id={}", saved.getId());

        // 第二步：发Kafka消息，异步处理
        CheckInEvent event = new CheckInEvent(
                saved.getId(), userId, content);
        checkInProducer.sendCheckInEvent(event);

        // 第三步：立刻返回，不等LLM
        return saved;
    }

    /**
     * 查询某用户的历史打卡记录
     */
    public List<CheckIn> getUserHistory(String userId) {
        return checkInRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 查询公开树洞
     */
    public List<CheckIn> getPublicWall() {
        return checkInRepository
                .findByVisibilityOrderByCreatedAtDesc("public");
    }
}
