package com.elevenftw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. @EnableScheduling turns on the auto-expiry job
 * (see service/MatchExpiryScheduler.java).
 */
@SpringBootApplication
@EnableScheduling
public class ElevenFtwApplication {
    public static void main(String[] args) {
        SpringApplication.run(ElevenFtwApplication.class, args);
    }
}
