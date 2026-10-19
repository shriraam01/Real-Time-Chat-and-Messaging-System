package com.chatapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGroupRequest(
        @NotBlank(message = "Group name is required")
        @Size(max = 100, message = "Group name must be at most 100 characters")
        String name,

        @NotEmpty(message = "Add at least one member")
        List<String> usernames
) {}
