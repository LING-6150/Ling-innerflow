package com.ling.linginnerflow.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.config.Observations;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import redis.embedded.RedisServer;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemoryCompressionRedisIntegrationTest {

    private static final String USER_ID = "redis-integration-user";
    private static final String MEMORY_KEY = "memory:short:" + USER_ID;
    private static final String WINDOW_MESSAGE = "message-written-during-compression";
    private static final long BASE_TIMESTAMP = 1_750_000_000_000L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RedisServer redisServer;
    private LettuceConnectionFactory connectionFactory;
    private RecordingStringRedisTemplate redisTemplate;

    @BeforeAll
    void startRedis() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        redisServer = RedisServer.newRedisServer()
                .port(port)
                .setting("bind 127.0.0.1")
                .setting("daemonize no")
                .setting("appendonly no")
                .build();
        redisServer.start();

        connectionFactory = new LettuceConnectionFactory("127.0.0.1", port);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new RecordingStringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    void stopRedis() throws Exception {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @BeforeEach
    void resetRedis() {
        try (RedisConnection connection =
                     redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        redisTemplate.clearLastScriptResult();
    }

    @Test
    void realLuaPreservesWindowMessageExactTimestampsOrderAndTtl() throws Exception {
        CountDownLatch summaryStarted = new CountDownLatch(1);
        CountDownLatch allowSummaryToFinish = new CountDownLatch(1);
        ChatClient.Builder chatClientBuilder = blockingChatClient(
                summaryStarted, allowSummaryToFinish);
        UserMemoryRepository memoryRepository = mock(UserMemoryRepository.class);
        when(memoryRepository.findByUserId(USER_ID)).thenReturn(Optional.of(new UserMemory()));

        MemoryCompressionService compressionService =
                newCompressionService(chatClientBuilder, memoryRepository);
        MemoryService memoryService =
                newMemoryService(chatClientBuilder, memoryRepository, compressionService);

        List<ConversationMessage> initialHistory = makeHistory(19);
        redisTemplate.opsForValue().set(
                MEMORY_KEY, objectMapper.writeValueAsString(initialHistory),
                30, TimeUnit.MINUTES);

        Map<String, Long> timestampsBeforeCompression = new LinkedHashMap<>();
        initialHistory.forEach(message ->
                timestampsBeforeCompression.put(message.getContent(), message.getTimestamp()));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> compression = executor.submit(
                    () -> memoryService.addMessage(USER_ID, "assistant", "message-19"));

            assertThat(summaryStarted.await(5, TimeUnit.SECONDS))
                    .as("compression must reach the blocked LLM call")
                    .isTrue();
            rememberTimestamp(timestampsBeforeCompression, "message-19");

            memoryService.addMessage(USER_ID, "user", WINDOW_MESSAGE);
            rememberTimestamp(timestampsBeforeCompression, WINDOW_MESSAGE);

            allowSummaryToFinish.countDown();
            compression.get(5, TimeUnit.SECONDS);

            List<ConversationMessage> finalHistory = readHistory();
            assertThat(redisTemplate.lastScriptResult()).isEqualTo(1L);
            assertThat(finalHistory)
                    .extracting(ConversationMessage::getContent)
                    .containsExactly(
                            "[Conversation summary] compressed summary",
                            "message-12",
                            "message-13",
                            "message-14",
                            "message-15",
                            "message-16",
                            "message-17",
                            "message-18",
                            "message-19",
                            WINDOW_MESSAGE);
            assertThat(finalHistory.get(0).getRole()).isEqualTo("system");

            for (ConversationMessage message : finalHistory.subList(1, finalHistory.size())) {
                assertThat(message.getTimestamp())
                        .as("timestamp for %s must survive Redis cjson exactly", message.getContent())
                        .isEqualTo(timestampsBeforeCompression.get(message.getContent()));
                assertThat(message.getTimestamp()).isGreaterThanOrEqualTo(BASE_TIMESTAMP);
            }

            Long ttlSeconds = redisTemplate.getExpire(MEMORY_KEY, TimeUnit.SECONDS);
            assertThat(ttlSeconds).isBetween(1_700L, 1_800L);
        } finally {
            allowSummaryToFinish.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void realLuaReturnsZeroAndLeavesRedisUntouchedWhenSnapshotPrefixChanged() throws Exception {
        ChatClient.Builder chatClientBuilder = immediateChatClient();
        UserMemoryRepository memoryRepository = mock(UserMemoryRepository.class);
        MemoryCompressionService compressionService =
                newCompressionService(chatClientBuilder, memoryRepository);

        List<ConversationMessage> snapshot = makeHistory(20);
        List<ConversationMessage> rewritten = new ArrayList<>(snapshot);
        rewritten.set(0, new ConversationMessage(
                "user", "rewritten-prefix", BASE_TIMESTAMP + 10_000));
        String rewrittenJson = objectMapper.writeValueAsString(rewritten);
        redisTemplate.opsForValue().set(MEMORY_KEY, rewrittenJson, 30, TimeUnit.MINUTES);

        compressionService.compressAsync(USER_ID, snapshot);

        assertThat(redisTemplate.lastScriptResult()).isEqualTo(0L);
        assertThat(redisTemplate.opsForValue().get(MEMORY_KEY)).isEqualTo(rewrittenJson);
        assertThat(readHistory()).containsExactlyElementsOf(rewritten);
        verify(memoryRepository, never()).save(any());
    }

    private MemoryCompressionService newCompressionService(
            ChatClient.Builder chatClientBuilder,
            UserMemoryRepository memoryRepository) {
        Observations observations = new Observations(ObservationRegistry.NOOP);
        MemoryCompressionService service = new MemoryCompressionService(
                redisTemplate,
                memoryRepository,
                chatClientBuilder,
                objectMapper,
                ObservationRegistry.NOOP,
                observations);
        ReflectionTestUtils.setField(service, "keepRecentRounds", 4);
        return service;
    }

    private MemoryService newMemoryService(
            ChatClient.Builder chatClientBuilder,
            UserMemoryRepository memoryRepository,
            MemoryCompressionService compressionService) {
        MemoryService service = new MemoryService(
                redisTemplate,
                memoryRepository,
                chatClientBuilder,
                objectMapper,
                compressionService,
                mock(EmbeddingModel.class),
                ObservationRegistry.NOOP,
                new Observations(ObservationRegistry.NOOP));
        ReflectionTestUtils.setField(service, "compressionThreshold", 10);
        return service;
    }

    private ChatClient.Builder blockingChatClient(
            CountDownLatch summaryStarted,
            CountDownLatch allowSummaryToFinish) throws Exception {
        ChatClient.Builder builder = mock(
                ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        when(builder.build().prompt().user(anyString()).call().content())
                .thenAnswer(ignored -> {
                    summaryStarted.countDown();
                    if (!allowSummaryToFinish.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release summary generation");
                    }
                    return "compressed summary";
                });
        return builder;
    }

    private ChatClient.Builder immediateChatClient() {
        ChatClient.Builder builder = mock(
                ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        when(builder.build().prompt().user(anyString()).call().content())
                .thenReturn("compressed summary");
        return builder;
    }

    private List<ConversationMessage> makeHistory(int messageCount) {
        List<ConversationMessage> history = new ArrayList<>();
        for (int i = 0; i < messageCount; i++) {
            history.add(new ConversationMessage(
                    i % 2 == 0 ? "user" : "assistant",
                    "message-" + i,
                    BASE_TIMESTAMP + i));
        }
        return history;
    }

    private List<ConversationMessage> readHistory() throws Exception {
        return objectMapper.readValue(
                redisTemplate.opsForValue().get(MEMORY_KEY),
                new TypeReference<>() {});
    }

    private void rememberTimestamp(Map<String, Long> timestamps, String content) throws Exception {
        ConversationMessage message = readHistory().stream()
                .filter(candidate -> content.equals(candidate.getContent()))
                .findFirst()
                .orElseThrow();
        timestamps.put(content, message.getTimestamp());
    }

    private static final class RecordingStringRedisTemplate extends StringRedisTemplate {
        private Long lastScriptResult;

        private RecordingStringRedisTemplate(LettuceConnectionFactory connectionFactory) {
            super(connectionFactory);
        }

        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            T result = super.execute(script, keys, args);
            if (script.getResultType() == Long.class) {
                lastScriptResult = (Long) result;
            }
            return result;
        }

        private Long lastScriptResult() {
            return lastScriptResult;
        }

        private void clearLastScriptResult() {
            lastScriptResult = null;
        }
    }
}
