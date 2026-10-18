package com.chatapp.controller;

import com.chatapp.dto.ChatMessageRequest;
import com.chatapp.dto.MessageResponse;
import com.chatapp.exception.BadRequestException;
import com.chatapp.exception.ForbiddenException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.repository.ConversationMemberRepository;
import com.chatapp.service.MessageService;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
public class ChatController {

    private final MessageService messageService;
    private final ConversationMemberRepository memberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(MessageService messageService,
                          ConversationMemberRepository memberRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.memberRepository = memberRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // Client sends to /app/chat.send
    @MessageMapping("/chat.send")
    public void send(@Payload ChatMessageRequest request, Principal principal) {
        if (request == null || request.conversationId() == null) {
            throw new BadRequestException("conversationId is required");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new BadRequestException("Message cannot be empty");
        }
        if (request.content().length() > 2000) {
            throw new BadRequestException("Message must be at most 2000 characters");
        }

        // saves the message (and checks membership) using the same service as the REST endpoint
        MessageResponse saved = messageService.send(
                request.conversationId(), principal.getName(), request.content());

        // push to every member, including the sender (acts as a delivery confirmation)
        List<String> usernames = memberRepository.findUsernamesByConversationId(request.conversationId());
        for (String username : usernames) {
            messagingTemplate.convertAndSendToUser(username, "/queue/messages", saved);
        }
    }

    // Errors go back only to the user who caused them, on /user/queue/errors
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, String> handleException(Exception ex) {
        boolean expected = ex instanceof BadRequestException
                || ex instanceof ForbiddenException
                || ex instanceof ResourceNotFoundException;
        return Map.of("error", expected ? ex.getMessage() : "Something went wrong");
    }
}
