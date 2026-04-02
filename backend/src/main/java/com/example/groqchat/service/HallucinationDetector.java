package com.example.groqchat.service;

import com.example.groqchat.dto.GuardrailResult;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class HallucinationDetector {

    private final ChatLanguageModel chatLanguageModel;

    @Value("${guardrails.hallucination-check:true}")
    private boolean hallucinationCheckEnabled;

    public HallucinationDetector(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    private static final String VERIFICATION_PROMPT = """
            You are a hallucination detection system. Your job is to verify whether an AI-generated answer
            is factually supported by the provided context.

            Rules:
            - Compare the ANSWER against the CONTEXT only.
            - If the answer is fully supported by the context, respond: GROUNDED
            - If the answer is partially supported (some claims lack evidence), respond: PARTIALLY_GROUNDED
            - If the answer contains claims not found in the context or contradicts it, respond: NOT_GROUNDED

            Respond with ONLY one word: GROUNDED, PARTIALLY_GROUNDED, or NOT_GROUNDED
            """;

    /**
     * Verifies if the generated answer is grounded in the retrieved RAG context.
     * Returns a HallucinationResult containing the (possibly modified) response and guardrail metadata.
     */
    public HallucinationResult verify(String userQuery, String retrievedContext, String generatedAnswer) {
        if (!hallucinationCheckEnabled) {
            return new HallucinationResult(generatedAnswer,
                    new GuardrailResult("HallucinationCheck", "DISABLED", "Hallucination check is disabled"));
        }

        if (retrievedContext == null || retrievedContext.isBlank()) {
            return new HallucinationResult(generatedAnswer,
                    new GuardrailResult("HallucinationCheck", "SKIPPED", "No RAG context to verify against"));
        }

        try {
            String verificationInput = String.format(
                    "CONTEXT:\n%s\n\nQUESTION:\n%s\n\nANSWER:\n%s",
                    retrievedContext, userQuery, generatedAnswer
            );

            List<ChatMessage> messages = List.of(
                    new SystemMessage(VERIFICATION_PROMPT),
                    new UserMessage(verificationInput)
            );

            String verdict = chatLanguageModel.chat(messages).aiMessage().text().trim().toUpperCase();
            log.info("HALLUCINATION CHECK verdict: {} for query: {}", verdict, truncate(userQuery));

            if (verdict.contains("NOT_GROUNDED")) {
                log.warn("HALLUCINATION DETECTED — replacing response with fallback");
                String fallback = "I don't have enough verified information in my documents to answer that accurately. "
                        + "Please try rephrasing your question, or ask about something covered in the available documents.";
                return new HallucinationResult(fallback,
                        new GuardrailResult("HallucinationCheck", "NOT_GROUNDED",
                                "Answer was not supported by retrieved documents — replaced with safe fallback"));
            }

            if (verdict.contains("PARTIALLY_GROUNDED")) {
                log.warn("PARTIAL HALLUCINATION — adding disclaimer");
                String withDisclaimer = generatedAnswer
                        + "\n\n*Note: Parts of this answer may not be fully supported by the available documents. "
                        + "Please verify critical details.*";
                return new HallucinationResult(withDisclaimer,
                        new GuardrailResult("HallucinationCheck", "PARTIALLY_GROUNDED",
                                "Answer was partially supported — disclaimer added"));
            }

            // GROUNDED
            return new HallucinationResult(generatedAnswer,
                    new GuardrailResult("HallucinationCheck", "GROUNDED", "Answer is fully supported by retrieved documents"));

        } catch (Exception e) {
            log.error("Hallucination check failed: {}", e.getMessage());
            return new HallucinationResult(generatedAnswer,
                    new GuardrailResult("HallucinationCheck", "ERROR", "Verification failed: " + e.getMessage()));
        }
    }

    private String truncate(String text) {
        return text.length() > 80 ? text.substring(0, 80) + "..." : text;
    }

    public record HallucinationResult(String response, GuardrailResult guardrailResult) {}
}
