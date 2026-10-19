package com.chatapp.repository;

import com.chatapp.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @EntityGraph(attributePaths = {"sender"})
    Page<Message> findByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId, Pageable pageable);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId);

    @Query("select max(m.id) from Message m where m.conversation.id = :conversationId")
    Long findLatestMessageId(@Param("conversationId") Long conversationId);

    // messages from other people that the user has not read yet
    @Query("""
            select count(m) from Message m
            where m.conversation.id = :conversationId
              and m.id > :afterId
              and m.sender.id <> :userId
            """)
    long countUnread(@Param("conversationId") Long conversationId,
                     @Param("afterId") long afterId,
                     @Param("userId") Long userId);
}
