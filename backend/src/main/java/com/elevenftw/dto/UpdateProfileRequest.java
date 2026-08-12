package com.elevenftw.dto;

import com.elevenftw.entity.enums.ProvinceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(
    @NotBlank String username,
    @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Enter a valid phone number")
    String phoneNumber,
    ProvinceType homeProvince
) {}
