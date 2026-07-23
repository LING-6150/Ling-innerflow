package com.ling.linginnerflow.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.config.Observations;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryCompressionConcurrencyTest {

    private static final String USER_ID = "concurrent-user";
    private static final String MEMORY_KEY = "memory:short:" + USER_ID;
    private static final String WINDOW_MESSAGE = "message-written-during-compression";

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient.Builder chatClientBuilder;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private UserMemoryRepository memoryRepository;
    @Mock private EmbeddingModel embeddingModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> redisValue = new AtomicReference<>();
    private final AtomicBoolean compressionLock = new AtomicBoolean();
    private MemoryService memoryService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(MEMORY_KEY)).thenAnswer(ignored -> redisValue.get());
        when(valueOperations.setIfAbsent(
                startsWith("memory:compressing:"), eq("1"), anyLong(), eq(TimeUnit.MINUTES)))
                .thenAnswer(ignored -> compressionLock.compareAndSet(false, true));
        when(redisTemplate.delete(startsWith("memory:compressing:"))).thenAnswer(ignored -> {
            compressionLock.set(false);
            return true;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            redisValue.set(invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(
                eq(MEMORY_KEY), anyString(), anyLong(), eq(TimeUnit.MINUTES));
        when(redisTemplate.execute(
                any(RedisScript.class), eq(List.of(MEMORY_KEY)),
                anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> applyCompressionScript(
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4)));
        when(memoryRepository.findByUserId(USER_ID)).thenReturn(Optional.of(new UserMemory()));

        Observations observations = new Observations(ObservationRegistry.NOOP);
        MemoryCompressionService compressionService = new MemoryCompressionService(
                redisTemplate,
                memoryRepository,
                chatClientBuilder,
                objectMapper,
                ObservationRegistry.NOOP,
                observations
        );
        ReflectionTestUtils.setField(compressionService, "keepRecentRounds", 4);

        memoryService = new MemoryService(
                redisTemplate,
                memoryRepository,
                chatClientBuilder,
                objectMapper,
                compressionService,
                embeddingModel,
                ObservationRegistry.NOOP,
                observations
        );
        ReflectionTestUtils.setField(memoryService, "compressionThreshold", 10);
    }

    @Test
    void messageAddedWhileCompressionIsBlockedMustSurviveWriteBack() throws Exception {
        CountDownLatch summaryStarted = new CountDownLatch(1);
        CountDownLatch allowSummaryToFinish = new CountDownLatch(1);
        when(chatClientBuilder.build().prompt().user(anyString()).call().content())
                .thenAnswer(ignored -> {
                    summaryStarted.countDown();
                    if (!allowSummaryToFinish.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release summary generation");
                    }
                    return "compressed summary";
                });

        for (int i = 0; i < 19; i++) {
            memoryService.addMessage(USER_ID, roleFor(i), "message-" + i);
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> compression = executor.submit(
                    () -> memoryService.addMessage(USER_ID, roleFor(19), "message-19"));

            assertThat(summaryStarted.await(5, TimeUnit.SECONDS))
                    .as("compression must reach the blocked LLM call")
                    .isTrue();

            memoryService.addMessage(USER_ID, "user", WINDOW_MESSAGE);
            allowSummaryToFinish.countDown();
            compression.get(5, TimeUnit.SECONDS);

            List<ConversationMessage> finalHistory = objectMapper.readValue(
                    redisValue.get(), new TypeReference<>() {});
            assertThat(finalHistory)
                    .extracting(ConversationMessage::getContent)
                    .contains(WINDOW_MESSAGE);
        } finally {
            allowSummaryToFinish.countDown();
            executor.shutdownNow();
        }
    }

    private String roleFor(int index) {
        return index % 2 == 0 ? "user" : "assistant";
    }

    private synchronized Long applyCompressionScript(
            String snapshotJson, String summaryJson, String summarizedCount) throws Exception {
        List<ConversationMessage> current = objectMapper.readValue(
                redisValue.get(), new TypeReference<>() {});
        List<ConversationMessage> snapshot = objectMapper.readValue(
                snapshotJson, new TypeReference<>() {});
        if (current.size() < snapshot.size()
                || !current.subList(0, snapshot.size()).equals(snapshot)) {
            return 0L;
        }

        List<ConversationMessage> compressed = new ArrayList<>();
        compressed.add(objectMapper.readValue(summaryJson, ConversationMessage.class));
        compressed.addAll(current.subList(Integer.parseInt(summarizedCount), current.size()));
        redisValue.set(objectMapper.writeValueAsString(compressed));
        return 1L;
    }
}
