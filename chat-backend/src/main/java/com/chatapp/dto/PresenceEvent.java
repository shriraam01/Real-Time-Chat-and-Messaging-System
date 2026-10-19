package com.chatapp.dto;

import java.time.LocalDateTime;

/** Pushed over WebSocket when someone goes online or offline. */
public record PresenceEvent(String username, boolean online, LocalDateTime lastSeen) {}
