package com.example.groqchat.repository;

import com.example.groqchat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    List<ChatSession> findByUserId(Long userId);
}
