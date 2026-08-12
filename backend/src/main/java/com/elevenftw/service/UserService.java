package com.elevenftw.service;

import com.elevenftw.dto.*;
import com.elevenftw.entity.BlockedUser;
import com.elevenftw.entity.User;
import com.elevenftw.entity.enums.ParticipantStatus;
import com.elevenftw.exception.NotFoundException;
import com.elevenftw.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository participantRepository;
    private final MatchRatingRepository ratingRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BlockedUserRepository blockedUserRepository;

    public UserService(
        UserRepository userRepository,
        MatchRepository matchRepository,
        MatchParticipantRepository participantRepository,
        MatchRatingRepository ratingRepository,
        RefreshTokenRepository refreshTokenRepository,
        BlockedUserRepository blockedUserRepository
    ) {
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
        this.ratingRepository = ratingRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.blockedUserRepository = blockedUserRepository;
    }

    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public UserResponse getProfile(Long userId) {
        User user = getById(userId);

        int matchesPlayed = (int) (
            matchRepository.countByCreatedById(userId)
            + participantRepository.countByUserIdAndStatus(userId, ParticipantStatus.CONFIRMED)
        );

        long totalRatings = ratingRepository.countByRatedUserId(userId);
        Double reliabilityScore = totalRatings == 0
            ? null
            : (ratingRepository.countByRatedUserIdAndAttendedTrue(userId) * 100.0) / totalRatings;

        return UserResponse.withStats(user, reliabilityScore, matchesPlayed);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = getById(userId);

        // If they're changing username, make sure nobody else already has it.
        if (!req.username().equals(user.getUsername()) && userRepository.existsByUsername(req.username())) {
            throw new IllegalArgumentException("That username is already taken");
        }

        user.setUsername(req.username());
        user.setPhoneNumber(req.phoneNumber());
        user.setHomeProvince(req.homeProvince());
        userRepository.save(user);
        return getProfile(userId);
    }

    /** Everything a user is entitled to download about themselves — see the "Export my data" button on Settings. */
    public ExportDataResponse exportData(Long userId) {
        var posted = matchRepository.findByCreatedByIdOrderByMatchDateDesc(userId).stream()
                .map(ExportMatchSummary::from)
                .toList();
        var joined = participantRepository.findByUserId(userId).stream()
                .map(p -> ExportMatchSummary.from(p.getMatch()))
                .toList();

        return new ExportDataResponse(getProfile(userId), posted, joined);
    }

    /**
     * Anonymizes rather than hard-deletes: matches you've posted or played
     * in stay in the system (other people's history references them, and
     * the DB has real foreign keys to this row), but every piece of your
     * PII — email, username, phone, photo, Google link, password — is
     * scrubbed, `deleted` is set (which blocks future logins, see
     * AuthService#login), and every active session is revoked immediately.
     */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = getById(userId);

        user.setUsername("deleted_user_" + userId);
        user.setEmail("deleted_" + userId + "_" + System.currentTimeMillis() + "@deleted.11ftw.local");
        user.setPassword(null);
        user.setPhoneNumber("");
        user.setProfilePhotoUrl(null);
        user.setGoogleId(null);
        user.setEmailVerified(false);
        user.setDeleted(true);
        userRepository.save(user);

        refreshTokenRepository.deleteByUserId(userId);
    }

    @Transactional
    public void blockUser(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("You can't block yourself.");
        }
        if (blockedUserRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            return; // already blocked — idempotent
        }
        User blocker = getById(blockerId);
        User blocked = getById(blockedId);
        blockedUserRepository.save(BlockedUser.builder().blocker(blocker).blocked(blocked).build());
    }

    @Transactional
    public void unblockUser(Long blockerId, Long blockedId) {
        blockedUserRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .ifPresent(blockedUserRepository::delete);
    }

    public java.util.List<BlockedUserResponse> getBlockedUsers(Long blockerId) {
        return blockedUserRepository.findByBlockerId(blockerId).stream()
                .map(BlockedUserResponse::from)
                .toList();
    }
}
