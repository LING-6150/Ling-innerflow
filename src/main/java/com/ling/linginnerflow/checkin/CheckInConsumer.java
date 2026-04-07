package com.ling.linginnerflow.checkin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.agent.EmotionGraph;
import com.ling.linginnerflow.emotion.EmotionLogService;
import com.ling.linginnerflow.pet.PetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.bsc.langgraph4j.state.AgentState;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.redisson.api.RLock;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 打卡事件消费者
 *
 * 三大保证：
 * 1. 幂等性：Redis记录已处理消息ID，防止重复消费
 * 2. 可靠性：手动提交offset，消费失败不提交
 * 3. 容错性：重试3次+指数退避+死信队列
 *
 * 并发消费：3个消费者对应3个partition，提升吞吐量
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CheckInConsumer {

    private final ObjectMapper objectMapper;
    private final EmotionGraph emotionGraph;
    private final CheckInRepository checkInRepository;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RedissonClient redissonClient;

    private static final String CACHE_PREFIX = "checkin:analysis:";
    private static final long CACHE_TTL = 60;
    private static final String DLT_TOPIC = "checkin-events-dlt";
    private static final int MAX_RETRY = 3;
    // 幂等性：已处理消息ID集合，TTL24小时
    private static final String PROCESSED_KEY = "kafka:processed:checkin";

    private final EmotionLogService emotionLogService;

    // 注入PetService
    private final PetService petService;


    @KafkaListener(
            topics = CheckInProducer.TOPIC,
            groupId = "innerflow-group",
            concurrency = "3"  // 3个并发消费者对应3个partition
    )
    public void consume(ConsumerRecord<String, String> record,
                        Acknowledgment ack) {

        // 构建唯一消息ID：topic-partition-offset
        String messageId = record.topic() + "-" +
                record.partition() + "-" + record.offset();

        // 幂等性检查：是否已经处理过这条消息
        Boolean alreadyProcessed = redisTemplate.opsForSet()
                .isMember(PROCESSED_KEY, messageId);
        if (Boolean.TRUE.equals(alreadyProcessed)) {
            log.warn("消息已处理，跳过(幂等): messageId={}", messageId);
            ack.acknowledge();
            return;
        }

        String message = record.value();
        int retryCount = 0;

        while (retryCount < MAX_RETRY) {
            try {
                // 分布式锁：防止同一条打卡记录被并发处理
                CheckInEvent event = objectMapper
                        .readValue(message, CheckInEvent.class);
                String lockKey = "lock:checkin:" + event.getCheckInId();
                RLock lock = redissonClient.getLock(lockKey);

                if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                    try {
                        processMessage(message);
                    } finally {
                        lock.unlock();
                    }
                } else {
                    log.warn("获取分布式锁失败，跳过: lockKey={}", lockKey);
                }

                // 处理成功：标记消息已处理（幂等）
                redisTemplate.opsForSet().add(PROCESSED_KEY, messageId);
                redisTemplate.expire(PROCESSED_KEY, 24, TimeUnit.HOURS);

                // 手动提交offset
                ack.acknowledge();
                log.info("消息处理成功: messageId={}", messageId);
                return;

            } catch (Exception e) {
                retryCount++;
                log.warn("消费失败，第{}/{}次重试: messageId={}, error={}",
                        retryCount, MAX_RETRY, messageId, e.getMessage());

                if (retryCount < MAX_RETRY) {
                    try {
                        // 指数退避：1s, 2s, 4s
                        Thread.sleep(1000L *
                                (long) Math.pow(2, retryCount - 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        // 重试全部失败，发送到死信队列
        log.error("消息重试{}次失败，发送死信队列: messageId={}",
                MAX_RETRY, messageId);
        kafkaTemplate.send(DLT_TOPIC, message);

        // 标记已处理（避免死信队列也重复处理）
        redisTemplate.opsForSet().add(PROCESSED_KEY, messageId);
        redisTemplate.expire(PROCESSED_KEY, 24, TimeUnit.HOURS);
        ack.acknowledge();
    }

    /**
     * 死信队列消费者
     */
    @KafkaListener(topics = DLT_TOPIC,
            groupId = "innerflow-dlt-group")
    public void consumeDlt(String message) {
        log.error("=== 死信队列消息待人工处理 ===: {}", message);
    }

    private void processMessage(String message) throws Exception {
        CheckInEvent event = objectMapper
                .readValue(message, CheckInEvent.class);
        log.info("收到打卡事件: checkInId={}, userId={}, partition消费",
                event.getCheckInId(), event.getUserId());

        String cacheKey = CACHE_PREFIX + sha256(event.getContent());
        String cached = redisTemplate.opsForValue().get(cacheKey);

        int emotionLevel;
        String aiResponse;

        if (cached != null) {
            log.info("Redis缓存命中，跳过LLM: key={}", cacheKey);
            CachedAnalysis analysis = objectMapper
                    .readValue(cached, CachedAnalysis.class);
            emotionLevel = analysis.getEmotionLevel();
            aiResponse = analysis.getAiResponse();
        } else {
            log.info("缓存未命中，调用LLM分析");
            Map<String, Object> input = new HashMap<>();
            input.put("userInput", event.getContent());
            AgentState finalState = emotionGraph.buildGraph()
                    .invoke(input).get();

            emotionLevel = (int) finalState.data()
                    .getOrDefault("emotionLevel", 1);
            aiResponse = (String) finalState.data()
                    .getOrDefault("response", "");

            CachedAnalysis analysis = new CachedAnalysis(
                    emotionLevel, aiResponse);
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(analysis),
                    CACHE_TTL,
                    TimeUnit.MINUTES
            );
            log.info("分析结果已缓存: key={}", cacheKey);
        }

        checkInRepository.findById(event.getCheckInId())
                .ifPresent(checkIn -> {
                    checkIn.setEmotionLevel(emotionLevel);
                    checkIn.setAiResponse(aiResponse);
                    checkInRepository.save(checkIn);
                    log.info("打卡记录更新完成: id={}, level=L{}",
                            checkIn.getId(), emotionLevel);
                });
        emotionLogService.log(
                event.getUserId(),
                emotionLevel,
                event.getContent(),
                aiResponse,
                "checkin"
        );
    }

    private String sha256(String content) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    content.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.warn("SHA256计算失败，降级用hashCode");
            return String.valueOf(content.hashCode());
        }
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    static class CachedAnalysis {
        private int emotionLevel;
        private String aiResponse;
    }
}