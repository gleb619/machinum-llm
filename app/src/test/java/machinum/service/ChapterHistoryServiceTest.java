package machinum.service;

import com.github.difflib.DiffUtils;
import machinum.TestApplication;
import machinum.model.Chapter;
import machinum.model.ChapterHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static machinum.listener.ChapterEntityListener.ChapterInfoConstants.CLEAN_TEXT;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class
)
class ChapterHistoryServiceTest extends NormalTest {

    @Autowired
    ChapterHistoryService chapterHistoryService;

    @Autowired
    ChapterService chapterService;

    private String chapterInfoId;
    private String fieldName;

    @BeforeEach
    void setUpVars() {
        chapterInfoId = "00000000-0000-0000-0000-000000000002";
        fieldName = CLEAN_TEXT;
    }

    @Test
    void testHistoryWork() {
        //Given
        Chapter chapter = chapterService.getById(chapterInfoId);
        String state1 = chapter.getText();

        chapterHistoryService.save(ChapterHistory.builder()
                .chapterInfoId(chapterInfoId)
                .number(1)
                .fieldName(fieldName)
                .patch(DiffUtils.diff(List.of(), lines(chapter.getText())))
                .createdAt(LocalDateTime.now())
                .build());

        String state2 = "New paragraph\n" + chapter.getText();
        chapter.setText(state2);

        //When
        chapterService.save(chapter);
        String state3 = "New paragraph2\n" + chapter.getText();
        chapter.setText(state3);
        chapterService.save(chapter);

        //Then
        var patches = chapterHistoryService.getPatches(chapter.getId(), fieldName);
        assertThat(patches)
                .isNotEmpty()
                .hasSize(3);

        String actualState1 = chapterHistoryService.rebuildContentAtPoint(chapter.getId(), fieldName, 1);
        String actualState2 = chapterHistoryService.rebuildContentAtPoint(chapter.getId(), fieldName, 2);
        String actualState3 = chapterHistoryService.rebuildContentAtPoint(chapter.getId(), fieldName, 3);

        assertThat(List.of(state1, state2, state3))
                .containsExactly(actualState1, actualState2, actualState3);
    }

    private List<String> lines(String text) {
        return Arrays.asList(text.split("\n"));
    }

}
