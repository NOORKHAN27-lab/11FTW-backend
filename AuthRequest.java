package com.elevenftw.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for POST /api/auth/google — the ID token React gets back from Google Sign-In. */
public record AuthRequest(
    @NotBlank String idToken
) {}
