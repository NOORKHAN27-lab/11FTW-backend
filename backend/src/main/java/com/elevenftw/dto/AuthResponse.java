package com.elevenftw.dto;

public record AuthResponse(
    String token,
    String refreshToken,
    boolean profileComplete,
    UserResponse user
) {}
