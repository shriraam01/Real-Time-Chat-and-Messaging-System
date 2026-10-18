package com.chatapp.repository;

import com.chatapp.entity.Conversation;
import com.chatapp.entity.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("""
            select c from Conversation c
            where c.type = :type
              and exists (select 1 from ConversationMember m1
                          where m1.conversation = c and m1.user.id = :userA)
              and exists (select 1 from ConversationMember m2
                          where m2.conversation = c and m2.user.id = :userB)
            """)
    Optional<Conversation> findDirectConversation(@Param("type") ConversationType type,
                                                  @Param("userA") Long userA,
                                                  @Param("userB") Long userB);
}
