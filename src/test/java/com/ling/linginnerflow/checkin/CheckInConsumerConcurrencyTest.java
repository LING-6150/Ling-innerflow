package com.ling.linginnerflow.checkin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.agent.EmotionGraph;
import com.ling.linginnerflow.cache.RedisDefenseService;
import com.ling.linginnerflow.emotion.EmotionLogService;
import com.ling.linginnerflow.pet.PetService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Concurrency and reliability tests for CheckInConsumer.
 *
 * Three guarantees verified:
 *   Q1. Idempotency      — duplicate messageId skips processing (no Redisson lock acquired).
 *   Q2. Distributed lock — N concurrent threads with the same checkInId:
 *                          exactly 1 processes; the rest are blocked at tryLock and skip.
 *   Q3. Dead-letter queue — after MAX_RETRY (3) failures the message is routed to DLT.
 *
 * RedisDefenseService (three-defence cache) is mocked to return a pre-built
 * CachedAnalysis so EmotionGraph / LLM calls are never needed.
 * The real ObjectMapper is injected so Kafka record parsing is genuine.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckInConsumerConcurrencyTest {

    @Mock private EmotionGraph emotionGraph;
    @Mock private CheckInRepository checkInRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SetOperations<String, String> setOps;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock rLock;
    @Mock private EmotionLogService emotionLogService;
    @Mock private PetService petService;
    @Mock private RedisDefenseService cacheDefenseService;
    @Mock private Acknowledgment ack;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CheckInConsumer consumer;

    /** checkInId=1 drives lock key "lock:checkin:1" */
    private static final String MESSAGE =
            "{\"checkInId\":1,\"userId\":\"u1\",\"content\":\"I feel anxious\"}";

    private static final CheckInConsumer.CachedAnalysis CACHED_ANALYSIS =
            new CheckInConsumer.CachedAnalysis(2, "That sounds really hard.");

    @BeforeEach
    void setUp() throws Exception {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);

        consumer = new CheckInConsumer(
                objectMapper, emotionGraph, checkInRepository,
                redisTemplate, kafkaTemplate, redissonClient,
                emotionLogService, petService, cacheDefenseService);
    }

    // ── Q1: Idempotency ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Q1: already-processed messageId is skipped — Redisson lock never acquired")
    void consume_alreadyProcessed_skipsLockAcquisition() {
        when(setOps.isMember(anyString(), anyString())).thenReturn(true);

        consumer.consume(record(0), ack);

        verify(redissonClient, never()).getLock(any());
        verify(ack).acknowledge();
    }

    // ── Q2: Distributed lock under concurrency ───────────────────────────────

    @Test
    @DisplayName("Q2: 5 concurrent threads for the same checkInId — exactly 1 processes")
    void concurrentConsume_sameLockKey_onlyOneProcesses() throws Exception {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);

        // First thread wins the lock; the rest are blocked
        AtomicInteger lockCalls = new AtomicInteger(0);
        when(rLock.tryLock(anyLong(), anyLong(), any()))
                .thenAnswer(inv -> lockCalls.getAndIncrement() == 0);

        // cacheDefenseService returns analysis — no LLM needed
        when(cacheDefenseService.getWithMutex(anyString(), anyString(), any(),
                eq(CheckInConsumer.CachedAnalysis.class), anyLong()))
                .thenReturn(CACHED_ANALYSIS);

        CheckIn checkIn = new CheckIn();
        checkIn.setId(1L);
        when(checkInRepository.findById(1L)).thenReturn(Optional.of(checkIn));

        int threadCount = 5;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final int partition = i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    consumer.consume(record(partition), ack);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS))
                .as("All threads must finish within 10 s").isTrue();
        pool.shutdown();

        // Only the lock-holder runs processMessage → findById called exactly once
        verify(checkInRepository, times(1)).findById(1L);
    }

    // ── Q3: Retry exhaustion + Dead Letter Queue ──────────────────────────────

    @Test
    @DisplayName("Q3: after MAX_RETRY failures the message is routed to the dead-letter topic")
    void consume_maxRetriesExhausted_sendsToDeadLetterTopic() throws Exception {
        when(setOps.isMember(anyString(), anyString())).thenReturn(false);
        when(rLock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);

        // Cache succeeds but DB write always fails — triggers retry loop
        when(cacheDefenseService.getWithMutex(anyString(), anyString(), any(),
                eq(CheckInConsumer.CachedAnalysis.class), anyLong()))
                .thenReturn(CACHED_ANALYSIS);
        when(checkInRepository.findById(any()))
                .thenThrow(new RuntimeException("DB unavailable"));

        // Exponential backoff: 1 s + 2 s ≈ 3 s total — expected for this test
        consumer.consume(record(0), ack);

        verify(kafkaTemplate).send(eq("checkin-events-dlt"), eq(MESSAGE));
        verify(ack).acknowledge();
        verify(rLock, times(3)).unlock();   // finally block fires on every failed attempt
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> record(int partition) {
        return new ConsumerRecord<>("checkin-events", partition, partition, "key", MESSAGE);
    }
}
