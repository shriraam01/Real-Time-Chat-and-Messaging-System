package com.chatapp.websocket;

import com.chatapp.dto.PresenceEvent;
import com.chatapp.dto.ReceiptEvent;
import com.chatapp.repository.ConversationMemberRepository;
import com.chatapp.service.NotificationService;
import com.chatapp.service.PresenceService;
import com.chatapp.service.ReceiptService;
import com.chatapp.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.LocalDateTime;

/**
 * Reacts to WebSocket sessions opening and closing:
 * online / offline status, "last seen", and marking waiting messages as delivered.
 */
@Component
public class PresenceEventListener {

    private static final Logger log = LoggerFactory.getLogger(PresenceEventListener.class);

    private final PresenceService presenceService;
    private final UserService userService;
    private final ReceiptService receiptService;
    private final NotificationService notificationService;
    private final ConversationMemberRepository memberRepository;

    public PresenceEventListener(PresenceService presenceService,
                                 UserService userService,
                                 ReceiptService receiptService,
                                 NotificationService notificationService,
                                 ConversationMemberRepository memberRepository) {
        this.presenceService = presenceService;
        this.userService = userService;
        this.receiptService = receiptService;
        this.notificationService = notificationService;
        this.memberRepository = memberRepository;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        try {
            Principal user = event.getUser();
            if (user == null) {
                return;
            }
            String username = user.getName();
            String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();

            boolean cameOnline = presenceService.connected(username, sessionId);
            if (cameOnline) {
                notificationService.pushPresence(new PresenceEvent(username, true, null),
                        memberRepository.findCoMemberUsernames(username));
            }

            // messages that arrived while this user was offline count as delivered now
            for (ReceiptEvent receipt : receiptService.markDeliveredOnConnect(username)) {
                notificationService.pushReceipt(receipt,
                        memberRepository.findUsernamesByConversationId(receipt.conversationId()));
            }
        } catch (Exception e) {
            log.warn("Could not process connect event", e);
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        try {
            String username = presenceService.disconnected(event.getSessionId());
            if (username == null) {
                return;   // the user still has another session open, or was never fully connected
            }

            LocalDateTime lastSeen = userService.touchLastSeen(username);
            notificationService.pushPresence(new PresenceEvent(username, false, lastSeen),
                    memberRepository.findCoMemberUsernames(username));
        } catch (Exception e) {
            log.warn("Could not process disconnect event", e);
        }
    }
}
