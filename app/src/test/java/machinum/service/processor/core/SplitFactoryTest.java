package machinum.service.processor.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import machinum.flow.model.Chunks;
import machinum.processor.core.SplitFactory;
import machinum.processor.core.SplitStrategy;
import machinum.util.TextUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static machinum.processor.core.SplitFactory.MAX_CHARACTERS_PER_CHUNK_PARAM;
import static machinum.processor.core.SplitFactory.OVERLAP_CHARACTERS_SIZE_PARAM;
import static machinum.processor.core.SplitFactory.Type.BALANCED_SENTENCE;
import static machinum.processor.core.SplitFactory.Type.SENTENCE;
import static org.assertj.core.api.Assertions.assertThat;

class SplitFactoryTest {

    SplitFactory splitFactory = new SplitFactory(512, 100);
    ObjectMapper mapper = new ObjectMapper();

    @Test
    void test_balanced_sentence() throws IOException {
        var chapter1Path = Path.of("src/test/resources/chapter01/origin_chapter_01.md");
        var chapterText = Files.readString(chapter1Path);

        int parts2 = Math.max(TextUtil.countTokens(chapterText) / 768, 2);
        int parts = chapterText.length() > 4_000 ? parts2 : 1;

        var result = splitFactory.getSplitStrategy(BALANCED_SENTENCE, Map.of(
                MAX_CHARACTERS_PER_CHUNK_PARAM, parts,
                OVERLAP_CHARACTERS_SIZE_PARAM, 0));

        List<String> chunks;
        if (result instanceof SplitStrategy.BalancedSentenceSplitter strategy) {
            chunks = strategy.split(chapterText, parts);
        } else {
            chunks = result.split(chapterText);
        }

        assertThat(chunks)
                .isNotEmpty();

        assertThat(chapterText)
                .isEqualTo(chunks.stream()
                        .collect(Collectors.joining("\n")));
    }

    @Test
    @Disabled
    void test_sentence() throws IOException {
        var chapterPath = Path.of("src/test/resources/chapter02/origin_chapter_02.md");
        String chapterText = Files.readString(chapterPath);

        var result = splitFactory.getSplitStrategy(SENTENCE, Map.of(
                MAX_CHARACTERS_PER_CHUNK_PARAM, chapterText.length() / 3));
        var chunks = result.split(chapterText);

        assertThat(chunks)
                .isNotEmpty();
    }

    @Deprecated
    void cleanText() throws IOException {
        var chapter1Path = Path.of("src/test/resources/temp/origin_chapter_01_02.md");
        var chapter2Path = Path.of("src/test/resources/temp/origin_chapter_02_02.md");
        var chapter3Path = Path.of("src/test/resources/temp/origin_chapter_03_02.md");

        var list = doTest(chapter1Path);
        var list2 = doTest(chapter2Path);
        var list3 = doTest(chapter3Path);

        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("build/texts/chapter_01_chunks.json"), list);
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("build/texts/chapter_02_chunks.json"), list2);
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File("build/texts/chapter_03_chunks.json"), list3);
    }

    /* ============= */

    private Chunks doTest(Path chapter1Path) throws IOException {
        String chapterText = Files.readString(chapter1Path);
        int parts2 = Math.max(TextUtil.countTokens(chapterText) / 768, 2);
        int parts = chapterText.length() > 4_000 ? parts2 : 1;

        var result = splitFactory.getSplitStrategy(BALANCED_SENTENCE, Map.of(
                MAX_CHARACTERS_PER_CHUNK_PARAM, parts,
                OVERLAP_CHARACTERS_SIZE_PARAM, 0));

        List<String> chunks;
        if (result instanceof SplitStrategy.BalancedSentenceSplitter strategy) {
            chunks = strategy.split(chapterText, parts);
        } else {
            chunks = result.split(chapterText);
        }

        var counter = new AtomicInteger(1);
        return Chunks.of(chunks.stream()
                .map(Chunks.ChunkItem::of)
                .peek(item -> item.setPart(counter.getAndIncrement()))
                .collect(Collectors.toList()));
    }

}
