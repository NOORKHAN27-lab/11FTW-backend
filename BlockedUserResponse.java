package com.elevenftw.dto;

import com.elevenftw.entity.BlockedUser;

import java.time.Instant;

public record BlockedUserResponse(Long userId, String username, Instant blockedAt) {
    public static BlockedUserResponse from(BlockedUser b) {
        return new BlockedUserResponse(b.getBlocked().getId(), b.getBlocked().getUsername(), b.getCreatedAt());
    }
}
