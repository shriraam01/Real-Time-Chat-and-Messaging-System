package com.chatapp.controller;

import com.chatapp.dto.MessageResponse;
import com.chatapp.dto.PageResponse;
import com.chatapp.service.MessageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    // sending goes through the WebSocket (see ChatController); REST is only used to load history
    @GetMapping
    public PageResponse<MessageResponse> history(@PathVariable("conversationId") Long conversationId,
                                                 @RequestParam(name = "page", defaultValue = "0") int page,
                                                 @RequestParam(name = "size", defaultValue = "20") int size,
                                                 @AuthenticationPrincipal UserDetails principal) {
        return messageService.history(conversationId, principal.getUsername(), page, size);
    }
}
