package com.elevenftw.controller;

import com.elevenftw.dto.AuthRequest;
import com.elevenftw.dto.AuthResponse;
import com.elevenftw.dto.ChangePasswordRequest;
import com.elevenftw.dto.ForgotPasswordRequest;
import com.elevenftw.dto.LoginRequest;
import com.elevenftw.dto.MessageResponse;
import com.elevenftw.dto.RefreshRequest;
import com.elevenftw.dto.RefreshResponse;
import com.elevenftw.dto.RegisterRequest;
import com.elevenftw.dto.ResendVerificationRequest;
import com.elevenftw.dto.ResetPasswordRequest;
import com.elevenftw.dto.VerifyEmailRequest;
import com.elevenftw.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/google")
    public AuthResponse loginWithGoogle(@Valid @RequestBody AuthRequest request) {
        return authService.loginWithGoogle(request.idToken());
    }

    @PostMapping("/register")
    public MessageResponse register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.email(), request.password());
        return new MessageResponse("Account created. Check your email to confirm your address before signing in.");
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/verify-email")
    public MessageResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return new MessageResponse("Email confirmed. You can now sign in.");
    }

    @PostMapping("/resend-verification")
    public MessageResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return new MessageResponse("If that email needs verifying, we've sent a new link.");
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        // Same response whether or not the email exists — see AuthService#forgotPassword.
        return new MessageResponse("If an account exists for that email, we've sent a reset link.");
    }

    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return new MessageResponse("Your password has been reset. You can now sign in.");
    }

    @PostMapping("/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public MessageResponse logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return new MessageResponse("Signed out.");
    }

    @PostMapping("/change-password")
    public MessageResponse changePassword(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.changePassword(userId, request.currentPassword(), request.newPassword());
        return new MessageResponse("Password updated.");
    }
}
