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

    public String queryDocuments(String query) {
        log.info("RAG Tool invoked with query: {}", query);
        String context = ragService.retrieveContext(query);
        if (context.isEmpty()) {
            return "No relevant documents found for this query.";
        }
        return context;
    }
}
