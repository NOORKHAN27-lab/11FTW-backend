package com.elevenftw.controller;

import com.elevenftw.dto.MessageResponse;
import com.elevenftw.dto.NotificationsResponse;
import com.elevenftw.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationsResponse list(@AuthenticationPrincipal Long userId) {
        return notificationService.getForUser(userId);
    }

    @PostMapping("/read-all")
    public MessageResponse markAllRead(@AuthenticationPrincipal Long userId) {
        notificationService.markAllRead(userId);
        return new MessageResponse("All caught up.");
    }
}
