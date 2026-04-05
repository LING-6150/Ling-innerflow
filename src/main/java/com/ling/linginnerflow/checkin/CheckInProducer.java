package com.ling.linginnerflow.checkin;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 打卡事件生产者
 * 把打卡事件发到Kafka Topic
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckInProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public static final String TOPIC = "checkin-events";

    public void sendCheckInEvent(CheckInEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, message);
            log.info("打卡事件已发送: checkInId={}", event.getCheckInId());
        } catch (Exception e) {
            log.error("Kafka发送失败: {}", e.getMessage());
        }
    }
}