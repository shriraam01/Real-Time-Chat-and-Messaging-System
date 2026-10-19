package com.chatapp.dto;

import java.time.LocalDateTime;

public record UserSummary(Long id, String username, boolean online, LocalDateTime lastSeen) {}
