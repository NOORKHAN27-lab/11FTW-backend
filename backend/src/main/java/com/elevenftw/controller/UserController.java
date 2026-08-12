package com.elevenftw.controller;

import com.elevenftw.dto.*;
import com.elevenftw.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse getMyProfile(@AuthenticationPrincipal Long userId) {
        return userService.getProfile(userId);
    }

    @PutMapping("/me")
    public UserResponse updateMyProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userService.updateProfile(userId, request);
    }

    @GetMapping("/me/export")
    public ExportDataResponse exportMyData(@AuthenticationPrincipal Long userId) {
        return userService.exportData(userId);
    }

    @DeleteMapping("/me")
    public MessageResponse deleteMyAccount(@AuthenticationPrincipal Long userId) {
        userService.deleteAccount(userId);
        return new MessageResponse("Your account has been deleted.");
    }

    @GetMapping("/me/blocked")
    public List<BlockedUserResponse> getBlockedUsers(@AuthenticationPrincipal Long userId) {
        return userService.getBlockedUsers(userId);
    }

    @PostMapping("/{id}/block")
    public MessageResponse block(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        userService.blockUser(userId, id);
        return new MessageResponse("User blocked — their matches are hidden from your feed.");
    }

    @DeleteMapping("/{id}/block")
    public MessageResponse unblock(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        userService.unblockUser(userId, id);
        return new MessageResponse("User unblocked.");
    }

    @GetMapping("/{id}")
    public UserResponse getPublicProfile(@PathVariable Long id) {
        return userService.getProfile(id);
    }
}
