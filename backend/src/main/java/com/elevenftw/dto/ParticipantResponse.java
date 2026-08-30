package com.elevenftw.dto;

import com.elevenftw.entity.MatchParticipant;
import com.elevenftw.entity.enums.ParticipantStatus;

import java.time.Instant;

public record ParticipantResponse(UserResponse user, ParticipantStatus status, Instant joinedAt) {
    public static ParticipantResponse from(MatchParticipant p) {
        return new ParticipantResponse(UserResponse.from(p.getUser()), p.getStatus(), p.getJoinedAt());
    }
}
