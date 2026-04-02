package com.example.groqchat.service;

import com.example.groqchat.dto.GuardrailResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class GuardrailService {

    @Value("${guardrails.max-input-length:2000}")
    private int maxInputLength;

    @Value("${guardrails.block-prompt-injection:true}")
    private boolean blockPromptInjection;

    @Value("${guardrails.block-sensitive-output:true}")
    private boolean blockSensitiveOutput;

    // ── Guard 1: Prompt Injection Patterns ─────────────────────
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)(ignore|disregard|forget)\\s+(all\\s+)?(previous|above|prior|earlier)\\s+(instructions|prompts|rules|context)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an|the)\\s+"),
            Pattern.compile("(?i)(pretend|act|behave)\\s+(like|as if|as though)\\s+you"),
            Pattern.compile("(?i)new\\s+(instructions|rules|prompt)\\s*:"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)\\[\\s*system\\s*\\]"),
            Pattern.compile("(?i)override\\s+(your|the|all)\\s+(rules|instructions|safety|guardrails)"),
            Pattern.compile("(?i)jailbreak|do\\s+anything\\s+now|DAN\\s+mode"),
            Pattern.compile("(?i)reveal\\s+(your|the|system)\\s+(prompt|instructions|rules)"),
            Pattern.compile("(?i)output\\s+(your|the|initial)\\s+(prompt|instructions|system\\s+message)")
    );

    // ── Guard 3: Harmful Content Patterns ──────────────────────
    private static final List<Pattern> HARMFUL_PATTERNS = List.of(
            Pattern.compile("(?i)(how\\s+to|tell\\s+me|explain)\\s+(make|build|create|manufacture)\\s+(a\\s+)?(bomb|explosive|weapon|drug)"),
            Pattern.compile("(?i)(hack|exploit|break\\s+into)\\s+(someone|a\\s+person|their)"),
            Pattern.compile("(?i)generate\\s+(malware|virus|ransomware|phishing)")
    );

    // ── Guard 4: Sensitive Data Patterns (output) ──────────────
    private static final List<Pattern> SENSITIVE_OUTPUT_PATTERNS = List.of(
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
            Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b"),
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
            Pattern.compile("(?i)(api[_-]?key|secret[_-]?key|password|token)\\s*[=:]\\s*\\S+"),
            Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13})\\b")
    );

    /**
     * Validates user input through all input guardrails.
     * Returns a list of GuardrailResults showing what each guard did.
     * If any guard blocks, the first element will have status "BLOCKED".
     */
    public List<GuardrailResult> validateInput(String userMessage) {
        List<GuardrailResult> results = new ArrayList<>();

        // Guard 1: Input Length
        if (userMessage == null || userMessage.isBlank()) {
            results.add(GuardrailResult.blocked("InputLength", "Message cannot be empty."));
            return results;
        }
        if (userMessage.length() > maxInputLength) {
            log.warn("GUARDRAIL [InputLength] blocked: {} chars (max {})", userMessage.length(), maxInputLength);
            results.add(GuardrailResult.blocked("InputLength",
                    "Message is too long. Please keep it under " + maxInputLength + " characters."));
            return results;
        }
        results.add(GuardrailResult.passed("InputLength"));

        // Guard 2: Prompt Injection
        if (blockPromptInjection) {
            boolean injectionFound = false;
            for (Pattern pattern : INJECTION_PATTERNS) {
                if (pattern.matcher(userMessage).find()) {
                    log.warn("GUARDRAIL [PromptInjection] blocked: {}", truncate(userMessage));
                    results.add(GuardrailResult.blocked("PromptInjection",
                            "Your message was flagged for attempting to manipulate the AI's instructions. Please rephrase your question."));
                    injectionFound = true;
                    break;
                }
            }
            if (!injectionFound) {
                results.add(GuardrailResult.passed("PromptInjection"));
            }
        } else {
            results.add(new GuardrailResult("PromptInjection", "DISABLED", "Prompt injection check is disabled"));
        }

        // If prompt injection blocked, return early
        if (results.stream().anyMatch(r -> "BLOCKED".equals(r.getStatus()))) {
            return results;
        }

        // Guard 3: Harmful Content
        boolean harmfulFound = false;
        for (Pattern pattern : HARMFUL_PATTERNS) {
            if (pattern.matcher(userMessage).find()) {
                log.warn("GUARDRAIL [HarmfulContent] blocked: {}", truncate(userMessage));
                results.add(GuardrailResult.blocked("HarmfulContent",
                        "I can't help with that request. Please ask something appropriate."));
                harmfulFound = true;
                break;
            }
        }
        if (!harmfulFound) {
            results.add(GuardrailResult.passed("HarmfulContent"));
        }

        log.debug("GUARDRAIL [Input] all checks complete — {} results", results.size());
        return results;
    }

    /**
     * Sanitizes LLM output — redacts leaked sensitive data.
     * Returns the sanitized string and a GuardrailResult.
     */
    public OutputSanitizationResult sanitizeOutput(String response) {
        if (response == null || response.isBlank() || !blockSensitiveOutput) {
            return new OutputSanitizationResult(response,
                    new GuardrailResult("SensitiveDataRedaction",
                            blockSensitiveOutput ? "PASSED" : "DISABLED",
                            blockSensitiveOutput ? "No sensitive data found" : "Output sanitization is disabled"));
        }

        String sanitized = response;
        boolean redacted = false;
        for (Pattern pattern : SENSITIVE_OUTPUT_PATTERNS) {
            if (pattern.matcher(sanitized).find()) {
                log.warn("GUARDRAIL [SensitiveData] redacting pattern match in output");
                sanitized = pattern.matcher(sanitized).replaceAll("[REDACTED]");
                redacted = true;
            }
        }

        GuardrailResult result = redacted
                ? GuardrailResult.redacted("SensitiveDataRedaction")
                : GuardrailResult.passed("SensitiveDataRedaction");

        return new OutputSanitizationResult(sanitized, result);
    }

    /**
     * Returns true if any result in the list has BLOCKED status.
     */
    public boolean isBlocked(List<GuardrailResult> results) {
        return results.stream().anyMatch(r -> "BLOCKED".equals(r.getStatus()));
    }

    /**
     * Gets the block message from the first BLOCKED result.
     */
    public String getBlockMessage(List<GuardrailResult> results) {
        return results.stream()
                .filter(r -> "BLOCKED".equals(r.getStatus()))
                .map(GuardrailResult::getMessage)
                .findFirst()
                .orElse("Request blocked by guardrails.");
    }

    private String truncate(String text) {
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }

    // ── Inner class for output sanitization result ─────────────
    public record OutputSanitizationResult(String sanitizedResponse, GuardrailResult guardrailResult) {}
}
