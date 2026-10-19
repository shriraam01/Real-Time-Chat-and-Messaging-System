package com.chatapp.service;

import com.chatapp.dto.MessageResponse;
import com.chatapp.dto.PageResponse;
import com.chatapp.entity.Conversation;
import com.chatapp.entity.Message;
import com.chatapp.entity.User;
import com.chatapp.exception.ForbiddenException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.repository.ConversationMemberRepository;
import com.chatapp.repository.ConversationRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          ConversationMemberRepository memberRepository,
                          UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
    }

    /** Saves a message. Called by the WebSocket handler; delivery to the other members happens there. */
    @Transactional
    public MessageResponse send(Long conversationId, String senderUsername, String content) {
        User sender = findUser(senderUsername);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        requireMember(conversationId, sender.getId());

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content.trim());

        return MessageResponse.from(messageRepository.save(message));
    }

    /** One page of history, newest message first. */
    @Transactional(readOnly = true)
    public PageResponse<MessageResponse> history(Long conversationId, String username, int page, int size) {
        User user = findUser(username);
        if (!conversationRepository.existsById(conversationId)) {
            throw new ResourceNotFoundException("Conversation not found");
        }
        requireMember(conversationId, user.getId());

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Page<Message> result = messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(
                conversationId, PageRequest.of(safePage, safeSize));

        return PageResponse.from(result.map(MessageResponse::from));
    }

    private void requireMember(Long conversationId, Long userId) {
        if (!memberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new ForbiddenException("You are not a member of this conversation");
        }
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
