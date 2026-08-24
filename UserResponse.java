package com.elevenftw.dto;

import com.elevenftw.entity.User;
import com.elevenftw.entity.enums.ProvinceType;

public record UserResponse(
    Long id,
    String username,
    String email,
    String profilePhotoUrl,
    ProvinceType homeProvince,
    /** Null until they've received at least one rating — see UserService#getProfile. */
    Double reliabilityScore,
    Integer matchesPlayed
) {
    /** Lightweight variant used anywhere a user just needs to be identified (match cards, participant lists, auth responses) — no extra queries. */
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getEmail(),
                u.getProfilePhotoUrl(), u.getHomeProvince(), null, null);
    }

    public static UserResponse withStats(User u, Double reliabilityScore, Integer matchesPlayed) {
        return new UserResponse(u.getId(), u.getUsername(), u.getEmail(),
                u.getProfilePhotoUrl(), u.getHomeProvince(), reliabilityScore, matchesPlayed);
    }
}
