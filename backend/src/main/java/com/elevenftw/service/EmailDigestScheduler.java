package com.elevenftw.service;

import com.elevenftw.entity.Match;
import com.elevenftw.entity.User;
import com.elevenftw.repository.MatchRepository;
import com.elevenftw.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Every Monday morning, emails each verified user a short digest of new
 * matches posted in their home province over the last 7 days. Users without
 * a home province set (or without a verified email) are skipped — this is
 * genuinely opt-in-by-profile-completeness, not a dark pattern to work
 * around.
 *
 * Digest granularity is by province, not precise geolocation — home lat/lng
 * isn't collected anywhere in this app (see Onboarding), so this is the
 * coarsest reasonable signal available without adding a new data collection
 * point just for this feature.
 */
@Component
public class EmailDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(EmailDigestScheduler.class);

    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final EmailService emailService;

    public EmailDigestScheduler(UserRepository userRepository, MatchRepository matchRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.emailService = emailService;
    }

    @Scheduled(cron = "${app.digest.cron:0 0 9 * * MON}")
    public void sendWeeklyDigest() {
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        int sent = 0;

        for (User user : userRepository.findAll()) {
            if (user.isDeleted() || !user.isEmailVerified() || user.getHomeProvince() == null) continue;

            List<Match> recent = matchRepository.findTop5ByProvinceAndCreatedAtAfterOrderByCreatedAtDesc(
                    user.getHomeProvince(), weekAgo);
            if (recent.isEmpty()) continue;

            try {
                emailService.sendWeeklyDigest(user.getEmail(), recent);
                sent++;
            } catch (Exception e) {
                // One user's bad/unreachable inbox shouldn't stop the rest of the run.
                log.warn("Failed to send weekly digest to user {}", user.getId(), e);
            }
        }

        log.info("Weekly digest run complete — sent to {} user(s)", sent);
    }
}
