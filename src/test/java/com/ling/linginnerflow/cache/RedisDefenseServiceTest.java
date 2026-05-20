package com.ling.linginnerflow.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.ling.linginnerflow.cache.RedisDefenseService.NULL_SENTINEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Redis three-defence service.
 *
 * ── 防穿透 (Penetration) ──────────────────────────────────────────────
 *   P1. Null DB result → NULL_SENTINEL written to Redis
 *   P2. NULL_SENTINEL in Redis → returns null, loader never called
 *
 * ── 防雪崩 (Avalanche) ───────────────────────────────────────────────
 *   A1. jitter stays within ±20% of baseTtl (verified over 200 iterations)
 *   A2. jitter produces different values, not a constant
 *
 * ── 防击穿 (Breakdown / Mutex) ────────────────────────────────────────
 *   B1. Cache hit on fast path → no lock attempt, no loader call
 *   B2. Cache miss → lock acquired → loader called once → value cached
 *   B3. Double-check: another thread populates cache while we wait for lock
 *       → loader NOT called after lock is acquired
 *   B4. Lock contention: spin → cache populated by another thread → returns it
 *   B5. Spin timeout → degrades to direct loader call (no panic, no error)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisDefenseServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RedisDefenseService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new RedisDefenseService(redisTemplate, objectMapper);
    }

    // ── 防穿透 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("P1: null loader result → NULL_SENTINEL cached with short TTL")
    void getOrLoad_nullResult_cachesSentinel() {
        when(valueOps.get("key")).thenReturn(null);

        String result = service.getOrLoad("key", () -> null, String.class, 30);

        assertThat(result).isNull();
        verify(valueOps).set(eq("key"), eq(NULL_SENTINEL), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("P2: NULL_SENTINEL in cache → returns null without calling loader")
    void getOrLoad_sentinelInCache_returnsNullWithoutLoader() {
        when(valueOps.get("key")).thenReturn(NULL_SENTINEL);
        Supplier<String> loader = mock(Supplier.class);

        String result = service.getOrLoad("key", loader, String.class, 30);

        assertThat(result).isNull();
        verify(loader, never()).get();
    }

    // ── 防雪崩 ────────────────────────────────────────────────────────────────

    @RepeatedTest(200)
    @DisplayName("A1: jitter(60) always stays within ±20% of base (48–72 min)")
    void jitter_alwaysWithinTwentyPercentOfBase() {
        long result = service.jitter(60);
        assertThat(result)
                .as("jitter(60) must be in [48, 72]")
                .isBetween(48L, 72L);
    }

    @Test
    @DisplayName("A2: repeated jitter calls produce different values (not a constant)")
    void jitter_distributesValues() {
        long first = service.jitter(60);
        boolean foundDifferent = false;
        for (int i = 0; i < 50; i++) {
            if (service.jitter(60) != first) {
                foundDifferent = true;
                break;
            }
        }
        assertThat(foundDifferent)
                .as("50 jitter calls must not all return the same value")
                .isTrue();
    }

    // ── 防击穿 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("B1: cache hit on fast path — no lock attempt, no loader call")
    void getWithMutex_cacheHit_noLockAndNoLoader() throws Exception {
        when(valueOps.get("key")).thenReturn("{\"name\":\"alice\",\"score\":99}");
        Supplier<TestResult> loader = mock(Supplier.class);

        TestResult result = service.getWithMutex("key", "lock:key", loader, TestResult.class, 30);

        assertThat(result.getName()).isEqualTo("alice");
        assertThat(result.getScore()).isEqualTo(99);
        verify(valueOps, never()).setIfAbsent(any(), any(), anyLong(), any());
        verify(loader, never()).get();
    }

    @Test
    @DisplayName("B2: cache miss → lock acquired → loader called once → value written to cache")
    void getWithMutex_cacheMiss_acquiresLockAndCallsLoader() throws Exception {
        when(valueOps.get("key")).thenReturn(null);
        when(valueOps.setIfAbsent(eq("lock:key"), eq("1"), anyLong(), any()))
                .thenReturn(true);

        TestResult loaded = new TestResult("bob", 42);
        Supplier<TestResult> loader = () -> loaded;

        TestResult result = service.getWithMutex("key", "lock:key", loader, TestResult.class, 30);

        assertThat(result.getName()).isEqualTo("bob");
        // Value should be serialized and written to Redis
        verify(valueOps).set(eq("key"), contains("\"name\":\"bob\""), anyLong(), eq(TimeUnit.MINUTES));
        verify(redisTemplate).delete("lock:key");
    }

    @Test
    @DisplayName("B3: double-check — cache populated while waiting → loader NOT called")
    void getWithMutex_doubleCheck_skipsLoaderWhenCacheAlreadyPopulated() throws Exception {
        // First read: cache miss; after lock acquired (double-check): cache hit
        when(valueOps.get("key"))
                .thenReturn(null)                                    // fast path miss
                .thenReturn("{\"name\":\"charlie\",\"score\":7}");  // double-check hit

        when(valueOps.setIfAbsent(eq("lock:key"), eq("1"), anyLong(), any()))
                .thenReturn(true);

        AtomicInteger loaderCalls = new AtomicInteger(0);
        Supplier<TestResult> loader = () -> {
            loaderCalls.incrementAndGet();
            return new TestResult("charlie", 7);
        };

        TestResult result = service.getWithMutex("key", "lock:key", loader, TestResult.class, 30);

        assertThat(result.getName()).isEqualTo("charlie");
        assertThat(loaderCalls.get())
                .as("Loader must not be called when double-check finds a cached value")
                .isZero();
    }

    @Test
    @DisplayName("B4: spin — another thread populates cache while we wait for lock")
    void getWithMutex_spinThenCachePopulated_returnsValueWithoutLoader() throws Exception {
        // Fast path: miss; lock: not acquired (held by another thread);
        // After first spin: cache populated by the other thread
        when(valueOps.get("key"))
                .thenReturn(null)                                // fast path: miss
                .thenReturn("{\"name\":\"dave\",\"score\":5}"); // after spin: hit

        when(valueOps.setIfAbsent(eq("lock:key"), eq("1"), anyLong(), any()))
                .thenReturn(false);   // lock always held by "another thread"

        Supplier<TestResult> loader = mock(Supplier.class);

        TestResult result = service.getWithMutex("key", "lock:key", loader, TestResult.class, 30);

        assertThat(result.getName()).isEqualTo("dave");
        verify(loader, never()).get();
    }

    @Test
    @DisplayName("B5: spin timeout → degrades gracefully to direct loader call")
    void getWithMutex_spinTimeout_degradesGracefully() throws Exception {
        when(valueOps.get(anyString())).thenReturn(null);     // cache always empty
        when(valueOps.setIfAbsent(any(), any(), anyLong(), any()))
                .thenReturn(false);   // lock always held — forces full spin

        TestResult fallback = new TestResult("fallback", 0);
        Supplier<TestResult> loader = () -> fallback;

        // MAX_SPIN=10 × 50ms = 500ms — acceptable for a unit test
        TestResult result = service.getWithMutex("key", "lock:key", loader, TestResult.class, 30);

        assertThat(result.getName()).isEqualTo("fallback");
    }

    // ── helper ────────────────────────────────────────────────────────────────

    @Data @NoArgsConstructor @AllArgsConstructor
    static class TestResult {
        private String name;
        private int score;
    }
}
