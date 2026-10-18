package com.chatapp.repository;

import com.chatapp.entity.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {
    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);
    List<ConversationMember> findByConversationId(Long conversationId);
    List<ConversationMember> findByUserId(Long userId);

    // returns plain usernames, so it works outside a transaction (e.g. in the WebSocket handler)
    @Query("select m.user.username from ConversationMember m where m.conversation.id = :conversationId")
    List<String> findUsernamesByConversationId(@Param("conversationId") Long conversationId);
}
