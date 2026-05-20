package com.ling.linginnerflow.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ling.linginnerflow.exception.AccountDisabledException;
import com.ling.linginnerflow.exception.AuthenticationFailedException;
import com.ling.linginnerflow.exception.DuplicateEmailException;
import com.ling.linginnerflow.exception.DuplicateUsernameException;
import com.ling.linginnerflow.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-layer tests for AuthController.
 *
 * Uses @WebMvcTest (web slice only — no JPA, Kafka, Redis etc.).
 * SecurityConfig is loaded normally so /api/auth/** permit-all behaviour is
 * also verified. JwtService is mocked to satisfy JwtAuthFilter's dependency
 * without running real token validation.
 *
 * GlobalExceptionHandler is imported explicitly so the exception→HTTP-status
 * mapping is tested end-to-end through the real handler.
 *
 * Coverage:
 *   Register — success (200), duplicate email (409), duplicate username (409)
 *   Login    — success (200), wrong credentials (401), disabled account (403)
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    // JwtAuthFilter (@Component) is auto-detected by @WebMvcTest; JwtService
    // must be mocked so the filter can be instantiated without a real JWT secret.
    @MockBean JwtService jwtService;

    // ── Register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register → 200 with token and user info")
    void register_success_returns200() throws Exception {
        AuthResponse response = new AuthResponse(
                "jwt-token", "42", "alice", "alice@example.com");
        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("alice", "alice@example.com", "P@ssword1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/register with duplicate email → 409 DUPLICATE_EMAIL")
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(any()))
                .thenThrow(new DuplicateEmailException("alice@example.com"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("alice", "alice@example.com", "P@ssword1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("alice@example.com")));
    }

    @Test
    @DisplayName("POST /api/auth/register with duplicate username → 409 DUPLICATE_USERNAME")
    void register_duplicateUsername_returns409() throws Exception {
        when(authService.register(any()))
                .thenThrow(new DuplicateUsernameException("alice"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("alice", "new@example.com", "P@ssword1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_USERNAME"));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login → 200 with token")
    void login_success_returns200() throws Exception {
        AuthResponse response = new AuthResponse(
                "jwt-token", "42", "alice", "alice@example.com");
        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("alice@example.com", "P@ssword1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.userId").value("42"));
    }

    @Test
    @DisplayName("POST /api/auth/login with wrong credentials → 401 AUTH_FAILED")
    void login_wrongCredentials_returns401() throws Exception {
        when(authService.login(any()))
                .thenThrow(new AuthenticationFailedException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("alice@example.com", "wrong")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_FAILED"));
    }

    @Test
    @DisplayName("POST /api/auth/login with disabled account → 403 ACCOUNT_DISABLED")
    void login_disabledAccount_returns403() throws Exception {
        when(authService.login(any()))
                .thenThrow(new AccountDisabledException());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("alice@example.com", "P@ssword1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String registerJson(String username, String email, String password) {
        return """
                {"username":"%s","email":"%s","password":"%s"}
                """.formatted(username, email, password);
    }

    private String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }
}
