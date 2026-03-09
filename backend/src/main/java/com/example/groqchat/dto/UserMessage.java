package com.example.groqchat.dto;

import lombok.Data;

@Data
public class UserMessage {
    private String sessionId;
    private String message;
    private String systemPrompt;
}
