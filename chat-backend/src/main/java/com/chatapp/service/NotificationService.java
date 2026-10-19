package com.chatapp.service;

import com.chatapp.dto.ConversationEvent;
import com.chatapp.dto.MessageResponse;
import com.chatapp.dto.PresenceEvent;
import com.chatapp.dto.ReceiptEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;

/** The single place that pushes things to users over WebSocket. */
@Service
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void pushMessage(MessageResponse message, Collection<String> usernames) {
        sendToEach(usernames, "/queue/messages", message);
    }

    public void pushReceipt(ReceiptEvent event, Collection<String> usernames) {
        sendToEach(usernames, "/queue/receipts", event);
    }

    public void pushPresence(PresenceEvent event, Collection<String> usernames) {
        sendToEach(usernames, "/queue/presence", event);
    }

    public void pushConversationEvent(String action, Long conversationId, Collection<String> usernames) {
        sendToEach(usernames, "/queue/conversations", new ConversationEvent(action, conversationId));
    }

    private void sendToEach(Collection<String> usernames, String destination, Object payload) {
        for (String username : usernames) {
            messagingTemplate.convertAndSendToUser(username, destination, payload);
        }
    }
}
