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
            "It's okay, let it stay here for a while.",
            "You don't have to feel better right now.",
            "You don't have to do anything.",
            "These feelings are allowed to exist.",
            "Slow is okay.",
            "No explanation needed, no change required.",
            "Just being here is enough.",
            "You are enough, right now.",
            "You don't have to control it.",
            "You don't need to figure everything out."
    );

    // Level 2：陪伴感（tap 20-80）
    private static final List<String> COMPANION_MESSAGES = List.of(
            "I'm here.",
            "You're not alone in this.",
            "Keep going, I'm with you.",
            "We'll take it slow together.",
            "No rush, no need to stop.",
            "You can lean for a moment.",
            "I'm here waiting with you.",
            "You don't have to say anything.",
            "I haven't gone anywhere.",
            "You are not alone."
    );

    // Level 3：轻微调节（tap > 80）
    private static final List<String> SOFT_GUIDANCE_MESSAGES = List.of(
            "Maybe try breathing a little slower.",
            "Notice the rhythm of your breath.",
            "Your body is slowly relaxing.",
            "You can gently soften your shoulders.",
            "Let the air in, and slowly let it out.",
            "No effort needed, just follow it.",
            "You're starting to slow down.",
            "Your body knows what to do."
    );

    // 节奏型（每3-5次才显示）
    private static final List<String> RHYTHM_MESSAGES = List.of(
            "one more", "again", "take it easy", "keep going",
            "you've got this", "still here", "no rush", "just like that"
    );

    private static final Map<Integer, String> MILESTONES = Map.of(
            10, "10 taps — you're showing up for yourself 🌱",
            50, "50 taps — we're still here 🌸",
            100, "100 taps — time to rest a little ☁️",
            200, "200 taps — that's enough for today 🌙"
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

        log.info("Tap connection established: userId={}, lastCount={}", userId, initCount);

        sendMessage(session, Map.of(
                "type", "connected",
                "message", "Ready when you are 🌱",
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
                "message", "Starting fresh 🌱"
        ));
        log.info("Tap reset: userId={}", userId);
    }

    private void handleContinue(WebSocketSession session,
                                String userId,
                                Map<String, String> payload) throws Exception {
        String lastKey = TAP_COUNT_PREFIX + userId + ":last";
        String lastCount = redisTemplate.opsForValue().get(lastKey);

        if (lastCount != null) {
            redisTemplate.opsForValue().set(
                    TAP_COUNT_PREFIX + userId, lastCount);
            log.info("Continuing last session: userId={}, count={}", userId, lastCount);

            sendMessage(session, Map.of(
                    "type", "continued",
                    "count", Long.parseLong(lastCount),
                    "message", "Picking up where you left off 💪"
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
            log.info("Tap snapshot saved: userId={}, count={}", userId, countStr);
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
                log.info("Tap session ended, saved: userId={}, count={}", userId, countStr);
            } catch (Exception e) {
                log.error("Tap record save failed: {}", e.getMessage());
            }
        }

        redisTemplate.delete(countKey);
        redisTemplate.delete(TAP_TIME_PREFIX + userId);
        log.info("Tap connection closed: userId={}", userId);
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