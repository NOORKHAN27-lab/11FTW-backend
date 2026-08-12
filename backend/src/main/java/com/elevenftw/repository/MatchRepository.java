package com.elevenftw.repository;

import com.elevenftw.entity.Match;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    /** Used for User#matchesPlayed — matches this user has posted. */
    long countByCreatedById(Long userId);

    /** "My Matches" — Posted tab, most recent first. */
    List<Match> findByCreatedByIdOrderByMatchDateDesc(Long userId);

    /** Ground-verified badge: this address has been used enough times to trust it's a real, known venue. */
    long countByAddressTextIgnoreCase(String addressText);

    /** Duplicate-post detection — same creator, sport, date and address, not yet cancelled/expired. */
    Optional<Match> findFirstByCreatedByIdAndSportAndMatchDateAndAddressTextIgnoreCaseAndStatusIn(
        Long createdById,
        com.elevenftw.entity.enums.SportType sport,
        java.time.LocalDate matchDate,
        String addressText,
        List<com.elevenftw.entity.enums.MatchStatus> statuses
    );

    /** Weekly email digest — newest matches posted in a province since the last run. */
    List<Match> findTop5ByProvinceAndCreatedAtAfterOrderByCreatedAtDesc(
        com.elevenftw.entity.enums.ProvinceType province, java.time.Instant after
    );

    /**
     * Row-level lock on a single match, held for the rest of the current
     * transaction. This is what actually makes joinMatch() concurrency-safe:
     * if two requests hit "join the last spot" at the same instant, the
     * second one blocks here until the first transaction commits or rolls
     * back — so the count-then-insert in MatchService#joinMatch can never
     * see a stale count. See technical_design.md §1.3.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Match m WHERE m.id = :id")
    Optional<Match> findByIdForUpdate(@Param("id") Long id);

    /** Used by MatchExpiryScheduler — matches still OPEN/FULL whose end time has passed. */
    @Query("""
        SELECT m FROM Match m
        WHERE m.status IN (com.elevenftw.entity.enums.MatchStatus.OPEN, com.elevenftw.entity.enums.MatchStatus.FULL)
          AND (m.matchDate < :today OR (m.matchDate = :today AND m.endTime <= :now))
        """)
    java.util.List<Match> findExpired(@Param("today") java.time.LocalDate today, @Param("now") java.time.LocalTime now);

    /**
     * Search matches with optional filters, plus a Haversine distance
     * calculation against the requesting user's own lat/lng. Any filter
     * param passed as null is simply skipped (the "(:param IS NULL OR ...)"
     * pattern below) — so the frontend can send only the filters the user
     * actually set. `keyword` does a simple case-insensitive substring
     * match against the ground/venue address text.
     *
     * See technical_design.md §4 for why Haversine-in-SQL is fine for MVP
     * scale, with a PostGIS upgrade path later if needed.
     */
    @Query(value = """
        SELECT m.*,
            (6371 * acos(
                cos(radians(:userLat)) * cos(radians(m.latitude)) *
                cos(radians(m.longitude) - radians(:userLng)) +
                sin(radians(:userLat)) * sin(radians(m.latitude))
            )) AS distance_km
        FROM matches m
        WHERE m.status = 'OPEN'
          AND (:province IS NULL OR m.province = :province)
          AND (:sport IS NULL OR m.sport = :sport)
          AND (:categoryGender IS NULL OR m.category_gender = :categoryGender)
          AND (:categoryAge IS NULL OR m.category_age = :categoryAge)
          AND (:matchDate IS NULL OR m.match_date = :matchDate)
          AND (:skillLevel IS NULL OR m.skill_level = :skillLevel OR m.skill_level = 'ANY')
          AND (:keyword IS NULL OR m.address_text ILIKE CONCAT('%', :keyword, '%'))
          AND m.created_by NOT IN (:blockedIds)
          AND m.latitude IS NOT NULL AND m.longitude IS NOT NULL
        HAVING (6371 * acos(
                cos(radians(:userLat)) * cos(radians(m.latitude)) *
                cos(radians(m.longitude) - radians(:userLng)) +
                sin(radians(:userLat)) * sin(radians(m.latitude))
            )) BETWEEN :minDistanceKm AND :maxDistanceKm
        ORDER BY distance_km ASC
        """,
        countQuery = """
        SELECT count(*) FROM matches m
        WHERE m.status = 'OPEN'
          AND (:province IS NULL OR m.province = :province)
          AND (:sport IS NULL OR m.sport = :sport)
          AND (:categoryGender IS NULL OR m.category_gender = :categoryGender)
          AND (:categoryAge IS NULL OR m.category_age = :categoryAge)
          AND (:matchDate IS NULL OR m.match_date = :matchDate)
          AND (:skillLevel IS NULL OR m.skill_level = :skillLevel OR m.skill_level = 'ANY')
          AND (:keyword IS NULL OR m.address_text ILIKE CONCAT('%', :keyword, '%'))
          AND m.created_by NOT IN (:blockedIds)
          AND m.latitude IS NOT NULL AND m.longitude IS NOT NULL
        """,
        nativeQuery = true)
    Page<Match> search(
        @Param("province") String province,
        @Param("sport") String sport,
        @Param("categoryGender") String categoryGender,
        @Param("categoryAge") String categoryAge,
        @Param("matchDate") java.time.LocalDate matchDate,
        @Param("skillLevel") String skillLevel,
        @Param("keyword") String keyword,
        @Param("blockedIds") List<Long> blockedIds,
        @Param("userLat") double userLat,
        @Param("userLng") double userLng,
        @Param("minDistanceKm") double minDistanceKm,
        @Param("maxDistanceKm") double maxDistanceKm,
        Pageable pageable
    );
}
