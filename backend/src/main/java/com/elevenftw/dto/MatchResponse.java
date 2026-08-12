package com.elevenftw.dto;

import com.elevenftw.entity.Match;
import com.elevenftw.entity.enums.*;

import java.time.LocalDate;
import java.time.LocalTime;

public record MatchResponse(
    Long id,
    UserResponse createdBy,
    SportType sport,
    FootballFormat footballFormat,
    CricketFormat cricketFormat,
    GenderCategory categoryGender,
    AgeCategory categoryAge,
    SkillLevel skillLevel,
    ProvinceType province,
    String addressText,
    /** True once this exact ground address has appeared in several past matches — see MatchService#toResponse. */
    boolean verifiedGround,
    LocalDate matchDate,
    LocalTime startTime,
    LocalTime endTime,
    Integer maxPlayers,
    Integer spotsFilled,
    Integer waitlistCount,
    /** True only when the requester themself is on the waitlist (not confirmed) for this match. */
    boolean requesterWaitlisted,
    String feeText,
    Integer totalFeeAmount,
    MatchStatus status,
    Double distanceKm,
    /** Only populated once the requester has joined — see MatchService. */
    String creatorContactNumber
) {
    public static MatchResponse from(
        Match m, int spotsFilled, int waitlistCount, boolean requesterWaitlisted,
        boolean verifiedGround, Double distanceKm, String contact
    ) {
        return new MatchResponse(
            m.getId(),
            UserResponse.from(m.getCreatedBy()),
            m.getSport(), m.getFootballFormat(), m.getCricketFormat(),
            m.getCategoryGender(), m.getCategoryAge(), m.getSkillLevel(), m.getProvince(),
            m.getAddressText(), verifiedGround, m.getMatchDate(), m.getStartTime(), m.getEndTime(),
            m.getMaxPlayers(), spotsFilled, waitlistCount, requesterWaitlisted,
            m.getFeeText(), m.getTotalFeeAmount(), m.getStatus(),
            distanceKm, contact
        );
    }
}
