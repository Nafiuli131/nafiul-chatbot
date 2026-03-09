package com.example.groqchat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_sessions")
@Getter
@Setter
@NoArgsConstructor
public class ChatSession {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false)
    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ChatSession(String id, String systemPrompt, Long userId) {
        this.id = id;
        this.systemPrompt = systemPrompt;
        this.userId = userId;
    }
}
