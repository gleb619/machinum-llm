package machinum.util;

import machinum.extract.util.SimpleProperNameExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleProperNameExtractorTest {

    @Test
    void testExtractProperNames() throws IOException {
        var chapterPath = Path.of("src/test/resources/chapter02/origin_chapter_02.md");
        var chapterText = Files.readString(chapterPath);

        List<String> result = SimpleProperNameExtractor.extractProperNames(chapterText);
        assertThat(result)
                .isNotEmpty();
    }

    @Test
    void testExtractNicknames() throws IOException {
        var chapterPath = Path.of("src/test/resources/chapter02/origin_chapter_02.md");
        var chapterText = Files.readString(chapterPath);

        List<String> result = SimpleProperNameExtractor.extractNicknames(chapterText);
        assertThat(result)
                .isEmpty();
    }

    @Test
    void testExtractAllNames() throws IOException {
        var chapterPath = Path.of("src/test/resources/chapter02/origin_chapter_02.md");
        var chapterText = Files.readString(chapterPath);

        List<String> result = SimpleProperNameExtractor.extractAllNames(chapterText);
        assertThat(result)
                .isNotEmpty();
    }

}
