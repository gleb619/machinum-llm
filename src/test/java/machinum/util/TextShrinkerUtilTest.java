package machinum.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class TextShrinkerUtilTest {

    @Test
    @Disabled
    void testShrinkText() throws IOException {
        String text = Files.readString(Path.of("target/texts/rewriter_new_2025-02-23T08:33:02.224092550.txt"));
//        String text = "This is a sample text. This text contains some sentences. Some sentences are duplicates. " +
//                "This is a small sentence. Another small one. This text is used for testing purposes.";
        double percentage = 0.8; // Remove 30% of the text

        String shrunkText = TextShrinkerUtil.shrinkText(text, 10);
        Integer textTokens = TextUtil.countTokens(text);
        Integer shrunkTextTokens = TextUtil.countTokens(shrunkText);

        System.out.println("Original Text:\n" + text);
        System.out.println("\nShrunk Text:\n" + shrunkText);
    }

}
