package com.elevenftw.entity;

import com.elevenftw.entity.enums.*;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SportType sport;

    @Enumerated(EnumType.STRING)
    @Column(name = "football_format")
    private FootballFormat footballFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "cricket_format")
    private CricketFormat cricketFormat;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_gender", nullable = false)
    private GenderCategory categoryGender;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_age", nullable = false)
    private AgeCategory categoryAge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProvinceType province;

    @Column(name = "address_text", nullable = false, columnDefinition = "TEXT")
    private String addressText;

    private Double latitude;
    private Double longitude;

    @Column(name = "match_date", nullable = false)
    private LocalDate matchDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "max_players", nullable = false)
    private Integer maxPlayers;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_level", nullable = false)
    @Builder.Default
    private SkillLevel skillLevel = SkillLevel.ANY;

    /** e.g. "Rs. 500/head" — display only, the app never touches money. */
    @Column(name = "fee_text")
    private String feeText;

    /** Optional total ground fee — when set, the frontend auto-computes a per-head split from maxPlayers. */
    @Column(name = "total_fee_amount")
    private Integer totalFeeAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MatchStatus status = MatchStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
