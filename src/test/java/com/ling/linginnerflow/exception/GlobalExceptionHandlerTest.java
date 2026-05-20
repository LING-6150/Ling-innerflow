package com.ling.linginnerflow.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler.
 *
 * Tests each exception type independently — no Spring context needed
 * because we call the handler methods directly.
 *
 * Coverage:
 *   BusinessException subtypes → correct HTTP status + errorCode
 *   Catch-all Exception → 500, no internal detail leaked
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("DuplicateEmailException → 409 CONFLICT with DUPLICATE_EMAIL code")
    void duplicateEmail_returns409() {
        DuplicateEmailException ex = new DuplicateEmailException("bob@example.com");

        ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("errorCode", "DUPLICATE_EMAIL");
        assertThat(response.getBody()).containsEntry("code", 409);
        assertThat(response.getBody().get("message").toString())
                .contains("bob@example.com");
    }

    @Test
    @DisplayName("DuplicateUsernameException → 409 CONFLICT with DUPLICATE_USERNAME code")
    void duplicateUsername_returns409() {
        DuplicateUsernameException ex = new DuplicateUsernameException("bob");

        ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("errorCode", "DUPLICATE_USERNAME");
    }

    @Test
    @DisplayName("AuthenticationFailedException → 401 UNAUTHORIZED with AUTH_FAILED code")
    void authFailed_returns401() {
        AuthenticationFailedException ex = new AuthenticationFailedException();

        ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("errorCode", "AUTH_FAILED");
    }

    @Test
    @DisplayName("AccountDisabledException → 403 FORBIDDEN with ACCOUNT_DISABLED code")
    void accountDisabled_returns403() {
        AccountDisabledException ex = new AccountDisabledException();

        ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("errorCode", "ACCOUNT_DISABLED");
    }

    @Test
    @DisplayName("Generic Exception → 500 with no internal detail in message")
    void genericException_returns500_noLeak() {
        Exception ex = new Exception("DB connection string: jdbc:mysql://secret-host/db");

        ResponseEntity<Map<String, Object>> response = handler.handleException(ex);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().get("message").toString())
                .doesNotContain("secret-host")
                .doesNotContain("jdbc");
        assertThat(response.getBody()).containsEntry("errorCode", "INTERNAL_ERROR");
    }

    @Test
    @DisplayName("Response body always includes timestamp")
    void responseBody_alwaysHasTimestamp() {
        DuplicateEmailException ex = new DuplicateEmailException("x@y.com");

        ResponseEntity<Map<String, Object>> response = handler.handleBusiness(ex);

        assertThat(response.getBody()).containsKey("timestamp");
        assertThat(response.getBody().get("timestamp").toString()).isNotBlank();
    }
}
