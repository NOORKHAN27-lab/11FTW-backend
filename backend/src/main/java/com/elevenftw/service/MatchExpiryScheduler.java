package com.elevenftw.service;

import com.elevenftw.entity.Match;
import com.elevenftw.entity.enums.MatchStatus;
import com.elevenftw.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Runs every minute, moves any match whose end_time has passed from
 * OPEN/FULL to EXPIRED. Expired matches are filtered out of search results
 * (MatchRepository#search only looks at OPEN), so this is what makes a
 * "5pm-6pm" post disappear from the feed right after 6pm, exactly as
 * specified. Rows are kept (not deleted) so "my past matches" is free to
 * build later — see technical_design.md §1.6.
 */
@Component
public class MatchExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchExpiryScheduler.class);

    private final MatchRepository matchRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public MatchExpiryScheduler(MatchRepository matchRepository, SimpMessagingTemplate messagingTemplate) {
        this.matchRepository = matchRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRate = 60_000) // every 60 seconds
    @Transactional
    public void expireOldMatches() {
        List<Match> expired = matchRepository.findExpired(LocalDate.now(), LocalTime.now());
        if (expired.isEmpty()) return;

        for (Match match : expired) {
            match.setStatus(MatchStatus.EXPIRED);
            messagingTemplate.convertAndSend(
                    "/topic/matches/" + match.getId(),
                    Map.of("matchId", match.getId(), "status", "EXPIRED")
            );
        }
        matchRepository.saveAll(expired);
        log.info("Auto-expired {} match(es)", expired.size());
    }
}
