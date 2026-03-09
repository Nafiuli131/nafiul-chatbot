package com.example.groqchat.repository;

import com.example.groqchat.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAscIdAsc(String sessionId);

    void deleteBySessionId(String sessionId);

    int countBySessionId(String sessionId);
}
