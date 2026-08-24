package com.elevenftw.dto;

import java.util.List;

public record MyMatchesResponse(List<MatchResponse> posted, List<MatchResponse> joined) {}
