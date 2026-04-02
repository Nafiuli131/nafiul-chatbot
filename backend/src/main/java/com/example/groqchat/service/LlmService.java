package com.example.groqchat.service;

import com.example.groqchat.agent.AgentRouter;
import com.example.groqchat.dto.AgentResponse;
import com.example.groqchat.dto.Message;
import com.example.groqchat.entity.ChatMessageEntity;
import com.example.groqchat.entity.ChatSession;
import com.example.groqchat.repository.ChatMessageRepository;
import com.example.groqchat.repository.ChatSessionRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    private final ChatLanguageModel chatLanguageModel;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final AgentRouter agentRouter;

    // ── Simple one-shot chat (no session) ─────────────────────
    public String chat(String userMessage, String systemPrompt) {
        List<ChatMessage> messages = List.of(
                new SystemMessage(systemPrompt != null ? systemPrompt : "You are a helpful assistant."),
                new UserMessage(userMessage)
        );
        return callLlm(messages);
    }

    // ── Multi-agent chat with memory ────────────────────────────
    @Transactional
    public AgentResponse chatWithMemory(String sessionId, String userMessage, String systemPrompt, Long userId) {
        String basePrompt = systemPrompt != null ? systemPrompt : "You are a helpful assistant.";

        if (!sessionRepository.existsById(sessionId)) {
            sessionRepository.save(new ChatSession(sessionId, basePrompt, userId));
            messageRepository.save(new ChatMessageEntity(sessionId, "system", basePrompt));
            log.info("New session created: {} for userId: {}", sessionId, userId);
        }

        messageRepository.save(new ChatMessageEntity(sessionId, "user", userMessage));

        // Load conversation history for context
        List<ChatMessage> history = loadHistory(sessionId);

        // Route through multi-agent system (returns response + guardrail metadata)
        AgentResponse agentResponse;
        try {
            agentResponse = agentRouter.route(userMessage, history);
        } catch (Exception e) {
            log.error("Agent router error: {}", e.getMessage(), e);
            agentResponse = new AgentResponse("Sorry, I encountered an error. Please try again.", false, List.of());
        }

        messageRepository.save(new ChatMessageEntity(sessionId, "assistant", agentResponse.getResponse()));

        log.info("Session [{}] — agent response generated", sessionId);
        return agentResponse;
    }

    // ── Session management ─────────────────────────────────────
    @Transactional
    public void clearSession(String sessionId, Long userId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getUserId().equals(userId)) {
                messageRepository.deleteBySessionId(sessionId);
                sessionRepository.deleteById(sessionId);
                log.info("Session cleared: {}", sessionId);
            }
        });
    }

    public Set<String> getActiveSessions(Long userId) {
        return sessionRepository.findByUserId(userId)
                .stream()
                .map(ChatSession::getId)
                .collect(Collectors.toSet());
    }

    public List<Map<String, Object>> getSessionsWithInfo(Long userId) {
        return sessionRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(ChatSession::getCreatedAt).reversed())
                .map(session -> {
                    String name = messageRepository
                            .findBySessionIdOrderByCreatedAtAscIdAsc(session.getId())
                            .stream()
                            .filter(m -> "user".equals(m.getRole()))
                            .findFirst()
                            .map(m -> m.getContent().length() > 30
                                    ? m.getContent().substring(0, 30) + "…"
                                    : m.getContent())
                            .orElse("New Chat");
                    Map<String, Object> info = new HashMap<>();
                    info.put("id", session.getId());
                    info.put("name", name);
                    return info;
                })
                .collect(Collectors.toList());
    }

    public List<Message> getSessionMessages(String sessionId, Long userId) {
        return sessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .map(s -> messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId)
                        .stream()
                        .filter(m -> !"system".equals(m.getRole()))
                        .map(m -> new Message(m.getRole(), m.getContent()))
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    public int getSessionSize(String sessionId) {
        return messageRepository.countBySessionId(sessionId);
    }

    // ── Helpers ────────────────────────────────────────────────
    private List<ChatMessage> loadHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId)
                .stream()
                .map(e -> (ChatMessage) switch (e.getRole()) {
                    case "system" -> new SystemMessage(e.getContent());
                    case "assistant" -> new AiMessage(e.getContent());
                    default -> new UserMessage(e.getContent());
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String callLlm(List<ChatMessage> messages) {
        try {
            ChatResponse response = chatLanguageModel.chat(messages);
            return response.aiMessage().text();
        } catch (Exception e) {
            log.error("LLM API error: {}", e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
