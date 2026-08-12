package com.elevenftw.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body for POST /api/auth/forgot-password. */
public record ForgotPasswordRequest(
    @NotBlank @Email String email
) {}
