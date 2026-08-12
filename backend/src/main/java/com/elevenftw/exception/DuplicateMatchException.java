package com.elevenftw.exception;

/** Thrown when a create-match request looks like an accidental repost — see MatchService#create. Mapped to 409. */
public class DuplicateMatchException extends RuntimeException {
    private final Long existingMatchId;

    public DuplicateMatchException(Long existingMatchId) {
        super("You already have a very similar match posted.");
        this.existingMatchId = existingMatchId;
    }

    public Long getExistingMatchId() {
        return existingMatchId;
    }
}
