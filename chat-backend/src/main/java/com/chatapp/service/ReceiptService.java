package com.chatapp.service;

import com.chatapp.dto.ReceiptEvent;
import com.chatapp.entity.ConversationMember;
import com.chatapp.exception.ForbiddenException;
import com.chatapp.repository.ConversationMemberRepository;
import com.chatapp.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Maintains the delivered / read positions stored on each ConversationMember.
 * Positions only ever move forward. The methods return events describing what changed,
 * and the caller pushes them to the other members after the transaction has committed.
 */
@Service
public class ReceiptService {

    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final PresenceService presenceService;

    public ReceiptService(ConversationMemberRepository memberRepository,
                          MessageRepository messageRepository,
                          PresenceService presenceService) {
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.presenceService = presenceService;
    }

    /**
     * A new message was saved. The sender has obviously seen everything up to it,
     * and every other member who is online right now has received it.
     */
    @Transactional
    public List<ReceiptEvent> onMessageSent(Long conversationId, String senderUsername, Long messageId) {
        List<ReceiptEvent> events = new ArrayList<>();

        for (ConversationMember member : memberRepository.findByConversationId(conversationId)) {
            String username = member.getUser().getUsername();
            boolean changed;

            if (username.equalsIgnoreCase(senderUsername)) {
                changed = advanceRead(member, messageId);
            } else if (presenceService.isOnline(username)) {
                changed = advanceDelivered(member, messageId);
            } else {
                changed = false;
            }

            if (changed) {
                events.add(toEvent(conversationId, member));
            }
        }
        return events;
    }

    /** The user opened a conversation (or is looking at it): everything in it counts as read. */
    @Transactional
    public Optional<ReceiptEvent> markRead(Long conversationId, String username) {
        ConversationMember member = memberRepository
                .findByConversationIdAndUserUsername(conversationId, username)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this conversation"));

        Long latest = messageRepository.findLatestMessageId(conversationId);
        if (latest == null) {
            return Optional.empty();
        }
        return advanceRead(member, latest)
                ? Optional.of(toEvent(conversationId, member))
                : Optional.empty();
    }

    /** The user just connected: messages that arrived while they were offline are now delivered. */
    @Transactional
    public List<ReceiptEvent> markDeliveredOnConnect(String username) {
        List<ReceiptEvent> events = new ArrayList<>();

        for (ConversationMember member : memberRepository.findByUserUsername(username)) {
            Long conversationId = member.getConversation().getId();
            Long latest = messageRepository.findLatestMessageId(conversationId);
            if (latest != null && advanceDelivered(member, latest)) {
                events.add(toEvent(conversationId, member));
            }
        }
        return events;
    }

    private boolean advanceDelivered(ConversationMember member, Long messageId) {
        Long current = member.getLastDeliveredMessageId();
        if (current == null || current < messageId) {
            member.setLastDeliveredMessageId(messageId);
            return true;
        }
        return false;
    }

    private boolean advanceRead(ConversationMember member, Long messageId) {
        boolean changed = advanceDelivered(member, messageId);   // read implies delivered
        Long current = member.getLastReadMessageId();
        if (current == null || current < messageId) {
            member.setLastReadMessageId(messageId);
            changed = true;
        }
        return changed;
    }

    private ReceiptEvent toEvent(Long conversationId, ConversationMember member) {
        return new ReceiptEvent(conversationId, member.getUser().getUsername(),
                member.getLastDeliveredMessageId(), member.getLastReadMessageId());
    }
}
