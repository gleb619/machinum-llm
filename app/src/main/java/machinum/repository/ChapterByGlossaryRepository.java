package machinum.repository;

import machinum.entity.ChapterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterByGlossaryRepository extends JpaRepository<ChapterEntity, String> {

    @Query(value = """ 
                select distinct
                    c2.chapter_id
                from 
                    chapter_glossary c2 
                where 
                    c2.name in :names 
                    and c2.book_id = :bookId 
            """, nativeQuery = true)
    List<String> findChaptersByGlossary_Native(@Param("names") List<String> names,
                                               @Param("bookId") String bookId);

    default List<ChapterEntity> findChaptersByGlossary(@Param("names") List<String> names,
                                                       @Param("bookId") String bookId) {
        List<String> ids = findChaptersByGlossary_Native(names, bookId);
        return findAllById(ids);
    }

}
