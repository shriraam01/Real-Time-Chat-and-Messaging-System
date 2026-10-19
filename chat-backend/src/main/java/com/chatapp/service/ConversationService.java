package com.chatapp.service;

import com.chatapp.dto.ConversationResponse;
import com.chatapp.dto.MemberResponse;
import com.chatapp.dto.MessageResponse;
import com.chatapp.entity.Conversation;
import com.chatapp.entity.ConversationMember;
import com.chatapp.entity.ConversationType;
import com.chatapp.entity.User;
import com.chatapp.exception.BadRequestException;
import com.chatapp.exception.DuplicateResourceException;
import com.chatapp.exception.ForbiddenException;
import com.chatapp.exception.ResourceNotFoundException;
import com.chatapp.repository.ConversationMemberRepository;
import com.chatapp.repository.ConversationRepository;
import com.chatapp.repository.MessageRepository;
import com.chatapp.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final PresenceService presenceService;

    public ConversationService(ConversationRepository conversationRepository,
                               ConversationMemberRepository memberRepository,
                               MessageRepository messageRepository,
                               UserRepository userRepository,
                               PresenceService presenceService) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.presenceService = presenceService;
    }

    // ---------- one-to-one ----------

    @Transactional
    public ConversationResponse startDirectConversation(String currentUsername, String otherUsername) {
        User current = findUser(currentUsername);
        User other = findUser(otherUsername.trim());
        if (current.getId().equals(other.getId())) {
            throw new BadRequestException("You cannot start a conversation with yourself");
        }

        // reuse the existing chat if these two users already have one
        Conversation conversation = conversationRepository
                .findDirectConversation(ConversationType.ONE_TO_ONE, current.getId(), other.getId())
                .orElseGet(() -> createDirectConversation(current, other));

        return toResponse(conversation, current);
    }

    // ---------- groups ----------

    @Transactional
    public ConversationResponse createGroup(String creatorUsername, String name, List<String> usernames) {
        String groupName = name == null ? "" : name.trim();
        if (groupName.isEmpty()) {
            throw new BadRequestException("Group name is required");
        }
        if (groupName.length() > 100) {
            throw new BadRequestException("Group name must be at most 100 characters");
        }

        User creator = findUser(creatorUsername);

        // keyed by id so the same person can't be added twice (even with different upper/lower case)
        Map<Long, User> others = new LinkedHashMap<>();
        if (usernames != null) {
            for (String username : usernames) {
                if (username == null || username.isBlank()) {
                    continue;
                }
                User user = findUser(username.trim());
                if (!user.getId().equals(creator.getId())) {
                    others.put(user.getId(), user);
                }
            }
        }
        if (others.isEmpty()) {
            throw new BadRequestException("Add at least one other member");
        }

        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.GROUP);
        conversation.setName(groupName);
        conversation.setCreatedBy(creator);
        Conversation saved = conversationRepository.save(conversation);

        memberRepository.save(new ConversationMember(saved, creator));
        for (User user : others.values()) {
            memberRepository.save(new ConversationMember(saved, user));
        }

        return toResponse(saved, creator);
    }

    @Transactional
    public ConversationResponse addMember(Long conversationId, String actingUsername, String newMemberUsername) {
        Conversation conversation = findConversation(conversationId);
        User acting = findUser(actingUsername);
        requireGroup(conversation);
        requireMember(conversationId, acting.getId());
        requireCreator(conversation, acting);

        User newUser = findUser(newMemberUsername.trim());
        if (memberRepository.existsByConversationIdAndUserId(conversationId, newUser.getId())) {
            throw new DuplicateResourceException("User is already in this group");
        }

        // a new member starts "caught up", so old messages don't show up as unread
        // and don't hold back the read ticks of earlier messages
        Long latest = messageRepository.findLatestMessageId(conversationId);
        ConversationMember member = new ConversationMember(conversation, newUser);
        member.setLastDeliveredMessageId(latest);
        member.setLastReadMessageId(latest);
        memberRepository.save(member);

        return toResponse(conversation, acting);
    }

    /** The creator can remove anyone; any member can remove themselves (leave). */
    @Transactional
    public void removeMember(Long conversationId, String actingUsername, String targetUsername) {
        Conversation conversation = findConversation(conversationId);
        User acting = findUser(actingUsername);
        User target = findUser(targetUsername.trim());
        requireGroup(conversation);
        requireMember(conversationId, acting.getId());

        boolean leavingSelf = acting.getId().equals(target.getId());
        if (!leavingSelf) {
            requireCreator(conversation, acting);
        }

        ConversationMember targetMember = memberRepository
                .findByConversationIdAndUserId(conversationId, target.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User is not in this group"));
        memberRepository.delete(targetMember);

        // if the creator left or was removed, the longest-standing remaining member becomes the admin
        if (isCreator(conversation, target)) {
            memberRepository.findByConversationId(conversationId).stream()
                    .min(Comparator.comparing(ConversationMember::getId))
                    .ifPresent(next -> conversation.setCreatedBy(next.getUser()));
        }
    }

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public List<ConversationResponse> listForUser(String username) {
        User user = findUser(username);
        return memberRepository.findByUserId(user.getId()).stream()
                .map(ConversationMember::getConversation)
                .map(conversation -> toResponse(conversation, user))
                .sorted(Comparator.comparing(ConversationService::lastActivity, Comparator.reverseOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(Long conversationId, String username) {
        User user = findUser(username);
        Conversation conversation = findConversation(conversationId);
        requireMember(conversationId, user.getId());
        return toResponse(conversation, user);
    }

    // ---------- helpers ----------

    private Conversation createDirectConversation(User a, User b) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.ONE_TO_ONE);
        Conversation saved = conversationRepository.save(conversation);

        memberRepository.save(new ConversationMember(saved, a));
        memberRepository.save(new ConversationMember(saved, b));
        return saved;
    }

    private ConversationResponse toResponse(Conversation conversation, User viewer) {
        List<ConversationMember> members = memberRepository.findByConversationId(conversation.getId()).stream()
                .sorted(Comparator.comparing(ConversationMember::getId))
                .toList();

        List<MemberResponse> memberResponses = members.stream()
                .map(this::toMemberResponse)
                .toList();

        long viewerLastRead = 0L;
        for (ConversationMember member : members) {
            if (member.getUser().getId().equals(viewer.getId()) && member.getLastReadMessageId() != null) {
                viewerLastRead = member.getLastReadMessageId();
            }
        }
        long unread = messageRepository.countUnread(conversation.getId(), viewerLastRead, viewer.getId());

        MessageResponse lastMessage = messageRepository
                .findFirstByConversationIdOrderByCreatedAtDescIdDesc(conversation.getId())
                .map(MessageResponse::from)
                .orElse(null);

        String createdBy = conversation.getCreatedBy() != null ? conversation.getCreatedBy().getUsername() : null;

        return new ConversationResponse(conversation.getId(), conversation.getType(), conversation.getName(),
                createdBy, memberResponses, lastMessage, unread, conversation.getCreatedAt());
    }

    private MemberResponse toMemberResponse(ConversationMember member) {
        User user = member.getUser();
        return new MemberResponse(user.getId(), user.getUsername(),
                presenceService.isOnline(user.getUsername()), user.getLastSeen(),
                member.getLastDeliveredMessageId(), member.getLastReadMessageId());
    }

    private static LocalDateTime lastActivity(ConversationResponse response) {
        return response.lastMessage() != null ? response.lastMessage().createdAt() : response.createdAt();
    }

    private boolean isCreator(Conversation conversation, User user) {
        return conversation.getCreatedBy() != null && conversation.getCreatedBy().getId().equals(user.getId());
    }

    private void requireCreator(Conversation conversation, User user) {
        if (!isCreator(conversation, user)) {
            throw new ForbiddenException("Only the group creator can do this");
        }
    }

    private void requireGroup(Conversation conversation) {
        if (conversation.getType() != ConversationType.GROUP) {
            throw new BadRequestException("This is not a group conversation");
        }
    }

    private void requireMember(Long conversationId, Long userId) {
        if (!memberRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new ForbiddenException("You are not a member of this conversation");
        }
    }

    private Conversation findConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }
}
