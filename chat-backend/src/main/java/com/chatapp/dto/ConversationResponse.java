package com.chatapp.dto;

import com.chatapp.entity.ConversationType;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationResponse(
        Long id,
        ConversationType type,
        String name,
        String createdBy,
        List<MemberResponse> members,
        MessageResponse lastMessage,
        long unreadCount,
        LocalDateTime createdAt
) {}
