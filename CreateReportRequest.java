package com.elevenftw.dto;

import com.elevenftw.entity.enums.ReportReason;
import com.elevenftw.entity.enums.ReportTargetType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
    @NotNull ReportTargetType targetType,
    @NotNull Long targetId,
    @NotNull ReportReason reason,
    @Size(max = 2000) String details
) {}
