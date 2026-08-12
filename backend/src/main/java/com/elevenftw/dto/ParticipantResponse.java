package com.elevenftw.dto;

import com.elevenftw.entity.MatchParticipant;
import com.elevenftw.entity.enums.ParticipantStatus;

public record ParticipantResponse(UserResponse user, ParticipantStatus status) {
    public static ParticipantResponse from(MatchParticipant p) {
        return new ParticipantResponse(UserResponse.from(p.getUser()), p.getStatus());
    }
}
