package com.elevenftw.exception;

/** Thrown when a join attempt loses the race for the last open spot. Mapped to 409 Conflict. */
public class MatchFullException extends RuntimeException {
    public MatchFullException(String message) {
        super(message);
    }
}
