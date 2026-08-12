package com.elevenftw.entity;

import com.elevenftw.entity.enums.ParticipantStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Who has joined which match. The unique constraint on (match_id, user_id)
 * is what makes double-joining structurally impossible — see the
 * concurrency note in MatchService#joinMatch.
 */
@Entity
@Table(
    name = "match_participants",
    uniqueConstraints = @UniqueConstraint(columnNames = {"match_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    /** CONFIRMED counts toward maxPlayers; WAITLISTED is promoted automatically when a confirmed spot frees up. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ParticipantStatus status = ParticipantStatus.CONFIRMED;

    @PrePersist
    void onCreate() {
        this.joinedAt = Instant.now();
    }
}
