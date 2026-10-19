package com.chatapp.controller;

import com.chatapp.dto.ChatMessageRequest;
import com.chatapp.dto.MessageResponse;
import com.chatapp.dto.ReadRequest;
import com.chatapp.dto.ReceiptEvent;
import com.chatapp.exception.BadRequestException;
import com.chatapp.exception.ForbiddenException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.repository.ConversationMemberRepository;
import com.chatapp.service.MessageService;
import com.chatapp.service.NotificationService;
import com.chatapp.service.ReceiptService;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
public class ChatController {

    private final MessageService messageService;
    private final ReceiptService receiptService;
    private final NotificationService notificationService;
    private final ConversationMemberRepository memberRepository;

    public ChatController(MessageService messageService,
                          ReceiptService receiptService,
                          NotificationService notificationService,
                          ConversationMemberRepository memberRepository) {
        this.messageService = messageService;
        this.receiptService = receiptService;
        this.notificationService = notificationService;
        this.memberRepository = memberRepository;
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

        Long conversationId = request.conversationId();

        // 1. save (also checks that the sender is a member)
        MessageResponse saved = messageService.send(conversationId, principal.getName(), request.content());

        // 2. push the message to every member, including the sender (confirmation)
        List<String> usernames = memberRepository.findUsernamesByConversationId(conversationId);
        notificationService.pushMessage(saved, usernames);

        // 3. update delivered / read positions and tell everyone so the ticks update
        for (ReceiptEvent event : receiptService.onMessageSent(conversationId, principal.getName(), saved.id())) {
            notificationService.pushReceipt(event, usernames);
        }
    }

    // Client sends to /app/chat.read when it opens or is looking at a conversation
    @MessageMapping("/chat.read")
    public void read(@Payload ReadRequest request, Principal principal) {
        if (request == null || request.conversationId() == null) {
            throw new BadRequestException("conversationId is required");
        }

        receiptService.markRead(request.conversationId(), principal.getName()).ifPresent(event -> {
            List<String> usernames = memberRepository.findUsernamesByConversationId(event.conversationId());
            notificationService.pushReceipt(event, usernames);
        });
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
