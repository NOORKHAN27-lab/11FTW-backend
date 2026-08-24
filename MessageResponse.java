package com.elevenftw.dto;

/** Generic {"message": "..."} body for endpoints that don't return a resource. */
public record MessageResponse(String message) {}
