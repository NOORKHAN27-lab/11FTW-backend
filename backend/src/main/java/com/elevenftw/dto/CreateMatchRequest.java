package com.elevenftw.dto;

import com.elevenftw.entity.enums.*;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateMatchRequest(
    @NotNull SportType sport,
    FootballFormat footballFormat,   // required if sport = FOOTBALL
    CricketFormat cricketFormat,     // required if sport = CRICKET
    @NotNull GenderCategory categoryGender,
    @NotNull AgeCategory categoryAge,
    @NotNull SkillLevel skillLevel,
    @NotNull ProvinceType province,
    @NotBlank String addressText,
    @NotNull LocalDate matchDate,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @NotNull @Min(1) @Max(50) Integer maxPlayers,
    String feeText,
    @Min(0) Integer totalFeeAmount,
    /** Set true to bypass the duplicate-post warning after the user confirms they meant to post again. */
    Boolean confirmDuplicate
) {}
