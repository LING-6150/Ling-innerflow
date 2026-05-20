package com.ling.linginnerflow.user;

import com.ling.linginnerflow.exception.AccountDisabledException;
import com.ling.linginnerflow.exception.AuthenticationFailedException;
import com.ling.linginnerflow.exception.DuplicateEmailException;
import com.ling.linginnerflow.exception.DuplicateUsernameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthService unit tests — verifies authentication business rules
 * using Mockito mocks for all external dependencies.
 *
 * Coverage:
 *   Register: happy path, duplicate email, duplicate username
 *   Login:    happy path, wrong email, wrong password, disabled account
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User activeUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("alice");
        registerRequest.setEmail("alice@example.com");
        registerRequest.setPassword("P@ssword1");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("alice@example.com");
        loginRequest.setPassword("P@ssword1");

        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setUsername("alice");
        activeUser.setEmail("alice@example.com");
        activeUser.setPassword("hashed");
        activeUser.setEnabled(true);
    }

    // ── Register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Register: happy path returns token and user info")
    void register_success() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("P@ssword1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(activeUser);
        when(jwtService.generateToken(activeUser)).thenReturn("jwt-token");

        AuthResponse response = authService.register(registerRequest);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("Register: duplicate email throws DuplicateEmailException (409)")
    void register_duplicateEmail_throws() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("alice@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Register: duplicate username throws DuplicateUsernameException (409)")
    void register_duplicateUsername_throws() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(DuplicateUsernameException.class)
                .hasMessageContaining("alice");

        verify(userRepository, never()).save(any());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Login: happy path returns a JWT token")
    void login_success() {
        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("P@ssword1", "hashed")).thenReturn(true);
        when(jwtService.generateToken(activeUser)).thenReturn("jwt-token");

        AuthResponse response = authService.login(loginRequest);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getUserId()).isEqualTo("1");
    }

    @Test
    @DisplayName("Login: unknown email throws AuthenticationFailedException (401)")
    void login_unknownEmail_throws() {
        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("Login: wrong password throws AuthenticationFailedException (401)")
    void login_wrongPassword_throws() {
        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("P@ssword1", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(AuthenticationFailedException.class);

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("Login: disabled account throws AccountDisabledException (403)")
    void login_disabledAccount_throws() {
        activeUser.setEnabled(false);
        when(userRepository.findByEmail("alice@example.com"))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("P@ssword1", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(AccountDisabledException.class);

        verify(jwtService, never()).generateToken(any());
    }
}
