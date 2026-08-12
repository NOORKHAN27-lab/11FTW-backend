package com.elevenftw.dto;

import com.elevenftw.entity.enums.*;

import java.time.LocalDate;

/** Query-param bundle for GET /api/matches. Every field is optional except the user's own location. */
public record MatchSearchFilters(
    ProvinceType province,
    SportType sport,
    GenderCategory categoryGender,
    AgeCategory categoryAge,
    LocalDate matchDate,
    SkillLevel skillLevel,
    String keyword,
    double userLat,
    double userLng,
    double minDistanceKm,
    double maxDistanceKm
) {}
