package com.elevenftw.dto;

import com.elevenftw.entity.Match;

import java.time.LocalDate;
import java.time.LocalTime;

public record ExportMatchSummary(
    Long id, String sport, LocalDate matchDate, LocalTime startTime, String addressText, String status
) {
    public static ExportMatchSummary from(Match m) {
        return new ExportMatchSummary(
            m.getId(), m.getSport().name(), m.getMatchDate(), m.getStartTime(), m.getAddressText(), m.getStatus().name()
        );
    }
}
