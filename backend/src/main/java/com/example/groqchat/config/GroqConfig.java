package com.example.groqchat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Data
@Configuration
@ConfigurationProperties(prefix = "groq")
public class GroqConfig {

    private String apiKey;
    private String baseUrl;
    private String model;
    private double temperature;
    private int maxTokens;

    @Bean
    public WebClient groqWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
