package com.example.groqchat.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.Data;
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
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        File storeFile = new File(vectorStorePath);
        if (storeFile.exists()) {
            return InMemoryEmbeddingStore.fromFile(storeFile.toPath());
        }
        return new InMemoryEmbeddingStore<>();
    }
}
