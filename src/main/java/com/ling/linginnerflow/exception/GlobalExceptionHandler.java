package com.ling.linginnerflow.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 全局异常处理
 * 统一所有接口的报错格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常（如注册时邮箱已存在）
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of(
                "code", 400,
                "message", e.getMessage(),
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * 所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(
            Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "code", 500,
                        "message", "系统繁忙，请稍后再试",
                        "timestamp", LocalDateTime.now().toString()
                ));
    }
}