package com.example.groqchat.agent;

import com.example.groqchat.agent.tools.DateTimeTool;
import com.example.groqchat.agent.tools.RagTool;
import com.example.groqchat.agent.tools.WeatherTool;
import com.example.groqchat.dto.AgentResponse;
import com.example.groqchat.dto.GuardrailResult;
import com.example.groqchat.service.GuardrailService;
import com.example.groqchat.service.HallucinationDetector;
import com.example.groqchat.service.RagService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AgentRouter {

    private final ChatLanguageModel chatLanguageModel;
    private final RagTool ragTool;
    private final WeatherTool weatherTool;
    private final DateTimeTool dateTimeTool;
    private final GuardrailService guardrailService;
    private final HallucinationDetector hallucinationDetector;

    public AgentRouter(ChatLanguageModel chatLanguageModel, RagTool ragTool,
                       WeatherTool weatherTool, DateTimeTool dateTimeTool,
                       GuardrailService guardrailService, HallucinationDetector hallucinationDetector) {
        this.chatLanguageModel = chatLanguageModel;
        this.ragTool = ragTool;
        this.weatherTool = weatherTool;
        this.dateTimeTool = dateTimeTool;
        this.guardrailService = guardrailService;
        this.hallucinationDetector = hallucinationDetector;
    }

    // ── Keyword patterns for fast tool routing ─────────────────
    private static final Pattern WEATHER_PATTERN = Pattern.compile(
            "(?i).*(weather|temperature|forecast|humid|rain|snow|sunny|cloudy|storm).*");
    private static final Pattern DATETIME_PATTERN = Pattern.compile(
            "(?i).*(what time|current time|what date|current date|today's date|time in |date in |timezone|time zone).*");

    // ── Classifier: only WEATHER vs DATETIME vs GENERAL ────────
    // RAG is NOT a category — it's always checked automatically
    private static final String CLASSIFIER_PROMPT = """
            Classify this user message into ONE category. Reply with ONLY the JSON.

            Categories:
            - WEATHER: asking about weather, temperature, forecast for a location
            - DATETIME: asking about current time, date, or timezone for a location
            - GENERAL: everything else (any question, knowledge, personal, etc.)

            Format: {"agent":"CATEGORY","param":"extracted location or search query"}
            """;

    // ── System prompts for each mode ───────────────────────────
    private static final String RAG_SYSTEM_PROMPT = """
            You are a helpful assistant with access to specific documents.
            Use the following document context to answer the user's question accurately.
            Base your answer on the context provided. If the context contains relevant information, use it.

            Document Context:
            """;

    private static final String TOOL_SYSTEM_PROMPT =
            "You are a helpful assistant. Use the following real-time data to answer the user's question naturally:\n\n";

    private static final String GENERAL_SYSTEM_PROMPT =
            "You are a helpful, knowledgeable assistant. Answer the user's question using your general knowledge. Be accurate, detailed, and helpful.";

    /**
     * Main routing logic:
     *
     * 1. Input guardrails → block unsafe input
     * 2. Route to WEATHER/DATETIME tool if detected
     * 3. For GENERAL queries:
     *    a. Search RAG → if relevant docs found, answer from docs + hallucination check
     *    b. If RAG finds nothing → answer from general knowledge (no hallucination check)
     *    c. If hallucination check says NOT_GROUNDED → fallback to general knowledge
     * 4. Output guardrails → redact sensitive data
     */
    public AgentResponse route(String userMessage, List<ChatMessage> history) {
        List<GuardrailResult> allGuardrails = new ArrayList<>();

        // ═══ INPUT GUARDRAILS ══════════════════════════════════
        List<GuardrailResult> inputResults = guardrailService.validateInput(userMessage);
        allGuardrails.addAll(inputResults);
        if (guardrailService.isBlocked(inputResults)) {
            log.info("Input blocked by guardrails");
            return new AgentResponse(guardrailService.getBlockMessage(inputResults), true, allGuardrails);
        }

        // ═══ CLASSIFY INTENT ═══════════════════════════════════
        String agent = classifyAgent(userMessage);
        String param = "";

        // ═══ ROUTE: WEATHER TOOL ═══════════════════════════════
        if ("WEATHER".equals(agent)) {
            param = extractLocationFromMessage(userMessage);
            log.info("→ WEATHER tool for: {}", param);

            String toolData = weatherTool.getWeather(param.isEmpty() ? userMessage : param);
            String response = generateResponse(userMessage, TOOL_SYSTEM_PROMPT + toolData, history);

            allGuardrails.add(new GuardrailResult("ToolUsed", "WEATHER", "Weather tool executed for: " + param));
            allGuardrails.add(new GuardrailResult("HallucinationCheck", "SKIPPED", "Tool query — no hallucination check needed"));
            return finishWithOutputGuardrails(response, allGuardrails);
        }

        // ═══ ROUTE: DATETIME TOOL ══════════════════════════════
        if ("DATETIME".equals(agent)) {
            param = extractLocationFromMessage(userMessage);
            log.info("→ DATETIME tool for: {}", param);

            String toolData = dateTimeTool.getDateTime(param.isEmpty() ? userMessage : param);
            String response = generateResponse(userMessage, TOOL_SYSTEM_PROMPT + toolData, history);

            allGuardrails.add(new GuardrailResult("ToolUsed", "DATETIME", "DateTime tool executed for: " + param));
            allGuardrails.add(new GuardrailResult("HallucinationCheck", "SKIPPED", "Tool query — no hallucination check needed"));
            return finishWithOutputGuardrails(response, allGuardrails);
        }

        // ═══ ROUTE: GENERAL (RAG-first, then fallback) ═════════
        log.info("→ GENERAL query — checking RAG documents first...");

        // Step A: Search RAG
        RagService.RagResult ragResult = ragTool.queryDocuments(userMessage);

        if (ragResult.hasResults()) {
            // Step B: RAG found relevant documents → answer from them
            log.info("RAG hit: {} chunks, bestScore={}", ragResult.matchCount(), String.format("%.3f", ragResult.bestScore()));

            String ragResponse = generateResponse(userMessage, RAG_SYSTEM_PROMPT + ragResult.context(), history);
            allGuardrails.add(new GuardrailResult("RAGSearch", "HIT",
                    "Found " + ragResult.matchCount() + " relevant chunks (bestScore=" + String.format("%.3f", ragResult.bestScore()) + ")"));

            // Step C: Hallucination check on RAG response
            HallucinationDetector.HallucinationResult halResult =
                    hallucinationDetector.verify(userMessage, ragResult.context(), ragResponse);
            allGuardrails.add(halResult.guardrailResult());

            String verdict = halResult.guardrailResult().getStatus();

            if ("NOT_GROUNDED".equals(verdict)) {
                // RAG context was not relevant to the question → fall back to general knowledge
                log.info("Hallucination check: NOT_GROUNDED → falling back to general knowledge");
                String generalResponse = generateResponse(userMessage, GENERAL_SYSTEM_PROMPT, history);
                allGuardrails.add(new GuardrailResult("GeneralFallback", "ACTIVATED",
                        "RAG context was not relevant to the question — answered using general knowledge"));
                return finishWithOutputGuardrails(generalResponse, allGuardrails);
            }

            // GROUNDED or PARTIALLY_GROUNDED → use the RAG response
            return finishWithOutputGuardrails(halResult.response(), allGuardrails);

        } else {
            // Step D: RAG found nothing → answer from general knowledge directly
            log.info("RAG miss: no relevant documents → using general knowledge");

            String generalResponse = generateResponse(userMessage, GENERAL_SYSTEM_PROMPT, history);
            allGuardrails.add(new GuardrailResult("RAGSearch", "MISS", "No relevant documents found"));
            allGuardrails.add(new GuardrailResult("HallucinationCheck", "SKIPPED",
                    "No RAG context — answered using general knowledge"));
            return finishWithOutputGuardrails(generalResponse, allGuardrails);
        }
    }

    // ── Apply output guardrails and return final response ──────
    private AgentResponse finishWithOutputGuardrails(String response, List<GuardrailResult> guardrails) {
        GuardrailService.OutputSanitizationResult sanitized = guardrailService.sanitizeOutput(response);
        guardrails.add(sanitized.guardrailResult());
        return new AgentResponse(sanitized.sanitizedResponse(), false, guardrails);
    }

    // ── Classify: WEATHER / DATETIME / GENERAL ─────────────────
    private String classifyAgent(String userMessage) {
        // Fast keyword match first (saves an LLM call)
        if (WEATHER_PATTERN.matcher(userMessage).matches()) {
            log.info("Fast route → WEATHER");
            return "WEATHER";
        }
        if (DATETIME_PATTERN.matcher(userMessage).matches()) {
            log.info("Fast route → DATETIME");
            return "DATETIME";
        }

        // LLM classifier for ambiguous cases
        try {
            List<ChatMessage> messages = List.of(
                    new SystemMessage(CLASSIFIER_PROMPT),
                    new UserMessage(userMessage)
            );
            String classification = chatLanguageModel.chat(messages).aiMessage().text().trim();
            log.info("LLM classification: {}", classification);
            return extractField(classification, "agent");
        } catch (Exception e) {
            log.error("Classification failed: {}", e.getMessage());
            return "GENERAL";
        }
    }

    // ── Generate LLM response with system prompt + history ─────
    private String generateResponse(String userMessage, String systemPrompt, List<ChatMessage> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        // Add last 10 messages from history
        List<ChatMessage> recentHistory = history.stream()
                .filter(msg -> !(msg instanceof SystemMessage))
                .toList();
        int start = Math.max(0, recentHistory.size() - 10);
        for (int i = start; i < recentHistory.size(); i++) {
            messages.add(recentHistory.get(i));
        }

        messages.add(new UserMessage(userMessage));
        return chatLanguageModel.chat(messages).aiMessage().text();
    }

    private String extractLocationFromMessage(String message) {
        return message
                .replaceAll("(?i)(what('s| is) the |how('s| is) the |tell me the |get me the )", "")
                .replaceAll("(?i)(weather|temperature|forecast|current time|current date|time|date) (in |of |for |at )", "")
                .replaceAll("(?i)(what |whats |what's )(time|date) (in |of |for )", "")
                .replaceAll("[?!.,]", "")
                .trim();
    }

    private String extractField(String json, String field) {
        try {
            String key = "\"" + field + "\"";
            int keyIndex = json.indexOf(key);
            if (keyIndex == -1) return "GENERAL";

            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1) return "GENERAL";

            int startQuote = json.indexOf("\"", colonIndex + 1);
            if (startQuote == -1) return "GENERAL";

            int endQuote = json.indexOf("\"", startQuote + 1);
            if (endQuote == -1) return "GENERAL";

            return json.substring(startQuote + 1, endQuote);
        } catch (Exception e) {
            log.warn("Failed to extract '{}' from: {}", field, json);
            return "GENERAL";
        }
    }
}
