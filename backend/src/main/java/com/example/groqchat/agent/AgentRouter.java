package com.example.groqchat.agent;

import com.example.groqchat.agent.tools.DateTimeTool;
import com.example.groqchat.agent.tools.RagTool;
import com.example.groqchat.agent.tools.WeatherTool;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AgentRouter {

    private final ChatLanguageModel chatLanguageModel;
    private final RagTool ragTool;
    private final WeatherTool weatherTool;
    private final DateTimeTool dateTimeTool;

    public AgentRouter(ChatLanguageModel chatLanguageModel, RagTool ragTool,
                       WeatherTool weatherTool, DateTimeTool dateTimeTool) {
        this.chatLanguageModel = chatLanguageModel;
        this.ragTool = ragTool;
        this.weatherTool = weatherTool;
        this.dateTimeTool = dateTimeTool;
    }

    // Keyword patterns for fast routing (no LLM call needed)
    private static final Pattern WEATHER_PATTERN = Pattern.compile(
            "(?i).*(weather|temperature|forecast|humid|rain|snow|sunny|cloudy|storm).*");
    private static final Pattern DATETIME_PATTERN = Pattern.compile(
            "(?i).*(what time|current time|what date|current date|today's date|time in |date in |timezone|time zone).*");

    private static final String CLASSIFIER_PROMPT = """
            Classify this message into ONE category. Reply with ONLY the JSON, nothing else.
            Categories: WEATHER, DATETIME, RAG, GENERAL
            Format: {"agent":"CATEGORY","param":"extracted search query or location"}
            """;

    public String route(String userMessage, List<ChatMessage> history) {
        String agent;
        String param;

        // Step 1: Fast keyword-based routing (saves 1 LLM call for obvious cases)
        if (WEATHER_PATTERN.matcher(userMessage).matches()) {
            agent = "WEATHER";
            param = extractLocationFromMessage(userMessage);
            log.info("Fast route → WEATHER for: {}", param);
        } else if (DATETIME_PATTERN.matcher(userMessage).matches()) {
            agent = "DATETIME";
            param = extractLocationFromMessage(userMessage);
            log.info("Fast route → DATETIME for: {}", param);
        } else {
            // Only call LLM classifier when keywords don't match
            String classification = classifyIntent(userMessage);
            log.info("LLM classification: {}", classification);
            agent = extractField(classification, "agent");
            param = extractField(classification, "param");
        }

        // Step 2: Execute the appropriate tool
        String toolResult = null;
        switch (agent) {
            case "WEATHER" -> {
                log.info("Routing to Weather Agent for: {}", param);
                toolResult = weatherTool.getWeather(param.isEmpty() ? userMessage : param);
            }
            case "DATETIME" -> {
                log.info("Routing to DateTime Agent for: {}", param);
                toolResult = dateTimeTool.getDateTime(param.isEmpty() ? userMessage : param);
            }
            case "RAG" -> {
                log.info("Routing to RAG Agent for: {}", param);
                String ragContext = ragTool.queryDocuments(param.isEmpty() ? userMessage : param);
                if (!ragContext.equals("No relevant documents found for this query.")) {
                    toolResult = ragContext;
                }
            }
            default -> log.info("General chat (no tool)");
        }

        // Step 3: Generate final response
        return generateResponse(userMessage, toolResult, history);
    }

    private String classifyIntent(String userMessage) {
        try {
            List<ChatMessage> messages = List.of(
                    new SystemMessage(CLASSIFIER_PROMPT),
                    new UserMessage(userMessage)
            );
            return chatLanguageModel.chat(messages).aiMessage().text().trim();
        } catch (Exception e) {
            log.error("Classification failed: {}", e.getMessage());
            return "{\"agent\":\"GENERAL\",\"param\":\"\"}";
        }
    }

    private String generateResponse(String userMessage, String toolResult, List<ChatMessage> history) {
        String systemPrompt;
        if (toolResult != null) {
            systemPrompt = "You are a helpful assistant. Answer based on this context:\n\n" + toolResult;
        } else {
            systemPrompt = "You are a helpful assistant.";
        }

        List<ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        // Add last 10 messages from history (not all — saves tokens)
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
        // Remove common question words to extract the location
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
            if (keyIndex == -1) return "";

            int colonIndex = json.indexOf(":", keyIndex);
            if (colonIndex == -1) return "";

            int startQuote = json.indexOf("\"", colonIndex + 1);
            if (startQuote == -1) return "";

            int endQuote = json.indexOf("\"", startQuote + 1);
            if (endQuote == -1) return "";

            return json.substring(startQuote + 1, endQuote);
        } catch (Exception e) {
            log.warn("Failed to extract '{}' from: {}", field, json);
            return "";
        }
    }
}
