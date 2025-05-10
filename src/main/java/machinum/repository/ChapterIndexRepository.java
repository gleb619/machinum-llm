package machinum.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.model.ChainOfThoughts;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

import static machinum.config.Constants.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterIndexRepository {

    private final TextSplitter textSplitter;

    private final VectorStore vectorStore;


    public void index(Chapter chapter) {
        log.debug("Prepare to index chapter: {}", chapter);
        var documents = new ArrayList<Document>();
        var summary = chapter.getSummary();
        var selfConsistencyList = chapter.getSelfConsistency().stream()
                .map(ChainOfThoughts.QuestionAndAnswer::toString)
                .toList();
        var glossaryList = chapter.getNames().stream()
                .map(ObjectName::toString)
                .toList();

        var summaryDocument = createDocument(chapter, summary, SUMMARY_VALUE);

        documents.add(summaryDocument);
        for (var selfConsistency : selfConsistencyList) {
            documents.add(createDocument(chapter, selfConsistency, SELF_CONSISTENCY_VALUE));
        }

        for (var glossary : glossaryList) {
            documents.add(createDocument(chapter, glossary, GLOSSARY_VALUE));
        }

        var result = textSplitter.split(documents.stream()
                .peek(d -> d.setContentFormatter(DefaultContentFormatter.builder()
                        .withExcludedInferenceMetadataKeys(CHAPTER_INFO_ID_PARAM, DOCUMENT_TYPE_PARAM, NUMBER_PARAM, CHAPTER_KEY_PARAM, BOOK_ID_PARAM)
                        .build()))
                .collect(Collectors.toList()));

        log.debug("Sending {} documents to vector store", result.size());

        vectorStore.add(result);

        log.debug("Created index for chapter: {}", chapter);
    }

    private Document createDocument(Chapter chapter, String text, String type) {
        return new Document(text, Map.of(
                TITLE_KEYWORD, chapter.getTitle(),
                BOOK_ID_PARAM, chapter.getBookId(),
                NUMBER_PARAM, chapter.getNumber(),
                DOCUMENT_TYPE_PARAM, type,
                CHAPTER_KEY_PARAM, chapter.getSourceKey(),
                CHAPTER_INFO_ID_PARAM, chapter.getId(),
                DATE_PARAM, LocalDateTime.now()
        ));
    }

}
