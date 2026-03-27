package com.example.groqchat.config;

import lombok.Data;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag")
public class RagConfig {

    private String docsPath;
    private String vectorStorePath;
    private int chunkSize = 800;
    private int chunkOverlap = 200;

    @Bean
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File storeFile = new File(vectorStorePath);
        if (storeFile.exists()) {
            store.load(storeFile);
        }
        return store;
    }
}
