package machinum.model;

import lombok.*;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Line {

    private String id;

    private String chapterId;

    private String sourceKey;

    private Integer number;

    private String bookId;

    private Integer lineIndex;

    @ToString.Exclude
    private String originalLine;

    @ToString.Exclude
    private String translatedLine;

}
