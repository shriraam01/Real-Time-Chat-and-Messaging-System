package com.chatapp.dto;

import java.time.LocalDateTime;

public record MemberResponse(
        Long userId,
        String username,
        boolean online,
        LocalDateTime lastSeen,
        Long lastDeliveredMessageId,
        Long lastReadMessageId
) {}
