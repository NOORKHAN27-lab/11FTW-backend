package com.elevenftw.controller;

import com.elevenftw.dto.CreateMatchRequest;
import com.elevenftw.dto.MatchResponse;
import com.elevenftw.dto.MatchSearchFilters;
import com.elevenftw.dto.MessageResponse;
import com.elevenftw.dto.MyMatchesResponse;
import com.elevenftw.dto.ParticipantResponse;
import com.elevenftw.dto.RateMatchRequest;
import com.elevenftw.entity.enums.*;
import com.elevenftw.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public Page<MatchResponse> search(
            @RequestParam(required = false) ProvinceType province,
            @RequestParam(required = false) SportType sport,
            @RequestParam(required = false) GenderCategory categoryGender,
            @RequestParam(required = false) AgeCategory categoryAge,
            @RequestParam(required = false) LocalDate matchDate,
            @RequestParam(required = false) SkillLevel skillLevel,
            @RequestParam(required = false) String keyword,
            @RequestParam double userLat,
            @RequestParam double userLng,
            @RequestParam(defaultValue = "0") double minDistanceKm,
            @RequestParam(defaultValue = "50") double maxDistanceKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Long userId
    ) {
        MatchSearchFilters filters = new MatchSearchFilters(
                province, sport, categoryGender, categoryAge, matchDate, skillLevel, keyword,
                userLat, userLng, minDistanceKm, maxDistanceKm
        );
        Pageable pageable = PageRequest.of(page, size);
        return matchService.search(filters, userId, pageable);
    }

    @GetMapping("/mine")
    public MyMatchesResponse mine(@AuthenticationPrincipal Long userId) {
        return matchService.getMyMatches(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchResponse create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateMatchRequest request
    ) {
        return matchService.create(userId, request);
    }

    @GetMapping("/{id}")
    public MatchResponse getById(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return matchService.getById(id, userId);
    }

    @PutMapping("/{id}")
    public MatchResponse update(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateMatchRequest request
    ) {
        return matchService.update(id, userId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        matchService.delete(id, userId);
    }

    @PostMapping("/{id}/join")
    public MatchResponse join(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return matchService.joinMatch(id, userId);
    }

    @DeleteMapping("/{id}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        matchService.leaveMatch(id, userId);
    }

    @GetMapping("/{id}/participants")
    public List<ParticipantResponse> participants(@PathVariable Long id) {
        return matchService.getParticipants(id);
    }

    /** Organizer-only: remove a specific player from the match — see MatchService#removeParticipant. */
    @DeleteMapping("/{id}/participants/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeParticipant(
            @PathVariable("id") Long id,
            @PathVariable("userId") Long targetUserId,
            @AuthenticationPrincipal Long requesterId
    ) {
        matchService.removeParticipant(id, requesterId, targetUserId);
    }

    @PostMapping("/{id}/ratings")
    public MessageResponse rate(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody RateMatchRequest request
    ) {
        matchService.rateMatch(id, userId, request);
        return new MessageResponse("Thanks for rating your teammates.");
    }
}
