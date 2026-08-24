package com.elevenftw.exception;

/** Thrown when a user tries to do something only the owner/organizer can do. Mapped to 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
