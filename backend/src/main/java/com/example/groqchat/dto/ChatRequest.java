package com.example.groqchat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private String model;
    private List<Message> messages;
    private double temperature;

    @JsonProperty("max_tokens")
    private int maxTokens;
}
