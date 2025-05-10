package machinum.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import machinum.processor.core.HashSupport;
import machinum.util.JavaUtil;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Chapter implements HashSupport {

    @ToString.Include
    private String id;
    @ToString.Include
    private Integer number;
    @JsonAlias({"title", "header"})
    @ToString.Include
    private String title;
    private String translatedTitle;
    @JsonAlias({"cleanText", "body", "value"})
    private String text;
    private Chunks cleanChunks;
    @Deprecated
    private String proofreadText;
    private String translatedText;
    //    private String fixedTranslatedText;
    private Chunks translatedChunks;
    //    private Chunks fixedTranslatedChunks;
    private String summary;
    @Deprecated
    private String consolidatedSummary;
    @Builder.Default
    private List<String> keywords = new ArrayList<>();
    @Builder.Default
    private ChainOfThoughts selfConsistency = ChainOfThoughts.createNew();
    @Builder.Default
    private List<String> quotes = new ArrayList<>();
    @Builder.Default
    private List<Character> characters = new ArrayList<>();
    private String themes;
    private String perspective;
    private String tone;
    private String foreshadowing;
    @Builder.Default
    private List<ObjectName> names = new ArrayList<>();
    @Builder.Default
    private List<Scene> scenes = new ArrayList<>();
    @ToString.Include
    private String bookId;
    @JsonAlias({"sourceKey", "key"})
    @ToString.Include
    private String sourceKey;

    @Override
    public List<String> hashValues() {
        return List.of(id, String.valueOf(number), title, sourceKey, bookId);
    }

    public ObjectName findObjectName(String name) {
        return JavaUtil.findBy(names, ObjectName::getName, name);
    }

    public Chapter replaceObjectName(ObjectName oldOne, ObjectName newOne) {
        int oldIndex = names.indexOf(oldOne);
        if (oldIndex > -1) {
            names.set(oldIndex, newOne);
        }

        return this;
    }

}
