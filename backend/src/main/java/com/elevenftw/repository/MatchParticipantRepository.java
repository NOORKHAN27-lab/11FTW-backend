package com.elevenftw.repository;

import com.elevenftw.entity.MatchParticipant;
import com.elevenftw.entity.enums.ParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, Long> {
    long countByMatchId(Long matchId);
    long countByMatchIdAndStatus(Long matchId, ParticipantStatus status);
    boolean existsByMatchIdAndUserId(Long matchId, Long userId);
    Optional<MatchParticipant> findByMatchIdAndUserId(Long matchId, Long userId);
    List<MatchParticipant> findByMatchId(Long matchId);
    List<MatchParticipant> findByUserId(Long userId);
    /** Earliest-joined waitlisted entry — who gets promoted first when a confirmed spot frees up. */
    Optional<MatchParticipant> findFirstByMatchIdAndStatusOrderByJoinedAtAsc(Long matchId, ParticipantStatus status);
    /** One row per match per user (unique constraint), so this doubles as "distinct matches confirmed in". */
    long countByUserIdAndStatus(Long userId, ParticipantStatus status);
}
