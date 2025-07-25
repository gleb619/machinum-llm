package machinum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.converter.ChapterMapper;
import machinum.model.Chapter;
import machinum.model.ChapterGlossary;
import machinum.model.ObjectName;
import machinum.repository.ChapterGlossaryRepository;
import machinum.repository.ChapterGlossaryRepository.GlossaryByQueryResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static machinum.util.JavaUtil.uniqueBy;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterGlossaryService {

    private final ChapterGlossaryRepository chapterRepository;
    @Qualifier("objectMapperHolder")
    private final Holder<ObjectMapper> objectMapperHolder;
    private final ChapterMapper chapterMapper;


    @Transactional(readOnly = true)
    public List<Chapter> findChaptersByGlossary(@NonNull List<String> names, @NonNull String bookId) {
        if (names.isEmpty()) {
            return Collections.emptyList();
        }

        var pairs = chapterRepository.findChaptersByGlossary(names, bookId);

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
        return chapterRepository.findGlossary(bookId, pageRequest)
                .map(projection -> {
                    var dto = chapterMapper.toDto(projection);
                    var objectName = objectMapperHolder.execute(mapper -> mapper.readValue(projection.getRawJson(), ObjectName.class));
                    dto.setObjectName(objectName);

                    return dto;
                });
    }

    @Transactional(readOnly = true)
    public Page<ChapterGlossary> findBookTranslatedGlossary(@NonNull String bookId, boolean translated, @NonNull PageRequest pageRequest) {
        log.debug("Prepare to load book's translated glossary from db: {}", bookId);
        return chapterRepository.findTranslatedGlossary(bookId, translated, pageRequest)
                .map(projection -> {
                    var dto = chapterMapper.toDto(projection);
                    var objectName = objectMapperHolder.execute(mapper -> mapper.readValue(projection.getRawJson(), ObjectName.class));
                    dto.setObjectName(objectName);

                    return dto;
                });
    }

    /* ============= */

    private List<String> findMissingNames(List<GlossaryByQueryResult> pairs, List<String> dtoNames) {
        // Find Pair's first values that are not in dtoNames
        return pairs.stream()
                .map(GlossaryByQueryResult::getName)
                .filter(name -> !dtoNames.contains(name))
                .collect(Collectors.toList());
    }

}
