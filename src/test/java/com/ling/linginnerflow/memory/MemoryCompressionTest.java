package com.ling.linginnerflow.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.config.Observations;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies MemoryCompressionService behaviour:
 *
 * Q1. Sliding-window split: correct messages are summarized vs kept raw.
 * Q2. Redis is written with [system-summary] + [recent raw messages].
 * Q3. compressionCount is incremented on each compression.
 * Q4. Compression is skipped when history is shorter than the keep window.
 * Q5. Concurrency lock prevents a second simultaneous compression.
 * Q6. Summary prompt contains the actual conversation dialogue text.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemoryCompressionTest {

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient.Builder mockChatBuilder;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private UserMemoryRepository memoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MemoryCompressionService service;

    private static final int KEEP_RECENT_ROUNDS = 4; // → 8 messages kept raw

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new MemoryCompressionService(
                redisTemplate,
                memoryRepository,
                mockChatBuilder,
                objectMapper,
                ObservationRegistry.NOOP,
                new Observations(ObservationRegistry.NOOP)
        );
        ReflectionTestUtils.setField(service, "keepRecentRounds", KEEP_RECENT_ROUNDS);
    }

    // ── Q1: sliding-window split ─────────────────────────────────────────────

    @Test
    @DisplayName("Q1: old messages are summarized; only the most-recent rounds are kept raw")
    void compressAsync_slidingWindow_correctSplit() throws Exception {
        // 20 messages = 10 rounds; keepRecent=4 rounds=8 messages → summarize first 12
        List<ConversationMessage> history = makeHistory(20);

        when(mockChatBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn("User discussed work anxiety; AI offered grounding techniques.");
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                .thenReturn(true);
        when(memoryRepository.findByUserId(anyString())).thenReturn(Optional.of(new UserMemory()));

        service.compressAsync("u1", history);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(contains("memory:short:"), jsonCaptor.capture(), anyLong(), any());

        List<ConversationMessage> saved = objectMapper.readValue(
                jsonCaptor.getValue(), new TypeReference<>() {});

        // Structure: 1 system summary + 8 most-recent raw messages
        assertThat(saved).hasSize(1 + KEEP_RECENT_ROUNDS * 2);
        assertThat(saved.get(0).getRole()).isEqualTo("system");

        // The 8 kept messages must be exactly the LAST 8 of the original history
        List<ConversationMessage> keptMessages = saved.subList(1, saved.size());
        List<ConversationMessage> expectedKept = history.subList(12, 20);
        for (int i = 0; i < keptMessages.size(); i++) {
            assertThat(keptMessages.get(i).getContent())
                    .isEqualTo(expectedKept.get(i).getContent());
        }
    }

    // ── Q2: Redis content structure ──────────────────────────────────────────

    @Test
    @DisplayName("Q2: Redis entry starts with system summary containing the LLM summary text")
    void compressAsync_redisEntry_startsWithSystemSummary() throws Exception {
        List<ConversationMessage> history = makeHistory(20);
        String expectedSummary = "The user expressed fear of failure; responded positively to validation.";

        when(mockChatBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn(expectedSummary);
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                .thenReturn(true);
        when(memoryRepository.findByUserId(anyString())).thenReturn(Optional.of(new UserMemory()));

        service.compressAsync("u2", history);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(anyString(), jsonCaptor.capture(), anyLong(), any());

        List<ConversationMessage> saved = objectMapper.readValue(
                jsonCaptor.getValue(), new TypeReference<>() {});

        assertThat(saved.get(0).getRole()).isEqualTo("system");
        assertThat(saved.get(0).getContent()).contains(expectedSummary);
    }

    // ── Q3: compressionCount incremented ────────────────────────────────────

    @Test
    @DisplayName("Q3: compressionCount increments from 2 to 3 after one compression")
    void compressAsync_incrementsCompressionCount() {
        List<ConversationMessage> history = makeHistory(20);

        when(mockChatBuilder.build().prompt().user(anyString()).call().content())
                .thenReturn("summary");
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                .thenReturn(true);

        UserMemory existing = new UserMemory();
        existing.setCompressionCount(2);
        when(memoryRepository.findByUserId("u3")).thenReturn(Optional.of(existing));

        service.compressAsync("u3", history);

        ArgumentCaptor<UserMemory> memCaptor = ArgumentCaptor.forClass(UserMemory.class);
        verify(memoryRepository).save(memCaptor.capture());
        assertThat(memCaptor.getValue().getCompressionCount()).isEqualTo(3);
    }

    // ── Q4: skip when history is too short ───────────────────────────────────

    @Test
    @DisplayName("Q4: compression is skipped when history fits within the keep window")
    void compressAsync_shortHistory_skipsCompression() {
        // 6 messages < keepRecentRounds*2=8 → nothing to compress
        List<ConversationMessage> history = makeHistory(6);

        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                .thenReturn(true);

        service.compressAsync("u4", history);

        verify(mockChatBuilder, never()).build();
        verify(valueOps, never()).set(contains("memory:short:"), anyString(), anyLong(), any());
    }

    // ── Q5: concurrency lock ─────────────────────────────────────────────────

    @Test
    @DisplayName("Q5: second concurrent compression is blocked by the Redis lock")
    void compressAsync_lockAlreadyHeld_skipsCompression() {
        List<ConversationMessage> history = makeHistory(20);

        // Lock already held — setIfAbsent returns false
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), any()))
                .thenReturn(false);

        service.compressAsync("u5", history);

        verify(mockChatBuilder, never()).build();
        verify(valueOps, never()).set(contains("memory:short:"), anyString(), anyLong(), any());
    }

    // ── Q6: summary prompt contains the actual dialogue ──────────────────────

    @Test
    @DisplayName("Q6: buildSummaryPrompt includes conversation text and all required structural fields")
    void buildSummaryPrompt_containsDialogueAndRequiredFields() {
        // Test the prompt builder directly — no LLM mock needed
        List<ConversationMessage> messages = List.of(
                new ConversationMessage("user", "I feel overwhelmed at work", 0),
                new ConversationMessage("assistant", "That sounds really hard.", 0)
        );

        String prompt = service.buildSummaryPrompt(messages);

        // Dialogue text included
        assertThat(prompt).contains("I feel overwhelmed at work");
        assertThat(prompt).contains("That sounds really hard.");
        // Required structural fields present
        assertThat(prompt.toLowerCase()).contains("emotional arc");
        assertThat(prompt.toLowerCase()).contains("open threads");
        assertThat(prompt.toLowerCase()).contains("key disclosures");
        // Past-tense / third-person instruction present
        assertThat(prompt).contains("past tense");
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private List<ConversationMessage> makeHistory(int messageCount) {
        List<ConversationMessage> history = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            String role = (i % 2 == 0) ? "user" : "assistant";
            history.add(new ConversationMessage(role, "message-" + i, i * 1000L));
        }
        return history;
    }
}
