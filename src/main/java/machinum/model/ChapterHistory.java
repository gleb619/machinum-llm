package machinum.model;

import com.fasterxml.jackson.annotation.JsonView;
import com.github.difflib.patch.Patch;
import machinum.controller.ChapterHistoryController;
import machinum.controller.ChapterHistoryController.ChapterInfoViews.Internal;
import machinum.controller.ChapterHistoryController.ChapterInfoViews.Public;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ChapterHistory {

    private String id;

    private String chapterInfoId;

    @Builder.Default
    private Integer number = 0;

    private String fieldName;

    @ToString.Exclude
    @JsonView(Internal.class)
    private Patch<String> patch;

    private LocalDateTime createdAt;

    public static boolean isEmpty(@NonNull ChapterHistory history) {
        return "-1".equals(history.getId());
    }

    public static ChapterHistory empty() {
        return ChapterHistory.builder()
                .id("-1")
                .build();
    }

}
