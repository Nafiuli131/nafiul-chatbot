package com.example.groqchat.service;

import com.example.groqchat.config.RagConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final SimpleVectorStore vectorStore;
    private final RagConfig ragConfig;

    @EventListener(ApplicationReadyEvent.class)
    public void ingestDocuments() {
        try {
            // Skip if vector store already exists on disk
            File storeFile = new File(ragConfig.getVectorStorePath());
            if (storeFile.exists()) {
                log.info("Vector store already exists at {}, skipping ingestion", storeFile.getPath());
                return;
            }

            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(ragConfig.getDocsPath() + "*.pdf");

            if (resources.length == 0) {
                log.warn("No PDF files found in {}", ragConfig.getDocsPath());
                return;
            }

            List<Document> allDocuments = new ArrayList<>();

            for (Resource resource : resources) {
                log.info("Loading PDF: {}", resource.getFilename());
                PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
                List<Document> documents = reader.get();
                allDocuments.addAll(documents);
            }

            TokenTextSplitter splitter = new TokenTextSplitter(
                    ragConfig.getChunkSize(),
                    ragConfig.getChunkOverlap(),
                    5,
                    10000,
                    true
            );
            List<Document> chunks = splitter.apply(allDocuments);

            log.info("Adding {} chunks from {} PDFs to vector store", chunks.size(), resources.length);
            vectorStore.add(chunks);

            // Persist to disk
            storeFile.getParentFile().mkdirs();
            vectorStore.save(storeFile);
            log.info("Vector store saved to {}", storeFile.getPath());

        } catch (Exception e) {
            log.error("Failed to ingest documents", e);
        }
    }

    @PreDestroy
    public void saveStore() {
        try {
            File storeFile = new File(ragConfig.getVectorStorePath());
            storeFile.getParentFile().mkdirs();
            vectorStore.save(storeFile);
        } catch (Exception e) {
            log.error("Failed to save vector store on shutdown", e);
        }
    }
}
