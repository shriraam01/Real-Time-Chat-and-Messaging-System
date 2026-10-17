package com.chatapp.dto;

public record AuthResponse(String token, String tokenType, Long userId, String username) {}