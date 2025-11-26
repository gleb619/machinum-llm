package machinum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.converter.ChapterContextMapper;
import machinum.entity.ChapterContextEntity;
import machinum.model.ChapterContext;
import machinum.repository.ChapterContextRepository;
import machinum.repository.ChapterContextRepository.ChapterSimilarityProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterContextService {

    private final ChapterContextRepository chapterContextRepository;
    private final ChapterContextMapper chapterContextMapper;

    @Transactional
    public void deleteById(String id) {
        chapterContextRepository.deleteById(id);
    }

    @Transactional
    public void save(ChapterContext dto) {
        ChapterContextEntity entity = chapterContextMapper.toEntity(dto);
        chapterContextRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ChapterSimilarityProjection> findSimilarByFieldProjected(String bookId, String embeddingStr, String fieldType, double threshold, int limit) {
        return chapterContextRepository.findSimilarByFieldProjected(bookId, embeddingStr, fieldType, threshold, limit);
    }

    @Transactional(readOnly = true)
    public List<ChapterSimilarityProjection> findSimilarAcrossFieldsProjected(String bookId, String embeddingStr, double threshold, int limit) {
        return chapterContextRepository.findSimilarAcrossFieldsProjected(bookId, embeddingStr, threshold, limit);
    }

    @Transactional(readOnly = true)
    public List<ChapterContext> findByBookId(String bookId) {
        return chapterContextRepository.findAll().stream()
                .filter(entity -> bookId.equals(entity.getBookId()))
                .map(chapterContextMapper::toDto)
                .toList();
    }

}
