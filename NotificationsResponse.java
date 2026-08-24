package com.elevenftw.dto;

import java.util.List;

public record NotificationsResponse(List<NotificationResponse> notifications, long unreadCount) {}
