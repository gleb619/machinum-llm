package machinum.ssml;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SSMLParserTest {

    SSMLParser parser = new SSMLParser();

    @Test
    void testValidate() {
        boolean result = parser.validate("ssml");
        assertThat(result)
                .isFalse();
    }

    @Test
    void testRepair() {
        String result = parser.repair("ssml");
        assertThat(result)
                .isEqualTo("ssml");
    }

    @Test
    void testMain() {
        String ssmlSample = //language=xml
                """
                        <speak>
                            <p>Первый параграф.</p>
                            <p>Второй параграф.</p>
                            <unsupportedTag>Некоторый текст</unsupportedTag>
                            <s>Первое предложение.</s>
                            <s>Второе предложение.</s>
                            <prosody rate="x-slow">Я говорю довольно медленно</prosody>
                            <break time="2000ms"/>
                        </speak>
                        """;

        // Validate SSML.
        boolean isValid = parser.validate(ssmlSample);
        assertThat(isValid)
                .isFalse();

        // Repair SSML.
        String repaired = parser.repair(ssmlSample);
        assertThat(repaired)
                .isNotEmpty()
                .isEqualTo(//language=xml
                        """
                                <speak>
                                  <p>Первый параграф.</p>
                                  <p>Второй параграф.</p>
                                   Некоторый текст\s
                                  <s>Первое предложение.</s>
                                  <s>Второе предложение.</s>
                                  <prosody rate="x-slow">Я говорю довольно медленно</prosody>
                                  <break time="2000ms"/>
                                </speak>
                                """);
    }

    @Test
    void testWork() throws IOException {
        var ssml = new SSMLParser();
        var example = Files.readString(Path.of("src/test/resources/ssml/example-ssml.txt"));
        var translatedText = Files.readString(Path.of("src/test/resources/chapter02/translated_chapter_02.md"));
        var rawText = ssml.rawText(example);
        var equal = rawText.equals(translatedText.replaceAll("\n\\s+", ""));

        String repaired1 = ssml.repair(example);
//        String repaired2 = new SSMLParser2().repair(example);
//        String repaired3 = new SSMLParser3().repair(example);

        boolean result = repaired1.equals(example);

        assertThat(repaired1)
                .isNotEmpty()
                .isNotEqualTo(example);
    }

}
