package com.example.groqchat.controller;

import com.example.groqchat.dto.AgentResponse;
import com.example.groqchat.dto.UserMessage;
import com.example.groqchat.entity.User;
import com.example.groqchat.service.GuardrailService;
import com.example.groqchat.service.LlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ChatController {

    private final LlmService llmService;
    private final GuardrailService guardrailService;

    // POST /api/chat — one-shot, no memory
    @PostMapping("/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody UserMessage body) {
        String response = llmService.chat(body.getMessage(), body.getSystemPrompt());
        return ResponseEntity.ok(Map.of("response", response));
    }

    // POST /api/chat/memory — with sessionId-based memory (scoped to authenticated user)
    @PostMapping("/chat/memory")
    public ResponseEntity<?> chatWithMemory(@RequestBody UserMessage body,
                                            @AuthenticationPrincipal User currentUser) {
        String sessionId = body.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sessionId is required"));
        }

        AgentResponse agentResponse = llmService.chatWithMemory(
                sessionId, body.getMessage(), body.getSystemPrompt(), currentUser.getId());

        Map<String, Object> responseMap = new java.util.LinkedHashMap<>();
        responseMap.put("sessionId", sessionId);
        responseMap.put("response", agentResponse.getResponse());
        responseMap.put("blocked", agentResponse.isBlocked());
        responseMap.put("guardrails", agentResponse.getGuardrails());
        responseMap.put("historySize", String.valueOf(llmService.getSessionSize(sessionId)));

        return ResponseEntity.ok(responseMap);
    }

    // GET /api/guardrails/status — see all active guardrails and their config
    @GetMapping("/guardrails/status")
    public ResponseEntity<Map<String, Object>> guardrailsStatus() {
        Map<String, Object> status = new java.util.LinkedHashMap<>();
        status.put("guardrails", List.of(
                Map.of("name", "InputLength", "type", "INPUT", "description", "Blocks messages exceeding max character limit"),
                Map.of("name", "PromptInjection", "type", "INPUT", "description", "Detects prompt injection and jailbreak attempts"),
                Map.of("name", "HarmfulContent", "type", "INPUT", "description", "Blocks requests for weapons, malware, hacking"),
                Map.of("name", "HallucinationCheck", "type", "RAG", "description", "Verifies RAG answers are grounded in retrieved documents"),
                Map.of("name", "SensitiveDataRedaction", "type", "OUTPUT", "description", "Redacts emails, phone numbers, SSNs, API keys, credit cards")
        ));
        status.put("totalGuardrails", 5);
        return ResponseEntity.ok(status);
    }

    // DELETE /api/chat/session/{sessionId} — clear session (only owner can delete)
    @DeleteMapping("/chat/session/{sessionId}")
    public ResponseEntity<Map<String, String>> clearSession(@PathVariable String sessionId,
                                                            @AuthenticationPrincipal User currentUser) {
        llmService.clearSession(sessionId, currentUser.getId());
        return ResponseEntity.ok(Map.of("status", "cleared", "sessionId", sessionId));
    }

    // GET /api/chat/sessions — list sessions with names for the authenticated user
    @GetMapping("/chat/sessions")
    public ResponseEntity<Map<String, Object>> getSessions(@AuthenticationPrincipal User currentUser) {
        List<Map<String, Object>> sessions = llmService.getSessionsWithInfo(currentUser.getId());
        return ResponseEntity.ok(Map.of("sessions", sessions));
    }

    // GET /api/chat/session/{sessionId}/messages — get message history for a session
    @GetMapping("/chat/session/{sessionId}/messages")
    public ResponseEntity<Map<String, Object>> getSessionMessages(@PathVariable String sessionId,
                                                                  @AuthenticationPrincipal User currentUser) {
        var messages = llmService.getSessionMessages(sessionId, currentUser.getId());
        return ResponseEntity.ok(Map.of("messages", messages));
    }

    // GET /api/health
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
