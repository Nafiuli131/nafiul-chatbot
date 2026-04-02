package com.example.groqchat.agent.tools;

import com.example.groqchat.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RagTool {

    private final RagService ragService;

    public RagTool(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * Queries documents and returns a RagResult with context + scores.
     */
    public RagService.RagResult queryDocuments(String query) {
        log.info("RAG Tool invoked with query: {}", query);
        return ragService.retrieveContext(query);
    }
}
