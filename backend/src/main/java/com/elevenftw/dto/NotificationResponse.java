package com.elevenftw.dto;

import com.elevenftw.entity.Notification;
import com.elevenftw.entity.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(
    Long id,
    NotificationType type,
    String message,
    Long relatedMatchId,
    boolean read,
    Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getMessage(), n.getRelatedMatchId(), n.isRead(), n.getCreatedAt());
    }
}
