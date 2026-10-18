package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;

public record StartConversationRequest(
        @NotBlank(message = "Username is required") String username
) {}
