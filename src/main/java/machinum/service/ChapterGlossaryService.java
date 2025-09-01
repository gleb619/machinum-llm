package machinum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.converter.ChapterGlossaryMapper;
import machinum.converter.ChapterMapper;
import machinum.exception.AppIllegalStateException;
import machinum.model.Chapter;
import machinum.model.ChapterGlossary;
import machinum.model.ObjectName;
import machinum.repository.ChapterByGlossaryRepository;
import machinum.repository.ChapterGlossaryRepository;
import machinum.repository.ChapterGlossaryRepository.CountResult;
import machinum.repository.ChapterGlossaryRepository.GlossaryByQueryResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static machinum.util.JavaUtil.uniqueBy;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterGlossaryService {

    private final ChapterGlossaryRepository chapterRepository;
    private final ChapterByGlossaryRepository chapterByGlossaryRepository;
    @Qualifier("objectMapperHolder")
    private final Holder<ObjectMapper> objectMapperHolder;
    private final ChapterGlossaryMapper chapterGlossaryMapper;
    private final ChapterMapper chapterMapper;

    @Transactional(readOnly = true)
    public ChapterGlossary getById(@NonNull String bookId) {
        return findById(bookId)
                .orElseThrow(() -> new AppIllegalStateException("Glossary for given id=%s, is not found", bookId));
    }

    @Transactional(readOnly = true)
    public Optional<ChapterGlossary> findById(@NonNull String bookId) {
        return chapterRepository.findById(bookId)
                .map(chapterGlossaryMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<Chapter> findChaptersByGlossary(@NonNull List<String> names, @NonNull String bookId) {
        if (names.isEmpty()) {
            return Collections.emptyList();
        }

        var pairs = chapterByGlossaryRepository.findChaptersByGlossary(names, bookId);

        return pairs.stream()
                .map(chapterMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ObjectName> findGlossary(@NonNull Integer chapterNumber, @NonNull List<ObjectName> glossary, String bookId) {
        if (glossary.isEmpty()) {
            return glossary;
        }

        log.debug("Prepare to load references for glossary: {}", glossary.size());
        var names = uniqueBy(glossary, ObjectName::getName).stream()
                .map(ObjectName::getName)
                .collect(Collectors.toList());

        var pairs = chapterRepository.findGlossaryByQuery(names, chapterNumber, bookId);

        return pairs.stream()
                .map(GlossaryByQueryResult::getRawJson)
                .map(rawJson -> objectMapperHolder.execute(mapper -> mapper.readValue(rawJson, ObjectName.class)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ObjectName> findReferences(@NonNull Integer chapterNumber, @NonNull List<ObjectName> glossary, String bookId, int similarityRatio) {
        if (glossary.isEmpty()) {
            return glossary;
        }

        log.debug("Prepare to load references for glossary: {}", glossary.size());
        var names = uniqueBy(glossary, ObjectName::getName).stream()
                .map(ObjectName::getName)
                .collect(Collectors.toList());

        var pairs = chapterRepository.findGlossaryByQuery(names, chapterNumber, bookId);

        if (pairs.size() == names.size()) {
            return pairs.stream()
                    .map(GlossaryByQueryResult::getRawJson)
                    .map(rawJson -> objectMapperHolder.execute(mapper -> mapper.readValue(rawJson, ObjectName.class)))
                    .collect(Collectors.toList());
        } else {
            var terms = findMissingNames(pairs, names);
            var termsOr = terms.stream()
                    .map(s -> String.join(" or ", s.split("\\s+")))
                    .collect(Collectors.toList());

            if (terms.isEmpty() || termsOr.isEmpty()) {
                return Collections.emptyList();
            }

            return chapterRepository.findLatestGlossaryListByQuery(terms, termsOr, chapterNumber, bookId).stream()
                    .map(rawJson -> objectMapperHolder.execute(mapper -> mapper.readValue(rawJson, ObjectName.class)))
                    .collect(Collectors.toList());
//            return chapterRepository.findLatestGlossaryByQuery(chapterNumber, missingNames, bookId, similarityRatio).stream()
//                    .map(rawJson -> objectMapperHolder.execute(mapper -> mapper.readValue(rawJson, ObjectName.class)))
//                    .collect(Collectors.toList());
        }
    }

    @Transactional(readOnly = true)
    public List<ObjectName> findTranslations(@NonNull String bookId, @NonNull List<ObjectName> glossary) {
        if (glossary.isEmpty()) {
            return glossary;
        }

        log.debug("Prepare to load translated names from old glossary terms: {}", glossary.size());
        return chapterRepository.findTranslatedNames(bookId, uniqueBy(glossary, ObjectName::getName).stream()
                        .map(ObjectName::getName)
                        .collect(Collectors.toList())).stream()
                .map(rawJson -> objectMapperHolder.execute(mapper -> mapper.readValue(rawJson, ObjectName.class)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ChapterGlossary> findBookGlossary(@NonNull String bookId, @NonNull PageRequest pageRequest) {
        log.debug("Prepare to load book's glossary from db: {}", bookId);
        var result = chapterRepository.findGlossary(bookId, pageRequest)
                .map(projection -> {
                    var dto = chapterGlossaryMapper.toDto(projection);
                    var objectName = objectMapperHolder.execute(mapper -> mapper.readValue(projection.getRawJson(), ObjectName.class));
                    dto.setObjectName(objectName);

                    return dto;
                });

        return processUniqueState(bookId, result);
    }

    @Transactional(readOnly = true)
    public Page<ChapterGlossary> findBookTranslatedGlossary(@NonNull String bookId, boolean translated, @NonNull PageRequest pageRequest) {
        log.debug("Prepare to load book's translated glossary from db: {}", bookId);
        var result = chapterRepository.findTranslatedGlossary(bookId, translated, pageRequest)
                .map(projection -> {
                    var dto = chapterGlossaryMapper.toDto(projection);
                    var objectName = objectMapperHolder.execute(mapper -> mapper.readValue(projection.getRawJson(), ObjectName.class));
                    dto.setObjectName(objectName);

                    return dto;
                });

        return processUniqueState(bookId, result);
    }

    @Transactional(readOnly = true)
    public Page<ChapterGlossary> findBookTranslatedGlossary(@NonNull String bookId, Integer fromChapter, Integer toChapter, @NonNull PageRequest pageRequest) {
        log.debug("Prepare to load book's translated glossary from db: {}, range={}/{}", bookId, fromChapter, toChapter);
        var result = chapterRepository.findTranslatedGlossary(bookId, fromChapter, toChapter, pageRequest)
                .map(projection -> {
                    var dto = chapterGlossaryMapper.toDto(projection);
                    var objectName = objectMapperHolder.execute(mapper -> mapper.readValue(projection.getRawJson(), ObjectName.class));
                    dto.setObjectName(objectName);

                    return dto;
                });

        return processUniqueState(bookId, result);
    }

    @Transactional(readOnly = true)
    public List<ChapterGlossary> searchGlossary(@NonNull String bookId,
                                                String searchText,
                                                Integer chapterStart,
                                                Integer chapterEnd,
                                                Integer topK,
                                                Float minScore) {
        log.debug("Prepare to search glossary for: bookId={}, searchText={}, chapterStart={}, chapterEnd={}, topK={}, minScore={}",
                bookId, searchText, chapterStart, chapterEnd, topK, minScore);

        return chapterRepository.searchGlossary(bookId, searchText, chapterStart, chapterEnd, topK, minScore).stream()
                .map(projection -> {
                    var dto = chapterGlossaryMapper.toDto(projection);
                    var objectName = objectMapperHolder.execute(mapper -> mapper.readValue(projection.getRawJson(), ObjectName.class));
                    dto.setObjectName(objectName);

                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void replaceText(@NonNull String bookId, String search, String replacement) {
        log.debug("Replacing text in bookId: {}", bookId);
        chapterRepository.replaceText(bookId, search, replacement);
        log.debug("Text replacement completed for bookId: {}", bookId);
    }

    @Transactional
    public void replaceTextById(@NonNull String chapterId, String search, String replacement) {
        log.debug("Replacing text in chapterId: {}", chapterId);
        chapterRepository.replaceTextById(chapterId, search, replacement);
        log.debug("Text replacement completed for chapterId: {}", chapterId);
    }

    @Transactional
    public void replaceTextForColumn(@NonNull String chapterId, String columnName, String search, String replacement) {
        log.debug("Replacing text in column: {} for chapterId: {}", columnName, chapterId);
        chapterRepository.replaceTextForColumn(chapterId, columnName, search, replacement);
        log.debug("Text replacement completed for column: {} in chapterId: {}", columnName, chapterId);
    }

    @Transactional
    public void replaceSummary(@NonNull String bookId, String search, String replacement) {
        log.debug("Replacing summary in bookId: {}", bookId);
        chapterRepository.replaceSummary(bookId, search, replacement);
        log.debug("Summary replacement completed for bookId: {}", bookId);
    }

    @Transactional
    public void updateGlossaryRuName(@NonNull String bookId, String oldRuName, String newRuName, Boolean returnIds) {
        if (returnIds == null) {
            log.warn("returnIds is null for bookId: {}", bookId);
        }
        log.debug("Updating glossary ru name in bookId: {}", bookId);
        chapterRepository.updateGlossaryRuName(bookId, oldRuName, newRuName, returnIds);
        log.debug("Glossary ru name update completed for bookId: {}", bookId);
    }

    /* ============= */

    private List<String> findMissingNames(List<GlossaryByQueryResult> pairs, List<String> dtoNames) {
        // Find Pair's first values that are not in dtoNames
        return pairs.stream()
                .map(GlossaryByQueryResult::getName)
                .filter(name -> !dtoNames.contains(name))
                .collect(Collectors.toList());
    }

    private Page<ChapterGlossary> processUniqueState(@NonNull String bookId, Page<ChapterGlossary> result) {
        var lastGlossary = result.getContent().getLast();
        var names = result.map(chapterGlossary -> chapterGlossary.getObjectName().getName()).getContent();

        var countResults = chapterRepository.countGlossaryInPreviousChapters(names, lastGlossary.getChapterNumber(), bookId).stream()
                .collect(Collectors.toMap(CountResult::getName, CountResult::getCount));

        return result.map(chapterGlossary -> {
            var name = chapterGlossary.getObjectName().getName();
            chapterGlossary.setUnique(countResults.getOrDefault(name, 0L) <= 1L);
            return chapterGlossary;
        });
    }

}
