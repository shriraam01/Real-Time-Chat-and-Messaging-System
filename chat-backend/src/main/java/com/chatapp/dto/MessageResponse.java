package com.chatapp.dto;

import com.chatapp.entity.Message;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderUsername,
        String content,
        LocalDateTime createdAt
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getConversation().getId(),
                m.getSender().getId(),
                m.getSender().getUsername(),
                m.getContent(),
                m.getCreatedAt());
    }
}
