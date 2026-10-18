package com.chatapp.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Checks every incoming STOMP frame.
 * - CONNECT: validates the JWT and attaches the user to the WebSocket session
 * - SEND / SUBSCRIBE: only allowed for an authenticated session
 * - SUBSCRIBE: only personal queues under /user/queue/ are allowed
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public StompAuthChannelInterceptor(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new MessagingException("Missing Authorization header");
            }

            String token = header.substring(7);
            if (!jwtService.isTokenValid(token)) {
                throw new MessagingException("Invalid or expired token");
            }

            try {
                UserDetails userDetails = userDetailsService.loadUserByUsername(jwtService.extractUsername(token));
                accessor.setUser(new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()));
            } catch (UsernameNotFoundException e) {
                throw new MessagingException("User no longer exists");
            }

        } else if (StompCommand.SEND.equals(command)) {
            if (accessor.getUser() == null) {
                throw new MessagingException("Not authenticated");
            }

        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            if (accessor.getUser() == null) {
                throw new MessagingException("Not authenticated");
            }
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith("/user/queue/")) {
                throw new MessagingException("Subscription not allowed: " + destination);
            }
        }

        return message;
    }
}
