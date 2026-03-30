package com.example.groqchat.service;

import com.example.groqchat.config.RagConfig;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final RagConfig ragConfig;

    @EventListener(ApplicationReadyEvent.class)
    public void ingestDocuments() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(ragConfig.getDocsPath() + "*.pdf");

            if (resources.length == 0) {
                log.warn("No PDF files found in {}", ragConfig.getDocsPath());
                return;
            }

            // Determine which PDFs are new
            Set<String> alreadyIngested = loadIngestedFileList();
            List<Resource> newPdfs = Arrays.stream(resources)
                    .filter(r -> r.getFilename() != null && !alreadyIngested.contains(r.getFilename()))
                    .toList();

            if (newPdfs.isEmpty()) {
                log.info("All {} PDFs already ingested, nothing new to process", resources.length);
                return;
            }

            log.info("Found {} new PDF(s) to ingest (out of {} total)", newPdfs.size(), resources.length);

            ApachePdfBoxDocumentParser parser = new ApachePdfBoxDocumentParser();
            List<TextSegment> allSegments = new ArrayList<>();

            for (Resource resource : newPdfs) {
                log.info("Loading PDF: {}", resource.getFilename());
                Document document = parser.parse(resource.getInputStream());
                List<TextSegment> segments = DocumentSplitters
                        .recursive(ragConfig.getChunkSize(), ragConfig.getChunkOverlap())
                        .split(document);
                allSegments.addAll(segments);
            }

            log.info("Adding {} chunks from {} new PDF(s) to embedding store", allSegments.size(), newPdfs.size());
            embeddingStore.addAll(embeddingModel.embedAll(allSegments).content(), allSegments);

            // Persist embedding store to disk
            File storeFile = new File(ragConfig.getVectorStorePath());
            storeFile.getParentFile().mkdirs();
            ((InMemoryEmbeddingStore<TextSegment>) embeddingStore).serializeToFile(storeFile.toPath());
            log.info("Embedding store saved to {}", storeFile.getPath());

            // Update ingested file list
            Set<String> updatedList = new HashSet<>(alreadyIngested);
            newPdfs.forEach(r -> updatedList.add(r.getFilename()));
            saveIngestedFileList(updatedList);

        } catch (Exception e) {
            log.error("Failed to ingest documents", e);
        }
    }

    private Path getIngestedListPath() {
        return Path.of(ragConfig.getVectorStorePath()).getParent().resolve("ingested-files.txt");
    }

    private Set<String> loadIngestedFileList() {
        Path path = getIngestedListPath();
        if (!Files.exists(path)) {
            return new HashSet<>();
        }
        try {
            return Files.readAllLines(path).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (IOException e) {
            log.warn("Could not read ingested file list, will re-ingest all PDFs", e);
            return new HashSet<>();
        }
    }

    private void saveIngestedFileList(Set<String> fileNames) {
        Path path = getIngestedListPath();
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, fileNames.stream().sorted().toList());
            log.info("Ingested file list updated: {}", fileNames);
        } catch (IOException e) {
            log.error("Could not save ingested file list", e);
        }
    }
}
