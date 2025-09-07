package machinum.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.*;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ChapterGlossary {

    private String id;
    private String chapterId;
    private Integer chapterNumber;
    @JsonUnwrapped
    private ObjectName objectName;
    //aka !isFoundInPreviousChapters
    private boolean isUnique;

    public interface ChapterGlossaryProjection {

        String getId();

        String getChapterId();

        Integer getChapterNumber();

        String getRawJson();

    }

}
