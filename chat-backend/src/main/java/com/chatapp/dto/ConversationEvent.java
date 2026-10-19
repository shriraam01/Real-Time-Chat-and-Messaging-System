package com.chatapp.dto;

/** Tells a client its conversation list changed. action is CHANGED or REMOVED. */
public record ConversationEvent(String action, Long conversationId) {}
