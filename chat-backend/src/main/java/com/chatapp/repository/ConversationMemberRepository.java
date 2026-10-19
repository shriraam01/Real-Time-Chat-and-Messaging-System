package com.chatapp.repository;

import com.chatapp.entity.ConversationMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    Optional<ConversationMember> findByConversationIdAndUserId(Long conversationId, Long userId);

    Optional<ConversationMember> findByConversationIdAndUserUsername(Long conversationId, String username);

    @EntityGraph(attributePaths = {"user"})
    List<ConversationMember> findByConversationId(Long conversationId);

    @EntityGraph(attributePaths = {"conversation"})
    List<ConversationMember> findByUserId(Long userId);

    List<ConversationMember> findByUserUsername(String username);

    // plain usernames, so it also works outside a transaction (WebSocket handlers, listeners)
    @Query("select m.user.username from ConversationMember m where m.conversation.id = :conversationId")
    List<String> findUsernamesByConversationId(@Param("conversationId") Long conversationId);

    // everyone who shares at least one conversation with the given user (used for presence updates)
    @Query("""
            select distinct m2.user.username
            from ConversationMember m1, ConversationMember m2
            where m1.user.username = :username
              and m2.conversation = m1.conversation
              and m2.user.username <> :username
            """)
    List<String> findCoMemberUsernames(@Param("username") String username);
}
