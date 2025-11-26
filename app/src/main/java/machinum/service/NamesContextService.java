package machinum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.converter.NamesContextMapper;
import machinum.model.NamesContext;
import machinum.repository.NamesContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NamesContextService {

    private final NamesContextRepository namesContextRepository;
    private final NamesContextMapper namesContextMapper;

    @Transactional(readOnly = true)
    public List<NamesContext> getNamesContextsByChapterId(String chapterId) {
        log.debug("Get names contexts by chapter id: {}", chapterId);
        return namesContextMapper.toDto(namesContextRepository.findAllByChapterId(chapterId));
    }

    @Transactional
    public void deleteAllNamesContextsByChapterId(String chapterId) {
        log.debug("Removing all names contexts by chapter id: {}", chapterId);
        namesContextRepository.deleteAllByChapterId(chapterId);
    }

    @Transactional
    public List<NamesContext> saveAllNamesContexts(List<NamesContext> namesContexts) {
        log.debug("Save batch of names contexts to db: {}", namesContexts.size());
        var entities = namesContextMapper.toEntity(namesContexts);
        var saved = namesContextRepository.saveAll(entities);
        return namesContextMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<NamesContext> findSimilarEmbeddings(String bookId, String embedding, double threshold, int limit) {
        log.debug("Finding similar names context embeddings with threshold {} and limit {}", threshold, limit);
        return namesContextMapper.toDto(namesContextRepository.findSimilarEmbeddings(bookId, embedding, threshold, limit));
    }

}
