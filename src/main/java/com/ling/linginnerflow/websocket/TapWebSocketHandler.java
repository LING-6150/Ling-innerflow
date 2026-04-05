package com.ling.linginnerflow.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TapWebSocketHandler extends TextWebSocketHandler {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TapRecordRepository tapRecordRepository;

    private static final String TAP_COUNT_PREFIX = "tap:count:";
    private static final String TAP_TIME_PREFIX = "tap:timestamps:";

    // 替换原来的 RELIEF_MESSAGES 和 MEDITATION_MESSAGES

    // Level 1：允许存在（tap < 20）
    private static final List<String> ACCEPTANCE_MESSAGES = List.of(
            "没关系，让它在这里待一会儿",
            "不用急着变好，现在这样也可以",
            "你可以什么都不用做",
            "这些感觉可以存在",
            "慢一点也没关系",
            "不需要解释，也不需要改变",
            "只是待在这里就好",
            "现在的你已经够了",
            "不用控制它",
            "你不需要把一切都弄明白"
    );

    // Level 2：陪伴感（tap 20-80）
    private static final List<String> COMPANION_MESSAGES = List.of(
            "我在",
            "你不是一个人在这里",
            "继续就好，我会陪着你",
            "我们一起慢慢来",
            "不用快，也不用停",
            "你可以靠一会儿",
            "我在这里等你",
            "不用说话也可以",
            "我没有走开",
            "你不是孤单的"
    );

    // Level 3：轻微调节（tap > 80）
    private static final List<String> SOFT_GUIDANCE_MESSAGES = List.of(
            "也许可以慢一点呼吸",
            "注意一下你的呼吸节奏",
            "身体也在慢慢放松",
            "你可以轻轻地放松肩膀",
            "让空气进来，再慢慢出去",
            "不用刻意，就顺着它",
            "你正在慢下来",
            "你的身体知道怎么做"
    );

    // 节奏型（每3-5次才显示）
    private static final List<String> RHYTHM_MESSAGES = List.of(
            "一下", "再一下", "慢慢来", "继续",
            "可以的", "在这里", "不用急", "这样就好"
    );

    private static final Map<Integer, String> MILESTONES = Map.of(
            10, "已经10下了，你在陪着自己 🌱",
            50, "50下了，我们还在这里 🌸",
            100, "100下了，可以休息一下了 ☁️",
            200, "200下了，今天已经够了 🌙"
    );

    // emoji分层
    private static final List<String> SAFE_EMOJIS = List.of(
            "🌙", "☁️", "🫧", "🕊️", "🌊");
    private static final List<String> WARM_EMOJIS = List.of(
            "🌸", "🌷", "🌺", "🍃", "🌿");
    private static final List<String> LIGHT_EMOJIS = List.of(
            "✨", "💫", "⭐", "🌈");

    private static final List<Map<String, String>> MUSIC_LIST = List.of(
            Map.of("title", "Rain Night",
                    "url", "https://assets.mixkit.co/music/preview/mixkit-rainy-night-ambient-1242.mp3"),
            Map.of("title", "Soft Wind",
                    "url", "https://assets.mixkit.co/music/preview/mixkit-soft-wind-ambient-1253.mp3"),
            Map.of("title", "Deep Ocean",
                    "url", "https://assets.mixkit.co/music/preview/mixkit-deep-ocean-ambient-1246.mp3"),
            Map.of("title", "Warm Piano",
                    "url", "https://assets.mixkit.co/music/preview/mixkit-piano-reflection-152.mp3"),
            Map.of("title", "Forest Calm",
                    "url", "https://assets.mixkit.co/music/preview/mixkit-forest-serenity-1221.mp3")
    );
    @Override
    public void afterConnectionEstablished(WebSocketSession session)
            throws Exception {
        String userId = getUserId(session);

        String lastKey = TAP_COUNT_PREFIX + userId + ":last";
        String lastCount = redisTemplate.opsForValue().get(lastKey);
        long initCount = lastCount != null
                ? Long.parseLong(lastCount) : 0;

        String countKey = TAP_COUNT_PREFIX + userId;
        redisTemplate.delete(countKey);
        redisTemplate.delete(TAP_TIME_PREFIX + userId);

        log.info("Tap连接建立: userId={}, lastCount={}", userId, initCount);

        sendMessage(session, Map.of(
                "type", "connected",
                "message", "准备好了，开始吧 🌱",
                "lastCount", initCount,
                "hasLastSession", initCount > 0
        ));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) throws Exception {
        String userId = getUserId(session);
        Map<String, String> payload = objectMapper.readValue(
                message.getPayload(), Map.class);

        String type = payload.get("type");
        String mode = payload.getOrDefault("mode", "relief");

        if ("tap".equals(type)) {
            handleTap(session, userId, mode);
        } else if ("reset".equals(type)) {
            handleReset(session, userId);
        } else if ("save".equals(type)) {
            handleSave(userId);
        } else if ("continue".equals(type)) {
            handleContinue(session, userId, payload);
        }
    }

    private void handleTap(WebSocketSession session,
                           String userId, String mode) throws Exception {

        String countKey = TAP_COUNT_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(countKey);
        redisTemplate.expire(countKey, 24, TimeUnit.HOURS);

        String timeKey = TAP_TIME_PREFIX + userId;
        long now = System.currentTimeMillis();
        redisTemplate.opsForList().rightPush(timeKey, String.valueOf(now));
        redisTemplate.opsForList().trim(timeKey, -20, -1);
        redisTemplate.expire(timeKey, 24, TimeUnit.HOURS);

        int bpm = calculateBPM(timeKey);

        String message = (count != null && MILESTONES.containsKey(count.intValue()))
                ? MILESTONES.get(count.intValue())
                : getLayeredMessage(count != null ? count : 0L, mode);
        String emoji = getLayeredEmoji(count != null ? count : 0L);
        String musicRecommend = null;

        boolean shouldRecommendMusic = count != null && (
                ("relief".equals(mode) && bpm > 80 && count % 10 == 0) ||
                        ("meditation".equals(mode) && count % 5 == 0));

        if (shouldRecommendMusic) {
            Map<String, String> music = MUSIC_LIST.get(
                    new Random().nextInt(MUSIC_LIST.size()));
            musicRecommend = objectMapper.writeValueAsString(music);
        }

        Map<String, Object> feedback = new HashMap<>();
        feedback.put("type", "feedback");
        feedback.put("count", count);
        feedback.put("bpm", bpm);
        feedback.put("message", message);
        feedback.put("emoji", emoji);
        feedback.put("mode", mode);
        if (musicRecommend != null) {
            feedback.put("music", musicRecommend);
        }

        sendMessage(session, feedback);
        log.info("Tap: userId={}, count={}, bpm={}, mode={}",
                userId, count, bpm, mode);
    }

    private void handleReset(WebSocketSession session,
                             String userId) throws Exception {
        redisTemplate.delete(TAP_COUNT_PREFIX + userId);
        redisTemplate.delete(TAP_TIME_PREFIX + userId);
        redisTemplate.delete(TAP_COUNT_PREFIX + userId + ":last");

        sendMessage(session, Map.of(
                "type", "reset",
                "message", "重新开始 🌱"
        ));
        log.info("Tap重置: userId={}", userId);
    }

    private void handleContinue(WebSocketSession session,
                                String userId,
                                Map<String, String> payload) throws Exception {
        String lastKey = TAP_COUNT_PREFIX + userId + ":last";
        String lastCount = redisTemplate.opsForValue().get(lastKey);

        if (lastCount != null) {
            redisTemplate.opsForValue().set(
                    TAP_COUNT_PREFIX + userId, lastCount);
            log.info("继续上次会话: userId={}, count={}", userId, lastCount);

            sendMessage(session, Map.of(
                    "type", "continued",
                    "count", Long.parseLong(lastCount),
                    "message", "继续上次，加油 💪"
            ));
        }
    }

    private void handleSave(String userId) {
        String countKey = TAP_COUNT_PREFIX + userId;
        String countStr = redisTemplate.opsForValue().get(countKey);

        if (countStr != null && Integer.parseInt(countStr) > 0) {
            redisTemplate.opsForValue().set(
                    TAP_COUNT_PREFIX + userId + ":last",
                    countStr,
                    24, TimeUnit.HOURS
            );
            log.info("主动保存Tap快照: userId={}, count={}", userId, countStr);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) {
        String userId = getUserId(session);
        String countKey = TAP_COUNT_PREFIX + userId;
        String countStr = redisTemplate.opsForValue().get(countKey);

        if (countStr != null && Integer.parseInt(countStr) > 0) {
            redisTemplate.opsForValue().set(
                    TAP_COUNT_PREFIX + userId + ":last",
                    countStr,
                    24, TimeUnit.HOURS
            );

            try {
                TapRecord record = new TapRecord();
                record.setUserId(userId);
                record.setTotalCount(Integer.parseInt(countStr));
                record.setMode("relief");
                tapRecordRepository.save(record);
                log.info("Tap会话结束，已保存: userId={}, count={}",
                        userId, countStr);
            } catch (Exception e) {
                log.error("Tap记录保存失败: {}", e.getMessage());
            }
        }

        redisTemplate.delete(countKey);
        redisTemplate.delete(TAP_TIME_PREFIX + userId);
        log.info("Tap连接关闭: userId={}", userId);
    }

    private int calculateBPM(String timeKey) {
        try {
            List<String> timestamps = redisTemplate.opsForList()
                    .range(timeKey, -10, -1);
            if (timestamps == null || timestamps.size() < 2) return 0;

            long first = Long.parseLong(timestamps.get(0));
            long last = Long.parseLong(
                    timestamps.get(timestamps.size() - 1));
            long duration = last - first;

            if (duration <= 0) return 0;
            return (int) ((timestamps.size() - 1) * 60000.0 / duration);
        } catch (Exception e) {
            return 0;
        }
    }

    private String getLayeredMessage(long count, String mode) {
        // 每3-5次才说话，其他时候返回空
        if (count % 4 != 0 && !MILESTONES.containsKey((int) count)) {
            return "";
        }

        // 节奏型（冥想模式优先）
        if ("meditation".equals(mode)) {
            return RHYTHM_MESSAGES.get(
                    new Random().nextInt(RHYTHM_MESSAGES.size()));
        }

        // 按阶段分层
        if (count < 20) {
            return ACCEPTANCE_MESSAGES.get(
                    new Random().nextInt(ACCEPTANCE_MESSAGES.size()));
        } else if (count < 80) {
            return COMPANION_MESSAGES.get(
                    new Random().nextInt(COMPANION_MESSAGES.size()));
        } else {
            return SOFT_GUIDANCE_MESSAGES.get(
                    new Random().nextInt(SOFT_GUIDANCE_MESSAGES.size()));
        }
    }

    private String getLayeredEmoji(long count) {
        if (count < 20) {
            return SAFE_EMOJIS.get(new Random().nextInt(SAFE_EMOJIS.size()));
        } else if (count < 80) {
            return WARM_EMOJIS.get(new Random().nextInt(WARM_EMOJIS.size()));
        } else {
            return LIGHT_EMOJIS.get(new Random().nextInt(LIGHT_EMOJIS.size()));
        }
    }

    private void sendMessage(WebSocketSession session,
                             Map<String, Object> data) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(
                    objectMapper.writeValueAsString(data)));
        }
    }

    private String getUserId(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query != null && query.contains("userId=")) {
            return query.split("userId=")[1].split("&")[0];
        }
        return "anonymous";
    }
}