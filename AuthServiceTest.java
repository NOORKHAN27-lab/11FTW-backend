package com.elevenftw.service;

import com.elevenftw.dto.AuthResponse;
import com.elevenftw.entity.EmailVerificationToken;
import com.elevenftw.entity.PasswordResetToken;
import com.elevenftw.entity.RefreshToken;
import com.elevenftw.entity.User;
import com.elevenftw.exception.ConflictException;
import com.elevenftw.exception.ForbiddenException;
import com.elevenftw.repository.EmailVerificationTokenRepository;
import com.elevenftw.repository.PasswordResetTokenRepository;
import com.elevenftw.repository.RefreshTokenRepository;
import com.elevenftw.repository.UserRepository;
import com.elevenftw.security.GoogleTokenVerifier;
import com.elevenftw.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the security-critical paths in AuthService: registration
 * conflicts, password verification, account lockout after repeated failed
 * attempts, and the email-verification gate on login. Pure Mockito (no
 * Spring context) — fast, and exercises exactly the logic that matters here
 * without needing a real database.
 */
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private EmailVerificationTokenRepository verificationTokenRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private GoogleTokenVerifier googleTokenVerifier;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthService(
            userRepository, resetTokenRepository, verificationTokenRepository,
            refreshTokenRepository, googleTokenVerifier, jwtUtil, passwordEncoder,
            emailService, 2_592_000_000L
        );
    }

    private User verifiedUserWithPassword(String hashedPassword) {
        return User.builder()
                .id(1L)
                .email("player@example.com")
                .username("noor")
                .phoneNumber("03001234567")
                .password(hashedPassword)
                .emailVerified(true)
                .failedLoginAttempts(0)
                .build();
    }

    @Test
    void register_throwsConflict_whenEmailAlreadyExists() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () ->
            authService.register("taken@example.com", "password123"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_sendsVerificationEmail_forNewAccount() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        authService.register("new@example.com", "password123");

        verify(emailService).sendVerificationEmail(eq("new@example.com"), anyString());
    }

    @Test
    void login_throwsForbidden_whenPasswordWrong() {
        User user = verifiedUserWithPassword("hashed");
        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(ForbiddenException.class, () ->
            authService.login("player@example.com", "wrong"));

        assertEquals(1, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }

    @Test
    void login_locksAccount_afterFiveFailedAttempts() {
        User user = verifiedUserWithPassword("hashed");
        user.setFailedLoginAttempts(4); // one more failure should trip the lock
        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(anyString(), eq("hashed"))).thenReturn(false);

        assertThrows(ForbiddenException.class, () ->
            authService.login("player@example.com", "wrong"));

        assertEquals(5, user.getFailedLoginAttempts());
        assertNotNull(user.getLockedUntil());
        assertTrue(user.getLockedUntil().isAfter(Instant.now()));
    }

    @Test
    void login_rejected_whileAccountLocked_evenWithCorrectPassword() {
        User user = verifiedUserWithPassword("hashed");
        user.setLockedUntil(Instant.now().plusSeconds(600));
        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class, () ->
            authService.login("player@example.com", "correct-password"));

        // Locked out before password is even checked.
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_rejected_whenEmailNotVerified() {
        User user = verifiedUserWithPassword("hashed");
        user.setEmailVerified(false);
        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
            authService.login("player@example.com", "correct"));

        assertTrue(ex.getMessage().toLowerCase().contains("verify"));
    }

    @Test
    void login_succeeds_andIssuesTokens_forVerifiedAccountWithCorrectPassword() {
        User user = verifiedUserWithPassword("hashed");
        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(jwtUtil.generateToken(1L)).thenReturn("fake-access-token");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login("player@example.com", "correct");

        assertEquals("fake-access-token", response.token());
        assertNotNull(response.refreshToken());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNull(user.getLockedUntil());
    }

    @Test
    void login_throwsForbidden_forGoogleOnlyAccount() {
        User user = verifiedUserWithPassword(null); // no password hash — Google-only account
        when(userRepository.findByEmail("player@example.com")).thenReturn(Optional.of(user));

        ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
            authService.login("player@example.com", "anything"));

        assertTrue(ex.getMessage().toLowerCase().contains("google"));
    }

    @Test
    void resetPassword_throwsForbidden_whenTokenExpired() {
        User user = verifiedUserWithPassword("old-hash");
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .token("expired-token")
                .expiresAt(Instant.now().minusSeconds(60)) // already expired
                .used(false)
                .build();
        when(resetTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

        assertThrows(ForbiddenException.class, () ->
            authService.resetPassword("expired-token", "newpassword123"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmail_marksUserVerified_forValidToken() {
        User user = verifiedUserWithPassword("hashed");
        user.setEmailVerified(false);
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user)
                .token("valid-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .used(false)
                .build();
        when(verificationTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(token));

        authService.verifyEmail("valid-token");

        assertTrue(user.isEmailVerified());
        assertTrue(token.isUsed());
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertTrue(savedUser.getValue().isEmailVerified());
    }
}
