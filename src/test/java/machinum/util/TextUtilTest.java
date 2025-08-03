package machinum.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextUtilTest {

    @ParameterizedTest
    @DisplayName("Should remove various special characters")
    @CsvSource({
            "'Hello, World!', 'Hello World'",
            "'Price: $19.99', 'Price 1999'",
            "'Email@domain.com', 'Emaildomaincom'",
            "'Mix3d Ch@r$', 'Mix3d Chr'",
            "'[Test]', 'Test'",
            "'(parentheses)', 'parentheses'",
            "'<angle>', 'angle'",
            "'question?', 'question'",
            "'exclamation!', 'exclamation'",
            "'semicolon;', 'semicolon'",
            "'colon:', 'colon'",
            "'comma,', 'comma'",
            "'period.', 'period'"
    })
    void shouldRemoveSpecialCharacters(String input, String expected) {
        assertEquals(expected, TextUtil.cleanTerm(input));
    }

}