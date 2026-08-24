package com.elevenftw.service;

import com.elevenftw.dto.CreateMatchRequest;
import com.elevenftw.dto.MatchResponse;
import com.elevenftw.dto.MatchSearchFilters;
import com.elevenftw.dto.MyMatchesResponse;
import com.elevenftw.dto.ParticipantResponse;
import com.elevenftw.dto.RateMatchRequest;
import com.elevenftw.dto.RatePlayerEntry;
import com.elevenftw.entity.Match;
import com.elevenftw.entity.MatchParticipant;
import com.elevenftw.entity.MatchRating;
import com.elevenftw.entity.User;
import com.elevenftw.entity.enums.MatchStatus;
import com.elevenftw.entity.enums.NotificationType;
import com.elevenftw.entity.enums.ParticipantStatus;
import com.elevenftw.exception.DuplicateMatchException;
import com.elevenftw.exception.ForbiddenException;
import com.elevenftw.exception.MatchFullException;
import com.elevenftw.exception.NotFoundException;
import com.elevenftw.repository.BlockedUserRepository;
import com.elevenftw.repository.MatchParticipantRepository;
import com.elevenftw.repository.MatchRatingRepository;
import com.elevenftw.repository.MatchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository participantRepository;
    private final MatchRatingRepository ratingRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final UserService userService;
    private final GeocodingService geocodingService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    private static final int VERIFIED_GROUND_THRESHOLD = 3;

    public MatchService(
            MatchRepository matchRepository,
            MatchParticipantRepository participantRepository,
            MatchRatingRepository ratingRepository,
            BlockedUserRepository blockedUserRepository,
            UserService userService,
            GeocodingService geocodingService,
            NotificationService notificationService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
        this.ratingRepository = ratingRepository;
        this.blockedUserRepository = blockedUserRepository;
        this.userService = userService;
        this.geocodingService = geocodingService;
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public MatchResponse create(Long creatorId, CreateMatchRequest req) {
        if (!Boolean.TRUE.equals(req.confirmDuplicate())) {
            matchRepository.findFirstByCreatedByIdAndSportAndMatchDateAndAddressTextIgnoreCaseAndStatusIn(
                    creatorId, req.sport(), req.matchDate(), req.addressText(),
                    List.of(MatchStatus.OPEN, MatchStatus.FULL)
            ).ifPresent(existing -> {
                throw new DuplicateMatchException(existing.getId());
            });
        }

        User creator = userService.getById(creatorId);
        GeocodingService.LatLng coords = geocodingService.geocode(req.addressText());

        Match match = Match.builder()
                .createdBy(creator)
                .sport(req.sport())
                .footballFormat(req.footballFormat())
                .cricketFormat(req.cricketFormat())
                .categoryGender(req.categoryGender())
                .categoryAge(req.categoryAge())
                .skillLevel(req.skillLevel())
                .matchType(req.matchType() != null ? req.matchType() : com.elevenftw.entity.enums.MatchType.FRIENDLY)
                .description(req.description())
                .province(req.province())
                .addressText(req.addressText())
                .latitude(coords.lat())
                .longitude(coords.lng())
                .matchDate(req.matchDate())
                .startTime(req.startTime())
                .endTime(req.endTime())
                .maxPlayers(req.maxPlayers())
                .feeText(req.feeText())
                .totalFeeAmount(req.totalFeeAmount())
                .status(MatchStatus.OPEN)
                .build();

        match = matchRepository.save(match);
        return toResponse(match, creatorId, null);
    }

    /**
     * Edits an existing match's details. Only the creator can edit, and not
     * once it's expired/cancelled. Shrinking maxPlayers below the number of
     * people already confirmed is rejected outright (rather than silently
     * bumping someone) — growing it re-checks the waitlist and promotes
     * people into the newly opened spots, same as a normal leave would.
     */
    @Transactional
    public MatchResponse update(Long matchId, Long requesterId, CreateMatchRequest req) {
        Match match = findOrThrow(matchId);
        if (!match.getCreatedBy().getId().equals(requesterId)) {
            throw new ForbiddenException("Only the creator can edit this match");
        }
        if (match.getStatus() == MatchStatus.EXPIRED || match.getStatus() == MatchStatus.CANCELLED) {
            throw new ForbiddenException("This match can no longer be edited");
        }

        long confirmedCount = participantRepository.countByMatchIdAndStatus(matchId, ParticipantStatus.CONFIRMED);
        if (req.maxPlayers() < confirmedCount) {
            throw new IllegalArgumentException(
                    "Can't set max players below the " + confirmedCount + " player(s) already confirmed.");
        }

        if (!req.addressText().equals(match.getAddressText())) {
            GeocodingService.LatLng coords = geocodingService.geocode(req.addressText());
            match.setLatitude(coords.lat());
            match.setLongitude(coords.lng());
        }

        match.setSport(req.sport());
        match.setFootballFormat(req.footballFormat());
        match.setCricketFormat(req.cricketFormat());
        match.setCategoryGender(req.categoryGender());
        match.setCategoryAge(req.categoryAge());
        match.setSkillLevel(req.skillLevel());
        if (req.matchType() != null) match.setMatchType(req.matchType());
        match.setDescription(req.description());
        match.setProvince(req.province());
        match.setAddressText(req.addressText());
        match.setMatchDate(req.matchDate());
        match.setStartTime(req.startTime());
        match.setEndTime(req.endTime());
        match.setMaxPlayers(req.maxPlayers());
        match.setFeeText(req.feeText());
        match.setTotalFeeAmount(req.totalFeeAmount());

        // maxPlayers may have gone up — pull people off the waitlist into the newly opened spots.
        long newConfirmedCount = confirmedCount;
        while (newConfirmedCount < match.getMaxPlayers()) {
            Optional<MatchParticipant> next = participantRepository
                    .findFirstByMatchIdAndStatusOrderByJoinedAtAsc(matchId, ParticipantStatus.WAITLISTED);
            if (next.isEmpty()) break;
            next.get().setStatus(ParticipantStatus.CONFIRMED);
            participantRepository.save(next.get());
            notifyPromoted(next.get());
            newConfirmedCount++;
        }
        match.setStatus(newConfirmedCount >= match.getMaxPlayers() ? MatchStatus.FULL : MatchStatus.OPEN);

        match = matchRepository.save(match);
        broadcastSlotUpdate(match);
        return toResponse(match, requesterId, null);
    }

    public Page<MatchResponse> search(MatchSearchFilters f, Long requesterId, Pageable pageable) {
        List<Long> blockedIds = requesterId == null
                ? List.of(-1L)
                : blockedUserRepository.findBlockedUserIds(requesterId);
        if (blockedIds.isEmpty()) blockedIds = List.of(-1L); // no-op sentinel so "NOT IN (:blockedIds)" never breaks on an empty list

        Page<Match> page = matchRepository.search(
                f.province() == null ? null : f.province().name(),
                f.sport() == null ? null : f.sport().name(),
                f.categoryGender() == null ? null : f.categoryGender().name(),
                f.categoryAge() == null ? null : f.categoryAge().name(),
                f.matchDate(),
                f.skillLevel() == null ? null : f.skillLevel().name(),
                (f.keyword() == null || f.keyword().isBlank()) ? null : f.keyword().trim(),
                blockedIds,
                f.userLat(), f.userLng(),
                f.minDistanceKm(), f.maxDistanceKm(),
                pageable
        );
        return page.map(m -> toResponse(m, requesterId, distanceFrom(m, f.userLat(), f.userLng())));
    }

    public MatchResponse getById(Long matchId, Long requesterId) {
        Match match = findOrThrow(matchId);
        return toResponse(match, requesterId, null);
    }

    /** Matches the requester posted, and matches they've joined (confirmed or waitlisted) — for the "My Matches" page. */
    public MyMatchesResponse getMyMatches(Long userId) {
        List<Match> posted = matchRepository.findByCreatedByIdOrderByMatchDateDesc(userId);
        List<Match> joined = participantRepository.findByUserId(userId).stream()
                .map(MatchParticipant::getMatch)
                .toList();

        return new MyMatchesResponse(
                posted.stream().map(m -> toResponse(m, userId, null)).toList(),
                joined.stream().map(m -> toResponse(m, userId, null)).toList()
        );
    }

    @Transactional
    public void delete(Long matchId, Long requesterId) {
        Match match = findOrThrow(matchId);
        if (!match.getCreatedBy().getId().equals(requesterId)) {
            throw new ForbiddenException("Only the creator can cancel this match");
        }
        matchRepository.delete(match);
    }

    /**
     * The concurrency-critical path. See technical_design.md §1.3 for the
     * full explanation — short version: findByIdForUpdate() takes a
     * row-level lock on this match for the rest of the transaction, so if
     * two people tap "Join" on the very last spot at the same instant, the
     * second request's count check waits for the first to fully commit,
     * and correctly sees the match as full (and joins the waitlist instead).
     * No lost updates, no double-booking, guaranteed by the database.
     *
     * When a match is full, joining no longer fails outright — the user is
     * added to the waitlist instead, and gets auto-promoted the moment
     * someone confirmed leaves (see leaveMatch).
     */
    @Transactional
    public MatchResponse joinMatch(Long matchId, Long userId) {
        Match match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new NotFoundException("Match not found"));

        if (match.getStatus() != MatchStatus.OPEN && match.getStatus() != MatchStatus.FULL) {
            throw new MatchFullException("This match is no longer accepting players");
        }
        if (participantRepository.existsByMatchIdAndUserId(matchId, userId)) {
            throw new IllegalStateException("You've already joined this match");
        }

        User user = userService.getById(userId);
        long confirmedCount = participantRepository.countByMatchIdAndStatus(matchId, ParticipantStatus.CONFIRMED);

        ParticipantStatus newStatus = confirmedCount < match.getMaxPlayers()
                ? ParticipantStatus.CONFIRMED
                : ParticipantStatus.WAITLISTED;

        participantRepository.save(MatchParticipant.builder().match(match).user(user).status(newStatus).build());

        if (newStatus == ParticipantStatus.CONFIRMED) {
            long newConfirmed = confirmedCount + 1;
            if (newConfirmed >= match.getMaxPlayers() && match.getStatus() != MatchStatus.FULL) {
                match.setStatus(MatchStatus.FULL);
                matchRepository.save(match);
            }
            if (!match.getCreatedBy().getId().equals(userId)) {
                notificationService.notify(
                        match.getCreatedBy(),
                        NotificationType.MATCH_JOINED,
                        user.getUsername() + " joined your " + match.getSport().name().toLowerCase() + " match.",
                        match.getId()
                );
            }
        }

        broadcastSlotUpdate(match);
        return toResponse(match, userId, null);
    }

    @Transactional
    public void leaveMatch(Long matchId, Long userId) {
        Match match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new NotFoundException("Match not found"));

        MatchParticipant participant = participantRepository.findByMatchIdAndUserId(matchId, userId)
                .orElseThrow(() -> new IllegalStateException("You haven't joined this match"));

        removeParticipantAndPromote(match, participant);
    }

    /**
     * Organizer-initiated removal — same effect as the player leaving
     * themselves (including waitlist promotion), but gated to the match
     * creator and targeting an arbitrary participant. Powers "Manage
     * Players" on the match detail page.
     */
    @Transactional
    public void removeParticipant(Long matchId, Long requesterId, Long targetUserId) {
        Match match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new NotFoundException("Match not found"));
        if (!match.getCreatedBy().getId().equals(requesterId)) {
            throw new ForbiddenException("Only the organizer can remove players from this match");
        }
        MatchParticipant participant = participantRepository.findByMatchIdAndUserId(matchId, targetUserId)
                .orElseThrow(() -> new NotFoundException("That player hasn't joined this match"));

        removeParticipantAndPromote(match, participant);

        if (!targetUserId.equals(requesterId)) {
            notificationService.notify(
                    participant.getUser(),
                    NotificationType.PLAYER_REMOVED,
                    "You were removed from the " + match.getSport().name().toLowerCase() + " match at " + match.getAddressText() + ".",
                    match.getId()
            );
        }
    }

    /** Shared by leaveMatch and removeParticipant: delete the row, then promote the next waitlisted player if a confirmed spot just opened up. */
    private void removeParticipantAndPromote(Match match, MatchParticipant participant) {
        boolean wasConfirmed = participant.getStatus() == ParticipantStatus.CONFIRMED;
        Long matchId = match.getId();
        participantRepository.delete(participant);

        if (wasConfirmed) {
            // A confirmed spot just opened up — promote the longest-waiting person on the waitlist, if any.
            Optional<MatchParticipant> next = participantRepository
                    .findFirstByMatchIdAndStatusOrderByJoinedAtAsc(matchId, ParticipantStatus.WAITLISTED);
            next.ifPresent(p -> {
                p.setStatus(ParticipantStatus.CONFIRMED);
                participantRepository.save(p);
                notifyPromoted(p);
            });

            long confirmedCount = participantRepository.countByMatchIdAndStatus(matchId, ParticipantStatus.CONFIRMED);
            MatchStatus newStatus = confirmedCount >= match.getMaxPlayers() ? MatchStatus.FULL : MatchStatus.OPEN;
            if (newStatus != match.getStatus() && (match.getStatus() == MatchStatus.OPEN || match.getStatus() == MatchStatus.FULL)) {
                match.setStatus(newStatus);
                matchRepository.save(match);
            }
        }

        broadcastSlotUpdate(match);
    }

    public List<ParticipantResponse> getParticipants(Long matchId) {
        return participantRepository.findByMatchId(matchId).stream()
                .map(ParticipantResponse::from)
                .toList();
    }

    /**
     * Rate the people you played with — only allowed once the match's end
     * time has passed, and only by someone who was actually there
     * (creator or a confirmed participant). Resubmitting overwrites your
     * previous rating for that player rather than erroring, so people can
     * fix a misclick.
     */
    @Transactional
    public void rateMatch(Long matchId, Long raterId, RateMatchRequest request) {
        Match match = findOrThrow(matchId);

        LocalDateTime matchEnd = LocalDateTime.of(match.getMatchDate(), match.getEndTime());
        if (LocalDateTime.now().isBefore(matchEnd)) {
            throw new ForbiddenException("You can rate players once the match has ended.");
        }

        boolean raterWasCreator = match.getCreatedBy().getId().equals(raterId);
        boolean raterWasConfirmed = participantRepository.findByMatchIdAndUserId(matchId, raterId)
                .map(p -> p.getStatus() == ParticipantStatus.CONFIRMED)
                .orElse(false);
        if (!raterWasCreator && !raterWasConfirmed) {
            throw new ForbiddenException("Only people who played in this match can rate it.");
        }

        for (RatePlayerEntry entry : request.ratings()) {
            if (entry.ratedUserId().equals(raterId)) {
                throw new IllegalArgumentException("You can't rate yourself.");
            }

            boolean ratedWasCreator = match.getCreatedBy().getId().equals(entry.ratedUserId());
            boolean ratedWasConfirmed = participantRepository
                    .findByMatchIdAndUserId(matchId, entry.ratedUserId())
                    .map(p -> p.getStatus() == ParticipantStatus.CONFIRMED)
                    .orElse(false);
            if (!ratedWasCreator && !ratedWasConfirmed) {
                throw new IllegalArgumentException("That person wasn't part of this match.");
            }

            User ratedUser = userService.getById(entry.ratedUserId());
            User rater = userService.getById(raterId);

            MatchRating rating = ratingRepository
                    .findByMatchIdAndRaterIdAndRatedUserId(matchId, raterId, entry.ratedUserId())
                    .orElseGet(() -> MatchRating.builder()
                            .match(match)
                            .rater(rater)
                            .ratedUser(ratedUser)
                            .build());

            rating.setAttended(entry.attended());
            rating.setPunctual(entry.punctual());
            ratingRepository.save(rating);
        }
    }

    // ---------- helpers ----------

    private Match findOrThrow(Long id) {
        return matchRepository.findById(id).orElseThrow(() -> new NotFoundException("Match not found"));
    }

    private void notifyPromoted(MatchParticipant promoted) {
        notificationService.notify(
                promoted.getUser(),
                NotificationType.WAITLIST_PROMOTED,
                "A spot opened up — you're confirmed for the " + promoted.getMatch().getSport().name().toLowerCase() + " match you were waitlisted for!",
                promoted.getMatch().getId()
        );
    }

    private MatchResponse toResponse(Match m, Long requesterId, Double distanceKm) {
        int spotsFilled = (int) participantRepository.countByMatchIdAndStatus(m.getId(), ParticipantStatus.CONFIRMED);
        int waitlistCount = (int) participantRepository.countByMatchIdAndStatus(m.getId(), ParticipantStatus.WAITLISTED);

        String contact = null;
        boolean requesterWaitlisted = false;

        boolean isCreator = requesterId != null && m.getCreatedBy().getId().equals(requesterId);

        Optional<MatchParticipant> requesterParticipation = requesterId == null
                ? Optional.empty()
                : participantRepository.findByMatchIdAndUserId(m.getId(), requesterId);

        boolean hasJoined = requesterParticipation.isPresent();

        if (isCreator || hasJoined) {
            contact = m.getCreatedBy().getPhoneNumber();
        }
        if (requesterParticipation.map(p -> p.getStatus() == ParticipantStatus.WAITLISTED).orElse(false)) {
            requesterWaitlisted = true;
        }

        boolean verifiedGround = matchRepository.countByAddressTextIgnoreCase(m.getAddressText()) >= VERIFIED_GROUND_THRESHOLD;

        return MatchResponse.from(m, spotsFilled, waitlistCount, requesterWaitlisted, verifiedGround, distanceKm, contact);
    }

    private Double distanceFrom(Match m, double userLat, double userLng) {
        if (m.getLatitude() == null || m.getLongitude() == null) return null;
        double earthRadiusKm = 6371;
        double dLat = Math.toRadians(m.getLatitude() - userLat);
        double dLng = Math.toRadians(m.getLongitude() - userLng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(userLat)) * Math.cos(Math.toRadians(m.getLatitude()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    /** Pushes the new slot/waitlist counts to everyone currently viewing this match. */
    private void broadcastSlotUpdate(Match match) {
        int spotsFilled = (int) participantRepository.countByMatchIdAndStatus(match.getId(), ParticipantStatus.CONFIRMED);
        int waitlistCount = (int) participantRepository.countByMatchIdAndStatus(match.getId(), ParticipantStatus.WAITLISTED);
        messagingTemplate.convertAndSend(
                "/topic/matches/" + match.getId(),
                Map.of(
                        "matchId", match.getId(),
                        "spotsFilled", spotsFilled,
                        "waitlistCount", waitlistCount,
                        "maxPlayers", match.getMaxPlayers(),
                        "status", match.getStatus().name()
                )
        );
    }
}
