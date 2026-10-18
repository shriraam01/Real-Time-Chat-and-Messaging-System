package com.chatapp.repository;

import com.chatapp.entity.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {
    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);
    List<ConversationMember> findByConversationId(Long conversationId);
    List<ConversationMember> findByUserId(Long userId);
}
