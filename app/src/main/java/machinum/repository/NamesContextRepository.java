package machinum.repository;

import machinum.entity.NamesContextEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NamesContextRepository extends JpaRepository<NamesContextEntity, String> {

    List<NamesContextEntity> findAllByChapterId(String chapterId);

    @Query(value = """
            SELECT * 
            FROM names_context
            WHERE book_id = :bookId 
            AND embedding <=> cast(:embedding as vector(384)) < :threshold
            ORDER BY embedding <=> cast(:embedding as vector(384))
            LIMIT :limit
            """, nativeQuery = true)
    List<NamesContextEntity> findSimilarEmbeddings(@Param("bookId") String bookId,
                                                   @Param("embedding") String embedding,
                                                   @Param("threshold") double threshold,
                                                   @Param("limit") int limit);

    void deleteAllByChapterId(String chapterId);

}
