package machinum.repository;

import machinum.entity.BookEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<BookEntity, String> {

    Page<BookEntity> findByTitleContainingIgnoreCase(String title, PageRequest pageRequest);

    @Query("""
            SELECT 
                CASE 
                    WHEN COUNT(be0) > 0 THEN true 
                    ELSE false 
                END 
            FROM BookEntity be0 
            WHERE be0.title = :title
            """)
    boolean existsByTitle(@Param("title") String title);

    @Modifying
    @Query(value = //language=sql
            """
                    UPDATE books SET 
                    book_state = jsonb_set(jsonb_set(jsonb_set(CAST(book_state AS jsonb), 
                            '{itemIndex}', to_jsonb(:index)), 
                            '{promptIndex}', to_jsonb(:promptIndex)), 
                            '{state}', to_jsonb(:state))
                    WHERE id = :id""", nativeQuery = true)
    void updateState(@Param("id") String id,
                     @Param("index") Integer itemIndex,
                     @Param("promptIndex") Integer processorIndex,
                     @Param("state") String state);

    @Query(value = "SELECT (book_state->>'itemIndex')::int FROM books WHERE id = :id", nativeQuery = true)
    Integer getItemIndexById(@Param("id") String id);

    @Query(value = "SELECT (book_state->>'promptIndex')::int FROM books WHERE id = :id", nativeQuery = true)
    Integer getProcessorIndexById(@Param("id") String id);

    @Query(value = "SELECT book_state->>'state' FROM books WHERE id = :id", nativeQuery = true)
    String getStateById(@Param("id") String id);

    @Query("SELECT be0 FROM BookEntity be0 WHERE be0.title = :title")
    BookEntity findByTitle(@Param("title") String title);

    BookEntity findFirstByOrderByIdAsc();

    @Modifying
    @Query(value = "UPDATE books SET book_state = jsonb_set(book_state::json, '{itemIndex}', to_jsonb(:itemIndex)) WHERE id = :id", nativeQuery = true)
    void updateItemIndex(@Param("id") String id, @Param("itemIndex") Integer itemIndex);

    @Modifying
    @Query(value = "UPDATE books SET book_state = jsonb_set(book_state::json, '{promptIndex}', to_jsonb(:promptIndex)) WHERE id = :id", nativeQuery = true)
    void updatePromptIndex(@Param("id") String id, @Param("promptIndex") Integer promptIndex);

    /**
     * @param id
     * @param processedChunk
     * @deprecated due a bug in Postgres or Spring data layer. Calling the method results in an NPE
     */
    @Deprecated
    @Modifying
    @Query(value = """
            UPDATE books SET 
            book_state = jsonb_set(CAST(book_state AS jsonb), '{processedChunks}', 
                CAST(book_state -> 'processedChunks' AS jsonb) || to_jsonb(:processedChunk)
            ) 
            WHERE id = :id""", nativeQuery = true)
    void addProcessedChunk(@Param("id") String id, @Param("processedChunk") String processedChunk);

    @Modifying
    @Query(value = "UPDATE books SET book_state = cast(:bookState as json) WHERE id = :id", nativeQuery = true)
    void changeBookState(@Param("id") String id, @Param("bookState") String bookState);

    @Query(value = "SELECT count(id) FROM books WHERE id = :id AND jsonb_exists(cast(book_state -> 'processedChunks' as jsonb), :processedChunk)", nativeQuery = true)
    Long hasProcessedChunk(@Param("id") String id, @Param("processedChunk") String category);

    @Query(value = """
            SELECT raw_json FROM ( 
            	SELECT cg.*, 
            	row_number() OVER (PARTITION BY name ORDER BY number ASC) row_num 
            	FROM chapter_glossary AS cg 
            	WHERE book_id = :bookId 
            ) data 
            WHERE row_num = 1 
            ORDER BY number, name 
            """, nativeQuery = true)
    List<String> getGlossaryForBookId(String bookId);

    List<BookTitlesQueryResult> findBy(PageRequest pageRequest);

    record BookTitlesQueryResult(String id, String title) {
    }

}
