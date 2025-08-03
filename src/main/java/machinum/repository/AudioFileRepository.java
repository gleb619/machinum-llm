package machinum.repository;

import machinum.entity.AudioFileEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AudioFileRepository extends JpaRepository<AudioFileEntity, String> {

    Optional<AudioFileEntity> findOneByChapterIdAndType(String chapterId, String type);

    List<AudioFileEntity> findByChapterId(String chapterId);

    @Query("""
            SELECT afe0 
            FROM AudioFileEntity afe0 
            WHERE afe0.chapterId IN (
                SELECT ce0.id 
                FROM ChapterEntity ce0 
                WHERE ce0.bookId = :bookId
            ) 
            """)
    List<AudioFileEntity> findAllByBookId(@Param("bookId") String bookId, Sort sort);

    @Query("""
            SELECT a1 FROM 
            AudioFileEntity a1 
            WHERE a1.chapterId IN :chapterIds 
            AND a1.createdAt = ( 
                SELECT MAX(a2.createdAt) 
                FROM AudioFileEntity a2 
                WHERE a2.chapterId = a1.chapterId 
            ) 
            """)
    List<AudioFileEntity> findByChapterIdIn(@Param("chapterIds") List<String> chapterIds);

    Optional<AudioFileEntity> findOneByMinioKey(String minioKey);

}
