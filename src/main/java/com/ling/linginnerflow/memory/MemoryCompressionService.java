package com.ling.linginnerflow.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.config.Observations;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Async sliding-window memory compression.
 *
 * Why async:
 *   compressMemory() used to run synchronously inside addMessage(), which runs
 *   inside the WebSocket doOnComplete() callback (Reactor thread). An LLM call
 *   there blocks the whole response pipeline for several seconds.
 *   Moving it to @Async keeps the write-path non-blocking.
 *
 * Sliding window strategy:
 *   Instead of replacing all history with one summary, we keep the most recent
 *   keepRecentRounds of raw messages intact. Only the older part is compressed.
 *
 *   Before: [msg1 … msg20]                             (20 messages)
 *   After:  [system: <summary of msg1-12>] + [msg13-20] (1 + 8 = 9 messages)
 *
 *   This gives the LLM both a compact history and full recent context.
 *
 * Concurrency guard:
 *   A Redis lock prevents two simultaneous compressions for the same user when
 *   messages arrive in rapid succession near the threshold.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryCompressionService {

    private final StringRedisTemplate redisTemplate;
    private final UserMemoryRepository userMemoryRepository;
    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;
    private final ObservationRegistry observationRegistry;
    private final Observations observations;

    private static final String SHORT_MEMORY_PREFIX = "memory:short:";
    private static final String COMPRESS_LOCK_PREFIX = "memory:compressing:";
    private static final long SHORT_MEMORY_TTL_MINUTES = 30;
    private static final long COMPRESS_LOCK_TTL_MINUTES = 5;

    @Value("${memory.compression.keep-recent:4}")
    private int keepRecentRounds;

    /**
     * Triggered by MemoryService when the history crosses the compression threshold.
     * Runs on the dedicated "memoryCompressionExecutor" thread pool.
     *
     * @param userId  the user whose history should be compressed
     * @param history a snapshot of the full history at trigger time
     */
    @Async("memoryCompressionExecutor")
    public void compressAsync(String userId, List<ConversationMessage> history) {
        Observation observation = Observation.createNotStarted("memory.compress", observationRegistry)
                .lowCardinalityKeyValue("memory.operation", "compress")
                .lowCardinalityKeyValue("memory.store", "mixed")
                .lowCardinalityKeyValue("memory.size_bucket", sizeBucket(history.size()))
                .start();

        try (Observation.Scope ignored = observation.openScope()) {
            String lockKey = COMPRESS_LOCK_PREFIX + userId;
            // Acquire lock — if another compression is already in flight, skip
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "1", COMPRESS_LOCK_TTL_MINUTES, TimeUnit.MINUTES);
            observation.lowCardinalityKeyValue("memory.lock_acquired", String.valueOf(Boolean.TRUE.equals(locked)));
            if (!Boolean.TRUE.equals(locked)) {
                log.info("[Compression] Already in progress for userId={}, skipping", userId);
                return;
            }

            log.info("[Compression] Starting: userId={}, totalMessages={}", userId, history.size());

            int keepMessages = keepRecentRounds * 2;  // each round = user + assistant

            if (history.size() <= keepMessages) {
                observation.lowCardinalityKeyValue("memory.skipped", "true");
                log.info("[Compression] History shorter than keep window, nothing to compress");
                return;
            }
            observation.lowCardinalityKeyValue("memory.skipped", "false");

            // ── Sliding window split ───────────────────────────────────────
            List<ConversationMessage> toSummarize =
                    new ArrayList<>(history.subList(0, history.size() - keepMessages));
            List<ConversationMessage> toKeep =
                    new ArrayList<>(history.subList(history.size() - keepMessages, history.size()));

            // ── Generate emotion-aware summary ─────────────────────────────
            String summary = generateSummary(toSummarize);

            // ── Build compressed history ───────────────────────────────────
            List<ConversationMessage> compressed = new ArrayList<>();
            compressed.add(new ConversationMessage(
                    "system",
                    "[Conversation summary] " + summary,
                    System.currentTimeMillis()
            ));
            compressed.addAll(toKeep);

            // ── Write back to Redis ────────────────────────────────────────
            String redisKey = SHORT_MEMORY_PREFIX + userId;
            redisTemplate.opsForValue().set(
                    redisKey,
                    objectMapper.writeValueAsString(compressed),
                    SHORT_MEMORY_TTL_MINUTES,
                    TimeUnit.MINUTES
            );

            // ── Persist summary + increment counter ────────────────────────
            UserMemory memory = userMemoryRepository.findByUserId(userId)
                    .orElseGet(() -> {
                        UserMemory m = new UserMemory();
                        m.setUserId(userId);
                        return m;
                    });
            memory.setConversationSummary(summary);
            memory.setCompressionCount(memory.getCompressionCount() + 1);
            userMemoryRepository.save(memory);

            log.info("[Compression] Done: userId={}, summaryChars={}, keptMessages={}, totalCompressions={}",
                    userId, summary.length(), toKeep.size(), memory.getCompressionCount());

        } catch (Exception e) {
            observation.error(e);
            log.error("[Compression] Failed: userId={}, error={}", userId, e.getMessage());
        } finally {
            redisTemplate.delete(COMPRESS_LOCK_PREFIX + userId);
            observation.stop();
        }
    }

    /**
     * Generates an emotion-aware, clinically structured summary of the given messages.
     * Split into two methods so prompt construction is testable without LLM mocking.
     */
    String generateSummary(List<ConversationMessage> messages) {
        String prompt = buildSummaryPrompt(messages);
        observations.tagPrompt("memory.compression.summary", "v1");
        return chatClientBuilder.build().prompt().user(prompt).call().content();
    }

    /** Builds the summary prompt. Package-private for unit testing. */
    String buildSummaryPrompt(List<ConversationMessage> messages) {
        StringBuilder dialogue = new StringBuilder();
        for (ConversationMessage msg : messages) {
            String label = switch (msg.getRole()) {
                case "user" -> "User";
                case "assistant" -> "AI";
                default -> "Context";
            };
            dialogue.append(label).append(": ").append(msg.getContent()).append("\n");
        }

        return """
                You are compressing a therapeutic conversation log to preserve \
                essential context for future sessions.

                Conversation to compress:
                %s

                Write a structured summary under 150 words covering:
                1. Emotional arc — how the user's distress or mood shifted
                2. Key disclosures — the core struggles or situations the user revealed
                3. What helped — any responses or moments the user engaged with positively
                4. Open threads — unresolved themes that should be followed up

                Rules:
                - Write in past tense, third person ("The user expressed…")
                - Be specific, not generic ("mentioned feeling trapped at work" not "had work stress")
                - Output ONLY the summary text, no headers or bullet points
                """.formatted(dialogue);
    }

    private String sizeBucket(int size) {
        if (size == 0) return "0";
        if (size <= 2) return "1-2";
        if (size <= 10) return "3-10";
        if (size <= 20) return "11-20";
        return "21+";
    }
}
