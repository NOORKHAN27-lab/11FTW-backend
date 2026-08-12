package com.elevenftw.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Requires a valid JWT on the STOMP CONNECT frame before a WebSocket
 * subscription is allowed. Previously any client could open /ws and
 * subscribe to /topic/matches/{id} with no auth at all — fine while the
 * only thing pushed over it was a non-sensitive slot count, but it's the
 * kind of gap that becomes a real problem the moment anything more
 * sensitive gets broadcast over this channel later.
 *
 * The frontend sends the access token as a STOMP CONNECT header (see
 * useMatchLiveUpdates.ts), not a URL query param, so it doesn't end up
 * logged in server access logs or browser history.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtUtil jwtUtil;

    public WebSocketAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            String token = (authHeader != null && authHeader.startsWith("Bearer "))
                    ? authHeader.substring(7)
                    : null;

            if (token == null) {
                log.debug("WebSocket CONNECT rejected: no Authorization header");
                throw new AuthenticationCredentialsNotFoundException("Missing auth token");
            }

            try {
                Long userId = jwtUtil.parseUserId(token);
                accessor.setUser((Principal) () -> String.valueOf(userId));
            } catch (Exception e) {
                log.debug("WebSocket CONNECT rejected: invalid token");
                throw new AuthenticationCredentialsNotFoundException("Invalid or expired auth token");
            }
        }

        return message;
    }
}
