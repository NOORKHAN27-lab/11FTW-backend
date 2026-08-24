package com.elevenftw.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record RateMatchRequest(@NotEmpty @Valid List<RatePlayerEntry> ratings) {}
