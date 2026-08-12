package com.elevenftw.repository;

import com.elevenftw.entity.MatchRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchRatingRepository extends JpaRepository<MatchRating, Long> {
    Optional<MatchRating> findByMatchIdAndRaterIdAndRatedUserId(Long matchId, Long raterId, Long ratedUserId);
    long countByRatedUserId(Long ratedUserId);
    long countByRatedUserIdAndAttendedTrue(Long ratedUserId);
}
