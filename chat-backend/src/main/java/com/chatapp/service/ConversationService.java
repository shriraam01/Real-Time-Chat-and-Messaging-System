package com.chatapp.service;

import com.chatapp.dto.ConversationResponse;
import com.chatapp.dto.MessageResponse;
import com.chatapp.entity.Conversation;
import com.chatapp.entity.ConversationMember;
import com.chatapp.entity.ConversationType;
import com.chatapp.entity.User;
import com.chatapp.exception.BadRequestException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.repository.ConversationMemberRepository;
import com.chatapp.repository.ConversationRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMemberRepository memberRepository,
                               MessageRepository messageRepository,
                               UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ConversationResponse startDirectConversation(String currentUsername, String otherUsername) {
        if (currentUsername.equals(otherUsername)) {
            throw new BadRequestException("You cannot start a conversation with yourself");
        }
        User current = findUser(currentUsername);
        User other = findUser(otherUsername);

        // reuse the existing chat if these two users already have one
        Conversation conversation = conversationRepository
                .findDirectConversation(ConversationType.ONE_TO_ONE, current.getId(), other.getId())
                .orElseGet(() -> createDirectConversation(current, other));

        return toResponse(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listForUser(String username) {
        User user = findUser(username);
        return memberRepository.findByUserId(user.getId()).stream()
                .map(ConversationMember::getConversation)
                .map(this::toResponse)
                .sorted(Comparator.comparing(ConversationService::lastActivity, Comparator.reverseOrder()))
                .toList();
    }

    private Conversation createDirectConversation(User a, User b) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.ONE_TO_ONE);
        Conversation saved = conversationRepository.save(conversation);

        memberRepository.save(new ConversationMember(saved, a));
        memberRepository.save(new ConversationMember(saved, b));
        return saved;
    }

    private ConversationResponse toResponse(Conversation c) {
        List<String> members = memberRepository.findByConversationId(c.getId()).stream()
                .map(m -> m.getUser().getUsername())
                .toList();

        MessageResponse lastMessage = messageRepository
                .findFirstByConversationIdOrderByCreatedAtDescIdDesc(c.getId())
                .map(MessageResponse::from)
                .orElse(null);

        return new ConversationResponse(c.getId(), c.getType(), c.getName(),
                members, lastMessage, c.getCreatedAt());
    }

    private static LocalDateTime lastActivity(ConversationResponse r) {
        return r.lastMessage() != null ? r.lastMessage().createdAt() : r.createdAt();
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
