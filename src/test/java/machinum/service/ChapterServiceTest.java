package machinum.service;

import com.fasterxml.jackson.core.type.TypeReference;
import machinum.TestApplication;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.repository.BookRepository;
import machinum.util.DurationUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.chat.model=deepseek-r1:32b"
                , "spring.ai.ollama.chat.options.temperature=0.4"
                , "spring.ai.ollama.chat.options.topK=40"
                , "spring.ai.ollama.chat.options.mirostatTau=3.0"
                , "spring.ai.ollama.chat.options.stop=nodata_nodata_nodata"
                , "spring.ai.ollama.chat.options.numCtx=30768"

                , "app.split.mode=balanced"
        }
)
class ChapterServiceTest extends NormalTest {

    @Autowired
    ChapterService chapterService;

    @Autowired
    BookRepository bookRepository;

    @Autowired
    VectorStore vectorStore;


    @Test
    void testSave() throws IOException {
        vectorStore.delete("documentType in ['Summary', 'SelfConsistency', 'Glossary'] and date >= '%s'".formatted(LocalDateTime.now()));
        vectorStore.delete("documentType in ['Summary', 'SelfConsistency', 'Glossary']");

        System.out.println("ChapterServiceTest.testSave");

        var cleanText = Files.readString(rewrittenChapterPath);
        var summaryText = Files.readString(summaryPath);
        var glossary = readJson(glossaryPath, new TypeReference<List<ObjectName>>() {
        });
        var book = bookRepository.findFirstByOrderByIdAsc();

        var chapterInfo = Chapter.builder()
                .number(2)
                .title("# Chapter 2: Spring (1)")
                .text(cleanText)
                .summary(summaryText)
                .names(glossary)
                .bookId(book.getId())
                .sourceKey("https://www.my-site.ai/novel/gnrc-1143/chapter-2")
                .build();

        var chapterId = DurationUtil.measure("chapterService", () -> {
            return chapterService.save(chapterInfo);
        });

        assertThat(chapterId.result())
                .isNotNull()
                .isNotEmpty();
    }

}
