package com.example.groqchat.service;

import com.example.groqchat.config.GroqConfig;
import com.example.groqchat.dto.ChatRequest;
import com.example.groqchat.dto.ChatResponse;
import com.example.groqchat.dto.Message;
import com.example.groqchat.entity.ChatMessageEntity;
import com.example.groqchat.entity.ChatSession;
import com.example.groqchat.repository.ChatMessageRepository;
import com.example.groqchat.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmService {

    private final WebClient groqWebClient;
    private final GroqConfig groqConfig;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    // ── Simple one-shot chat (no session) ─────────────────────
    public String chat(String userMessage, String systemPrompt) {
        List<Message> messages = List.of(
                new Message("system", systemPrompt != null ? systemPrompt : "You are a helpful assistant."),
                new Message("user", userMessage)
        );
        return callGroq(messages);
    }

    // ── Multi-user chat with memory ────────────────────────────
    @Transactional
    public String chatWithMemory(String sessionId, String userMessage, String systemPrompt, Long userId) {
        if (!sessionRepository.existsById(sessionId)) {
            String prompt = systemPrompt != null ? systemPrompt : "You are a helpful assistant.";
            sessionRepository.save(new ChatSession(sessionId, prompt, userId));
            messageRepository.save(new ChatMessageEntity(sessionId, "system", prompt));
            log.info("New session created: {} for userId: {}", sessionId, userId);
        }

        messageRepository.save(new ChatMessageEntity(sessionId, "user", userMessage));

        List<Message> history = loadHistory(sessionId);
        String response = callGroq(history);

        messageRepository.save(new ChatMessageEntity(sessionId, "assistant", response));

        log.info("Session [{}] — {} messages", sessionId, history.size() + 1);
        return response;
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
    private List<Message> loadHistory(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId)
                .stream()
                .map(e -> new Message(e.getRole(), e.getContent()))
                .collect(Collectors.toList());
    }

    private String callGroq(List<Message> messages) {
        ChatRequest request = new ChatRequest(
                groqConfig.getModel(),
                messages,
                groqConfig.getTemperature(),
                groqConfig.getMaxTokens()
        );

        try {
            ChatResponse response = groqWebClient
                    .post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ChatResponse.class)
                    .block();

            if (response != null && !response.getChoices().isEmpty()) {
                return response.getChoices().get(0).getMessage().getContent();
            }
            return "No response from Groq.";

        } catch (WebClientResponseException e) {
            log.error("Groq API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "Error: " + e.getStatusCode() + " — " + e.getResponseBodyAsString();
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage());
            return "Unexpected error: " + e.getMessage();
        }
    }
}
