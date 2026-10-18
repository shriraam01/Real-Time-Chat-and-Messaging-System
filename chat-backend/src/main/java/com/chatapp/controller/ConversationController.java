package com.chatapp.controller;

import com.chatapp.dto.ConversationResponse;
import com.chatapp.dto.StartConversationRequest;
import com.chatapp.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> start(@Valid @RequestBody StartConversationRequest request,
                                                      @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(
                conversationService.startDirectConversation(principal.getUsername(), request.username()));
    }

    @GetMapping
    public List<ConversationResponse> list(@AuthenticationPrincipal UserDetails principal) {
        return conversationService.listForUser(principal.getUsername());
    }
}
