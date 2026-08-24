package com.elevenftw.dto;

import java.util.List;

/** The full payload behind "Export my data" — see UserService#exportData. */
public record ExportDataResponse(
    UserResponse profile,
    List<ExportMatchSummary> postedMatches,
    List<ExportMatchSummary> joinedMatches
) {}
