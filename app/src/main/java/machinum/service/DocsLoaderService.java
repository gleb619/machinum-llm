package machinum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.util.JavaUtil;
import org.springframework.ai.autoconfigure.ollama.CharactersMetadataEnricher;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.db.DbHelper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static machinum.config.Constants.*;

@Deprecated
@Slf4j
@Service
@RequiredArgsConstructor
public class DocsLoaderService {

    private static final int MAX_TOKENS_PER_CHUNK = 2000; // Adjust this value as needed

    private final DbHelper dbHelper;

    private final ObjectMapper objectMapper;

    public static void testMethod(ClassPathResource resource) {
        JsonReader jsonReader = new JsonReader(resource);
        TextSplitter textSplitter = new TokenTextSplitter();
        List<Document> documentList = textSplitter.apply(jsonReader.get());

        System.out.println("DocsLoaderService.testMethod: " + documentList);
    }

    @SneakyThrows
    public void loadDocs2() {
        AtomicInteger counter = new AtomicInteger(1);
        ClassPathResource resource = new ClassPathResource("docs/devcenter-content-snapshot.2024-05-21.json");

        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            List<Document> documents = new ArrayList<>();
            String line;

            while ((line = reader.readLine()) != null) {
                log.info("Working with line: {}", counter.get());

                Map<String, Object> jsonDoc = objectMapper.readValue(line, Map.class);
                String title = (String) jsonDoc.get("title");
                String content = (String) jsonDoc.get("body");

                int chapter = counter.getAndIncrement();
                Document document = new Document(content, new HashMap<>(Map.of(
                        TITLE_KEYWORD, title,
                        CHAPTER_KEYWORD, chapter,
                        FILE_KEYWORD, "chapter_%d.json".formatted(chapter)
                )));

                documents.add(document);
            }

            ChatModel ollamaModel = null;

            TokenTextSplitter splitter = new TokenTextSplitter();
            CharactersMetadataEnricher characters = new CharactersMetadataEnricher(ollamaModel, objectMapper);

            var transformer = JavaUtil.combine(
                    characters,
                    splitter
            );

//            List<Document> newDocuments = transformer.apply(documents);
            List<Document> newDocuments = splitter.apply(documents);

            //dbHelper.add(newDocuments);
        }
    }

    @SneakyThrows
    public String loadDocs() {
        AtomicInteger counter = new AtomicInteger(1);
        ClassPathResource resource = new ClassPathResource("docs/devcenter-content-snapshot.2024-05-21.json");
//        testMethod(resource);

        log.info("Read file: {}", resource.getFile());

        try (InputStream inputStream = resource.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            List<Document> documents = new ArrayList<>();
            String line;

            while ((line = reader.readLine()) != null) {
                log.info("Working with line: {}", counter.get());

                Map<String, Object> jsonDoc = objectMapper.readValue(line, Map.class);
                String content = (String) jsonDoc.get("body");

                // Split the content into smaller chunks if it exceeds the token limit
//                List<String> chunks = splitIntoChunks(content);
                List<String> chunks = splitIntoChunksByDimension(content, 1536);

                log.info("Created {} chunks", chunks.size());

                // Create a Document for each chunk and add it to the list
                for (String chunk : chunks) {
                    Document document = createDocument(jsonDoc, chunk, counter);
                    documents.add(document);
                }

                log.info("Creating {} documents", documents.size());

                // Add documents in batches to avoid memory overload
                if (documents.size() >= 100) {
                    log.info("Sending part to vector store");
//                    dbHelper.add(documents);
                    documents.clear();
                }
            }

            // Add any remaining documents
            if (!documents.isEmpty()) {
                log.info("Sending to vector store");
//                dbHelper.add(documents);
            }

            log.info("Done");

            return "All documents added successfully!";
        }
    }

    private Document createDocument(Map<String, Object> jsonMap, String content, AtomicInteger counter) {
        String title = (String) jsonMap.get("title");
        Map<String, Object> metadata = Objects.nonNull(jsonMap.get("metadata")) ? (Map<String, Object>) jsonMap.get("metadata") : new HashMap<>();

        // Add additional metadata fields
        int chapter = counter.getAndIncrement();
        metadata.putIfAbsent("title", title);
        metadata.putIfAbsent("chapter", chapter);
        metadata.putIfAbsent("file", "chapter_%d".formatted(chapter));
//        metadata.putIfAbsent("url", jsonMap.get("url"));
//        metadata.putIfAbsent("action", jsonMap.get("action"));
//        metadata.putIfAbsent("format", jsonMap.get("format"));
//        metadata.putIfAbsent("updated", jsonMap.get("updated"));

        return new Document(content, metadata);
    }

    private List<String> splitIntoChunks(String content) {
        List<String> chunks = new ArrayList<>();
        String[] words = content.split("\\s+");
        StringBuilder chunk = new StringBuilder();
        int tokenCount = 0;

        for (String word : words) {
            // Estimate token count for the word (approximated by character length for simplicity)
            int wordTokens = word.length() / 4;  // Rough estimate: 1 token = ~4 characters

            if (tokenCount + wordTokens > DocsLoaderService.MAX_TOKENS_PER_CHUNK) {
                chunks.add(chunk.toString());
                chunk.setLength(0); // Clear the buffer
                tokenCount = 0;
            }

            chunk.append(word).append(" ");
            tokenCount += wordTokens;
        }

        if (!chunk.isEmpty()) {
            chunks.add(chunk.toString());
        }

        return chunks;
    }

    public List<String> splitIntoChunksByDimension(String text, int dimension) {
        int tokensPerDimension = dimension / 4; // Assuming 4 bytes per float
        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();

        StringBuilder currentChunk = new StringBuilder();
        int currentTokenCount = 0;

        for (String word : words) {
            if (currentTokenCount + word.length() > tokensPerDimension) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
                currentTokenCount = 0;
            }
            currentChunk.append(word).append(" ");
            currentTokenCount += word.length();
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }


}
