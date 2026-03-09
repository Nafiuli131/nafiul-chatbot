package com.example.groqchat.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatResponse {
    private List<Choice> choices;

    @Data
    public static class Choice {
        private Message message;
    }
}
