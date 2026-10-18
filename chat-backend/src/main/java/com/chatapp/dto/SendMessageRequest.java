package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Message cannot be empty")
        @Size(max = 2000, message = "Message must be at most 2000 characters")
        String content
) {}
