package com.ling.linginnerflow.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Typed business exceptions — each carries its own HTTP status and error code. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException e) {
        log.warn("[Business] code={} message={}", e.getErrorCode(), e.getMessage());
        return ResponseEntity.status(e.getStatus()).body(errorBody(
                e.getStatus().value(), e.getErrorCode(), e.getMessage()));
    }

    /** @Valid / @Validated field validation failures → 400 with field-level detail. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("[Validation] {}", detail);
        return ResponseEntity.badRequest().body(
                errorBody(400, "VALIDATION_ERROR", detail));
    }

    /** Catch-all for any unhandled exception → 500, no internal detail leaked. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("[System] Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                errorBody(500, "INTERNAL_ERROR", "系统繁忙，请稍后再试"));
    }

    private Map<String, Object> errorBody(int code, String errorCode, String message) {
        return Map.of(
                "code", code,
                "errorCode", errorCode,
                "message", message,
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
