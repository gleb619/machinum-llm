package machinum.repository;

import machinum.TestApplication;
import machinum.service.DbTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class
)
class ChapterGlossaryRepositorySmokeTest extends DbTest {

    @Autowired
    ChapterGlossaryRepository chapterRepository;

    @Test
    void testFindLatestGlossaryByQuery() {
        List<String> result = chapterRepository.findLatestGlossaryByQuery(1, List.of("names"), "bookId", 1);
        assertThat(result)
                .isNotNull()
                .isEmpty();
    }

}
