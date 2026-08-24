package com.elevenftw.dto;

import jakarta.validation.constraints.NotNull;

public record RatePlayerEntry(@NotNull Long ratedUserId, boolean attended, boolean punctual) {}
