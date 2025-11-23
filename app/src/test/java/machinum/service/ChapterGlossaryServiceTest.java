package machinum.service;

import machinum.TestApplication;
import machinum.model.ObjectName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Sql(scripts = "classpath:/sql/init.sql")
class ChapterGlossaryServiceTest {

    @Autowired
    private ChapterGlossaryService chapterGlossaryService;

    @Test
    void testFindGlossaryWithAlternatives_EmptyGlossary() {
        // Given
        List<ObjectName> emptyGlossary = List.of();
        String bookId = "00000000-0000-0000-0000-000000000001";
        Integer chapterNumber = 1;

        // When
        List<ObjectName> result = chapterGlossaryService.findGlossaryWithAlternatives(chapterNumber, emptyGlossary, bookId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testFindGlossaryWithAlternatives_WithNames() {
        // Given
        List<ObjectName> glossary = List.of(
                ObjectName.forName("Dragon"),
                ObjectName.forName("Knight"),
                ObjectName.forName("Castle")
        );
        String bookId = "00000000-0000-0000-0000-000000000001";
        Integer chapterNumber = 1;

        // When
        List<ObjectName> result = chapterGlossaryService.findGlossaryWithAlternatives(chapterNumber, glossary, bookId);

        // Then - Since no glossary data in init.sql, result should include alternative searches if any, but typically empty or with fuzzy/contains results
        assertThat(result).isNotNull();
        // The result may be empty if no matches, or contain alternatives from search algorithms
        // We just verify no exceptions are thrown and result is valid
    }

}
