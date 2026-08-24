package com.elevenftw.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.util.Collections;

/**
 * Wraps Google's own ID-token verification library. This is what stops
 * someone from just POSTing a made-up token to /api/auth/google — Google's
 * library cryptographically checks the token was really issued by Google
 * for OUR app's client ID before we trust anything in it.
 */
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${app.google.client-id}") String googleClientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    public record GoogleUser(String googleId, String email, String name, String pictureUrl) {}

    /** Returns null if the token is invalid/expired/forged. */
    public GoogleUser verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) return null;

            GoogleIdToken.Payload payload = idToken.getPayload();
            return new GoogleUser(
                    payload.getSubject(),
                    payload.getEmail(),
                    (String) payload.get("name"),
                    (String) payload.get("picture")
            );
        } catch (GeneralSecurityException | java.io.IOException e) {
            return null;
        }
    }
}
