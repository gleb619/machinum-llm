package machinum.processor.core;

import lombok.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Represents warnings found in a chapter during the editing process.
 */
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ChapterWarning {

    public static final String NAME_PARAM = "name";

    /**
     * The type of warning, categorized by content issues such as language, punctuation, or R18 content.
     */
    private WarningType type;

    /**
     * The text description of the warning.
     */
    private String text;

    /**
     * Additional metadata associated with the warning, stored in a map for flexibility.
     */
    @Singular("metadata")
    private Map<String, Object> metadata;

    public static ChapterWarning createNew(Function<ChapterWarning.ChapterWarningBuilder, ChapterWarning.ChapterWarningBuilder> builderFn) {
        return builderFn.apply(ChapterWarning.builder()).build();
    }

    public String name() {
        return (String) getMetadata().get(NAME_PARAM);
    }

    public ChapterWarning name(String value) {
        getMetadata().put(NAME_PARAM, value);

        return this;
    }

    /**
     * Enum to categorize types of warnings found in a chapter.
     */
    public enum WarningType {

        EMPTY_FIELD,
        LANGUAGE,
        PUNCTUATION,
        R18_CONTENT,
        OTHER,

    }

}
