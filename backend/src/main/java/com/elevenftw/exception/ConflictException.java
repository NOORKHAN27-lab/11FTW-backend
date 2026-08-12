package com.elevenftw.exception;

/** Thrown when a request conflicts with existing state (e.g. email already registered). Mapped to 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
