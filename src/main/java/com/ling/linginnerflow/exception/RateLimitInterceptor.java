package com.ling.linginnerflow.exception;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流拦截器
 * 基于用户IP限流，防止OpenAI费用爆炸
 *
 * 规则：
 * - /api/emotion/analyze：每分钟最多10次
 * - /api/checkin：每分钟最多5次
 * - 其他接口：每分钟最多30次
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    // IP → Bucket映射，每个IP独立限流
    private final Map<String, Bucket> emotionBuckets =
            new ConcurrentHashMap<>();
    private final Map<String, Bucket> checkinBuckets =
            new ConcurrentHashMap<>();
    private final Map<String, Bucket> defaultBuckets =
            new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response, Object handler)
            throws Exception {

        String ip = getClientIp(request);
        String path = request.getRequestURI();

        Bucket bucket = getBucket(ip, path);

        if (bucket.tryConsume(1)) {
            return true;
        }

        // 限流触发
        log.warn("限流触发: ip={}, path={}", ip, path);
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":429,\"message\":\"请求太频繁，请稍后再试\"}"
        );
        return false;
    }

    private Bucket getBucket(String ip, String path) {
        if (path.contains("/api/emotion/analyze")) {
            // 情绪分析：每分钟10次
            return emotionBuckets.computeIfAbsent(ip,
                    k -> buildBucket(10, Duration.ofMinutes(1)));
        } else if (path.contains("/api/checkin")) {
            // 打卡：每分钟5次
            return checkinBuckets.computeIfAbsent(ip,
                    k -> buildBucket(5, Duration.ofMinutes(1)));
        } else {
            // 其他：每分钟30次
            return defaultBuckets.computeIfAbsent(ip,
                    k -> buildBucket(30, Duration.ofMinutes(1)));
        }
    }

    private Bucket buildBucket(int capacity, Duration duration) {
        Bandwidth limit = Bandwidth.classic(capacity,
                Refill.greedy(capacity, duration));
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}