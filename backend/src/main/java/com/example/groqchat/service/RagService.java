package com.example.groqchat.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    @Value("${guardrails.rag-min-score:0.7}")
    private double ragMinScore;

    public RagService(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Searches for relevant document chunks.
     * Returns a RagResult containing the context text, best score, and match count.
     */
    public RagResult retrieveContext(String query) {
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(query).content())
                .maxResults(4)
                .minScore(ragMinScore)
                .build();

        EmbeddingSearchResult<TextSegment> results = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = results.matches();

        if (matches.isEmpty()) {
            log.info("RAG: No documents found (minScore={}) for query: {}", ragMinScore, query);
            return RagResult.empty();
        }

        double bestScore = matches.stream()
                .mapToDouble(EmbeddingMatch::score)
                .max()
                .orElse(0.0);

        String context = matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n\n---\n\n"));

        log.info("RAG: Found {} chunks (bestScore={}, minScore={}) for query: {}",
                matches.size(), String.format("%.3f", bestScore), ragMinScore, query);

        return new RagResult(context, bestScore, matches.size());
    }

    /**
     * Holds RAG retrieval results with metadata for routing decisions.
     */
    public record RagResult(String context, double bestScore, int matchCount) {
        public boolean hasResults() {
            return context != null && !context.isBlank() && matchCount > 0;
        }

        public static RagResult empty() {
            return new RagResult("", 0.0, 0);
        }
    }
}
