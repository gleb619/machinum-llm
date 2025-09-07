package machinum.extract.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static machinum.extract.util.NamedEntityRecognizer.Entity.createNew;
import static org.assertj.core.api.Assertions.assertThat;

class NamedEntityRecognizerTest {

    NamedEntityRecognizer namedEntityRecognizer;

    @BeforeEach
    void setUp() {
        namedEntityRecognizer = NamedEntityRecognizer.from(Map.of(
                "PERSON", "src/main/resources/nlp-models/en-ner-person.bin",
                "LOCATION", "src/main/resources/nlp-models/en-ner-location.bin",
                "ORGANIZATION", "src/main/resources/nlp-models/en-ner-organization.bin"
        ));
    }

    @Test
    void testRecognizeEntities() throws IOException {
        var chapterPath = Path.of("src/test/resources/chapter02/origin_chapter_02.md");
        var chapterText = Files.readString(chapterPath);
        var awaitedEntities = List.of(createNew(b -> b
                .text("text")
                .type("type")
                .start(0)
                .end(0)
        ));

        var result = namedEntityRecognizer.recognizeEntities(chapterText);
        var uniqueResult = namedEntityRecognizer.recognizeUniqueEntities(chapterText);

        assertThat(result)
                .isNotEmpty()
                .isNotEqualTo(uniqueResult);

        assertThat(uniqueResult)
                .isNotEmpty();
    }

}
