package com.elevenftw.exception;

/** Thrown when a requested match/tournament/user id doesn't exist. Mapped to 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
