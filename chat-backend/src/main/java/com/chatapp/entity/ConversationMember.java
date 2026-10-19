package com.chatapp.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One row per (conversation, user). Besides membership it stores two "watermarks":
 * the id of the newest message this member has received and the newest one they have read.
 * Message ticks (sent / delivered / read) are worked out from these two numbers.
 */
@Entity
@Table(name = "conversation_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"conversation_id", "user_id"}))
public class ConversationMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private Long lastDeliveredMessageId;

    private Long lastReadMessageId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
    }

    public ConversationMember() {}

    public ConversationMember(Conversation conversation, User user) {
        this.conversation = conversation;
        this.user = user;
    }

    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public User getUser() { return user; }
    public Long getLastDeliveredMessageId() { return lastDeliveredMessageId; }
    public void setLastDeliveredMessageId(Long lastDeliveredMessageId) { this.lastDeliveredMessageId = lastDeliveredMessageId; }
    public Long getLastReadMessageId() { return lastReadMessageId; }
    public void setLastReadMessageId(Long lastReadMessageId) { this.lastReadMessageId = lastReadMessageId; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
}
