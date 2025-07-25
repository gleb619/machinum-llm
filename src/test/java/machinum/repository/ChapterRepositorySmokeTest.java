package machinum.repository;

import machinum.TestApplication;
import machinum.entity.ChapterEntity;
import machinum.service.DbTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class
)
class ChapterRepositorySmokeTest extends DbTest {

    @Autowired
    ChapterRepository chapterRepository;

    @Test
    void testSearchByChapterInfoFields() {
        Page<ChapterEntity> result = chapterRepository.searchInText("bookId", "searchTerm", false, false, false, PageRequest.of(0, 1));
        assertThat(result)
                .isNotNull()
                .isEmpty();
    }

    @Test
    void testSearchByObjectNameFields() {
        Page<ChapterEntity> result = chapterRepository.searchInGlossary("bookId", "searchTerm", false, false, false, PageRequest.of(0, 1));
        assertThat(result)
                .isNotNull()
                .isEmpty();
    }

    @Test
    void testSearchByCombinedCriteria() {
        Page<ChapterEntity> result = chapterRepository.searchByCombinedCriteria("bookId", "searchTerm", "searchNameTerm", PageRequest.of(0, 1));
        assertThat(result)
                .isNotNull()
                .isEmpty();
    }

    @Test
    void testFindPrevious() {
        Optional<ChapterEntity> result = chapterRepository.findPrevious("chapterInfoId");
        assertThat(result)
                .isNotNull()
                .isEmpty();
    }

}
