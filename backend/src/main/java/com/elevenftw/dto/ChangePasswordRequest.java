package com.elevenftw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * currentPassword is optional — null/blank is only accepted for a
 * Google-only account setting a password for the first time. An account
 * that already has a password must supply the correct current one.
 */
public record ChangePasswordRequest(
    String currentPassword,
    @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String newPassword
) {}
