package com.elevenftw.service;

import com.elevenftw.dto.NotificationResponse;
import com.elevenftw.dto.NotificationsResponse;
import com.elevenftw.entity.Notification;
import com.elevenftw.entity.User;
import com.elevenftw.entity.enums.NotificationType;
import com.elevenftw.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** Fire-and-forget from MatchService — never let a notification failure break the join/leave flow it's attached to. */
    @Transactional
    public void notify(User user, NotificationType type, String message, Long relatedMatchId) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .message(message)
                .relatedMatchId(relatedMatchId)
                .read(false)
                .build();
        notificationRepository.save(notification);
    }

    public NotificationsResponse getForUser(Long userId) {
        var list = notificationRepository.findTop30ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
        long unread = notificationRepository.countByUserIdAndReadFalse(userId);
        return new NotificationsResponse(list, unread);
    }

    @Transactional
    public void markAllRead(Long userId) {
        var unread = notificationRepository.findByUserIdAndReadFalse(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
    }
}
