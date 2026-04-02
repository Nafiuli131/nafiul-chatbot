package com.example.groqchat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GuardrailResult {

    private String name;       // e.g. "PromptInjection", "InputLength", "HarmfulContent", "SensitiveData", "Hallucination"
    private String status;     // "PASSED", "BLOCKED", "REDACTED", "GROUNDED", "PARTIALLY_GROUNDED", "NOT_GROUNDED"
    private String message;    // Human-readable detail

    public static GuardrailResult passed(String name) {
        return new GuardrailResult(name, "PASSED", "Check passed");
    }

    public static GuardrailResult blocked(String name, String message) {
        return new GuardrailResult(name, "BLOCKED", message);
    }

    public static GuardrailResult redacted(String name) {
        return new GuardrailResult(name, "REDACTED", "Sensitive data was redacted from output");
    }
}
