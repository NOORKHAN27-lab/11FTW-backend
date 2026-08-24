package com.elevenftw.service;

import com.elevenftw.dto.AuthResponse;
import com.elevenftw.dto.RefreshResponse;
import com.elevenftw.dto.UserResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final long RESET_TOKEN_TTL_MINUTES = 60;
    private static final long VERIFY_TOKEN_TTL_HOURS = 24;
    private static final int LOCKOUT_THRESHOLD = 5;
    private static final long LOCKOUT_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final long refreshTokenTtlMs;

    public AuthService(
        UserRepository userRepository,
        PasswordResetTokenRepository resetTokenRepository,
        EmailVerificationTokenRepository verificationTokenRepository,
        RefreshTokenRepository refreshTokenRepository,
        GoogleTokenVerifier googleTokenVerifier,
        JwtUtil jwtUtil,
        PasswordEncoder passwordEncoder,
        EmailService emailService,
        @Value("${app.refresh-token.expiration-ms:2592000000}") long refreshTokenTtlMs // default: 30 days
    ) {
        this.userRepository = userRepository;
        this.resetTokenRepository = resetTokenRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.googleTokenVerifier = googleTokenVerifier;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.refreshTokenTtlMs = refreshTokenTtlMs;
    }

    /**
     * Verifies the Google ID token, then either logs in an existing user or
     * creates a brand-new one. New users get a placeholder username/phone —
     * profileComplete=false tells the frontend to route them to /onboarding
     * to fill in a real username and WhatsApp number before they can post
     * or join anything. Google accounts are auto-verified since Google
     * already confirmed the email address.
     */
    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        GoogleTokenVerifier.GoogleUser googleUser = googleTokenVerifier.verify(idToken);
        if (googleUser == null) {
            throw new ForbiddenException("Invalid Google token");
        }

        User user = userRepository.findByGoogleId(googleUser.googleId())
                .orElseGet(() -> createGoogleUser(googleUser));

        return respondWithTokens(user);
    }

    /**
     * Creates a new email+password account. Deliberately does NOT log the
     * user in — they must verify their email first, then sign in normally
     * via login(). Returns nothing sensitive; the controller sends back a
     * generic "check your email" message.
     */
    @Transactional
    public void register(String email, String rawPassword) {
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                // Placeholder username (e.g. "player_482913") — user picks a real one during onboarding.
                .username("player_" + System.currentTimeMillis() % 1_000_000)
                .phoneNumber("")
                .emailVerified(false)
                .build();
        user = userRepository.save(user);

        issueVerificationEmail(user);
    }

    @Transactional
    public AuthResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ForbiddenException("Incorrect email or password."));

        if (user.isDeleted()) {
            throw new ForbiddenException("This account no longer exists.");
        }

        if (user.getPassword() == null) {
            throw new ForbiddenException("This email signed up with Google. Use \"Continue with Google\" instead.");
        }

        if (user.getLockedUntil() != null && Instant.now().isBefore(user.getLockedUntil())) {
            long minutesLeft = Math.max(1, Duration.between(Instant.now(), user.getLockedUntil()).toMinutes());
            throw new ForbiddenException("Too many failed attempts. Try again in " + minutesLeft + " minute(s).");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            registerFailedAttempt(user);
            throw new ForbiddenException("Incorrect email or password.");
        }

        // Successful password match — clear any lockout state.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        if (!user.isEmailVerified()) {
            throw new ForbiddenException("Please verify your email before signing in. Check your inbox, or request a new link.");
        }

        return respondWithTokens(user);
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= LOCKOUT_THRESHOLD) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION_MINUTES, ChronoUnit.MINUTES));
        }
        userRepository.save(user);
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = verificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new ForbiddenException("This verification link is invalid."));

        if (verificationToken.isUsed() || verificationToken.isExpired()) {
            throw new ForbiddenException("This verification link has expired. Request a new one.");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        verificationTokenRepository.save(verificationToken);
    }

    /** Same "always succeeds" shape as forgotPassword — doesn't reveal whether the email is registered. */
    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.isEmailVerified()) return;
            issueVerificationEmail(user);
        });
    }

    private void issueVerificationEmail(User user) {
        String token = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .token(token)
                .expiresAt(Instant.now().plus(VERIFY_TOKEN_TTL_HOURS, ChronoUnit.HOURS))
                .used(false)
                .build();
        verificationTokenRepository.save(verificationToken);

        try {
            emailService.sendVerificationEmail(user.getEmail(), token);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}", user.getEmail(), e);
        }
    }

    /**
     * Always succeeds from the caller's point of view — whether or not the
     * email exists, whether or not sending actually worked — so a bad actor
     * can't use this endpoint to discover which emails are registered.
     * Real failures (bad SMTP config, etc.) are logged, not surfaced.
     */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getPassword() == null) {
                log.info("Password reset requested for Google-only account: {}", email);
                return;
            }
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .token(token)
                    .expiresAt(Instant.now().plus(RESET_TOKEN_TTL_MINUTES, ChronoUnit.MINUTES))
                    .used(false)
                    .build();
            resetTokenRepository.save(resetToken);

            try {
                emailService.sendPasswordResetEmail(user.getEmail(), token);
            } catch (Exception e) {
                log.error("Failed to send password reset email to {}", email, e);
            }
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = resetTokenRepository.findByToken(token)
                .orElseThrow(() -> new ForbiddenException("This reset link is invalid or has already been used."));

        if (resetToken.isUsed() || resetToken.isExpired()) {
            throw new ForbiddenException("This reset link has expired. Request a new one.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);
    }

    /**
     * Changes a user's password. currentPassword is required unless the
     * account has never had one (a Google-only account setting a password
     * for the first time, so they can also sign in with email+password).
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenException("User not found"));

        if (user.getPassword() != null) {
            if (currentPassword == null || currentPassword.isBlank()
                    || !passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new ForbiddenException("Current password is incorrect.");
            }
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    /** Rotates a refresh token: the old one is revoked and a new access+refresh pair is issued. */
    @Transactional
    public RefreshResponse refresh(String refreshTokenStr) {
        RefreshToken existing = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new ForbiddenException("Session expired. Please sign in again."));

        if (!existing.isValid()) {
            throw new ForbiddenException("Session expired. Please sign in again.");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        User user = existing.getUser();
        String accessToken = jwtUtil.generateToken(user.getId());
        RefreshToken newRefreshToken = issueRefreshToken(user);

        return new RefreshResponse(accessToken, newRefreshToken.getToken());
    }

    @Transactional
    public void logout(String refreshTokenStr) {
        refreshTokenRepository.findByToken(refreshTokenStr).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    private User createGoogleUser(GoogleTokenVerifier.GoogleUser googleUser) {
        User user = User.builder()
                .googleId(googleUser.googleId())
                .email(googleUser.email())
                .username("player_" + System.currentTimeMillis() % 1_000_000)
                .phoneNumber("")
                .profilePhotoUrl(googleUser.pictureUrl())
                .emailVerified(true)
                .build();
        return userRepository.save(user);
    }

    private RefreshToken issueRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plusMillis(refreshTokenTtlMs))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    private AuthResponse respondWithTokens(User user) {
        String accessToken = jwtUtil.generateToken(user.getId());
        RefreshToken refreshToken = issueRefreshToken(user);
        boolean profileComplete = !user.getUsername().startsWith("player_");
        return new AuthResponse(accessToken, refreshToken.getToken(), profileComplete, UserResponse.from(user));
    }
}
