package machinum.processor.core;

import java.util.regex.Pattern;

public interface RussianSupport {

    Pattern RUSSIAN = Pattern.compile("[\\p{IsCyrillic}]");

    default boolean isRussian(String text) {
        return RUSSIAN.matcher(text).find();
    }

}
