package com.chatapp.dto;

/** Pushed over WebSocket when a member's delivered/read position in a conversation moves forward. */
public record ReceiptEvent(
        Long conversationId,
        String username,
        Long lastDeliveredMessageId,
        Long lastReadMessageId
) {}
