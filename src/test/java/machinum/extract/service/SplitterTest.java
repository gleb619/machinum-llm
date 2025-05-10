package machinum.extract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import machinum.extract.Splitter;
import machinum.model.Chunks;
import machinum.processor.core.SplitStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Spy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static machinum.flow.FlowContext.of;
import static machinum.flow.FlowContext.text;

class SplitterTest {

    @Spy
    SplitStrategy splitStrategy = new SplitStrategy.BalancedSentenceSplitter(1024);

    @InjectMocks
    Splitter splitter;

    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        splitter = new Splitter(640, splitStrategy);
    }

    @Test
    void testSplit() throws IOException {
        var chapter1Path = Path.of("src/test/resources/temp/origin_chapter_01_02.md");
        var chapter2Path = Path.of("src/test/resources/temp/origin_chapter_02_02.md");
        var chapter3Path = Path.of("src/test/resources/temp/origin_chapter_03_02.md");
        var chapter4Path = Path.of("src/test/resources/temp/origin_chapter_04_02.md");
        var chapter5Path = Path.of("src/test/resources/temp/origin_chapter_05_02.md");

        var list = doWork(chapter1Path);
        var list2 = doWork(chapter2Path);
        var list3 = doWork(chapter3Path);
        var list4 = doWork(chapter4Path);
        var list5 = doWork(chapter5Path);

        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("target/texts/chapter_01_chunks.json"), list);
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("target/texts/chapter_02_chunks.json"), list2);
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("target/texts/chapter_03_chunks.json"), list3);
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("target/texts/chapter_04_chunks.json"), list4);
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("target/texts/chapter_05_chunks.json"), list5);
    }

    private Chunks doWork(Path chapter1Path) throws IOException {
        var chapterText = Files.readString(chapter1Path);

        var result = splitter.split(of(
                text(chapterText)));

        return result.chunks();
    }

}
