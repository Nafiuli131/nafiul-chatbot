package com.example.groqchat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentResponse {

    private String response;
    private boolean blocked;
    private List<GuardrailResult> guardrails;
}
