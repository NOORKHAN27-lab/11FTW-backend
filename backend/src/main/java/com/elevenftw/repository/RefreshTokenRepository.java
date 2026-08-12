package com.elevenftw.repository;

import com.elevenftw.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    /** Called on account deletion — every session for this user is invalidated at once. */
    void deleteByUserId(Long userId);
}
