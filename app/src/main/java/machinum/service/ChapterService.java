package machinum.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Holder;
import machinum.controller.ChapterController.ChapterSearchRequest;
import machinum.converter.ChapterMapper;
import machinum.entity.ChapterEntity;
import machinum.exception.AppIllegalStateException;
import machinum.flow.AppFlowActions;
import machinum.flow.model.FlowContext;
import machinum.flow.model.Pack;
import machinum.listener.ChapterEntityListener;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.repository.ChapterGlossaryRepository.GlossaryByQueryResult;
import machinum.repository.ChapterIndexRepository;
import machinum.repository.ChapterRepository;
import machinum.util.LanguageDetectorUtil;
import machinum.util.TextUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static machinum.config.Constants.TRANSLATED_TITLE;
import static machinum.listener.ChapterEntityListener.ChapterInfoConstants.CLEAN_TEXT;
import static machinum.listener.ChapterEntityListener.ChapterInfoConstants.TRANSLATED_TEXT;
import static machinum.util.LanguageDetectorUtil.Lang.RUSSIAN;
import static machinum.util.TextUtil.isNotEmpty;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterService {

    public static final String NEW_EN_NAME = "newName";

    private final ChapterIndexRepository chapterIndexRepository;
    private final ChapterRepository chapterRepository;
    private final ChapterMapper chapterMapper;
    @Qualifier("objectMapperHolder")
    private final Holder<ObjectMapper> objectMapperHolder;
    private final ChapterEntityListener chapterEntityListener;

    @Value("${app.batch-size}")
    private final Integer batchSize;

    @PersistenceContext
    private EntityManager entityManager;


    @Transactional(readOnly = true)
    public Page<Chapter> getAllChapters(PageRequest pageRequest) {
        log.debug("Get all chapters from db for all books");
        return chapterRepository.findAll(pageRequest)
                .map(chapterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<Chapter> getChapterById(String id) {
        log.debug("Get chapter from db: {}", id);
        return chapterRepository.findById(id)
                .map(chapterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<Chapter> getChapterByIds(List<String> ids) {
        log.debug("Get chapters from db: {} items", ids.size());
        return chapterMapper.toDto(chapterRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(ChapterEntity::getNumber))
                .collect(Collectors.toList()));
    }

    @Transactional(readOnly = true)
    public Page<Chapter> getChaptersWithWarnings(String bookId, PageRequest pageRequest) {
        log.debug("Get chapters with warnings for book: {}", bookId);
        return chapterRepository.findChaptersWithWarningsByBookId(bookId, pageRequest)
                .map(chapterMapper::toDto);
    }

    @Transactional
    public Chapter createChapter(Chapter chapter) {
        log.debug("Creating chapter in db: {}", chapter);
        return chapterMapper.toDto(chapterRepository.save(
                chapterMapper.toEntity(chapter)));
    }

    @Transactional
    public Chapter updateChapter(Chapter updatedChapter) {
        log.debug("Update chapter in db: {}", updatedChapter);
        var inputItem = chapterMapper.toEntity(updatedChapter);
        var updatedItem = chapterRepository.save(inputItem);

        return chapterMapper.toDto(updatedItem);
    }

    @Transactional
    public Optional<Chapter> patchChapter(String id, Chapter updatedChapter) {
        log.debug("Update chapter in db: {}, chapter={}", id, updatedChapter);
        return chapterRepository.findById(id)
                .map(chapterMapper::toDto)
                .map(existingChapter -> {
                    existingChapter.setBookId(updatedChapter.getBookId());
                    existingChapter.setSourceKey(updatedChapter.getSourceKey());
                    existingChapter.setNumber(updatedChapter.getNumber());
                    existingChapter.setTitle(updatedChapter.getTitle());
                    existingChapter.setNames(updatedChapter.getNames());

                    return existingChapter;
                })
                .map(chapterMapper::toEntity)
                .map(chapterRepository::save)
                .map(chapterMapper::toDto);
    }

    @Transactional
    public void deleteChapter(String id) {
        log.debug("Removing chapter from db: {}", id);
        chapterRepository.deleteById(id);
    }

    @Transactional
    public boolean updateNameInChapter(String chapterId, String oldName, ObjectName updatedName) {
        log.debug("Update chapter in db: {}", chapterId);
        return chapterRepository.findById(chapterId)
                .map(chapter -> {
                    List<ObjectName> names = chapter.getNames();
                    for (ObjectName name : names) {
                        if (name.getName().equals(oldName)) {
                            name.setName(updatedName.getName());
                            name.setCategory(updatedName.getCategory());
                            name.setDescription(updatedName.getDescription());
                            name.setMetadata(updatedName.getMetadata());
                            chapterRepository.save(chapter);
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    @Transactional
    public boolean addNameToChapter(String chapterId, ObjectName newName) {
        log.debug("Add name to chapter in db: {}", chapterId);
        return chapterRepository.findById(chapterId)
                .map(chapter -> {
                    chapter.getNames().add(newName);
                    chapterRepository.save(chapter);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean removeNameFromChapter(String chapterId, String nameToRemove) {
        log.debug("Remove name in chapter in db: {}", chapterId);
        return chapterRepository.findById(chapterId)
                .map(chapter -> {
                    boolean removed = chapter.getNames().removeIf(name -> name.getName().equals(nameToRemove));
                    if (removed) {
                        chapterRepository.save(chapter);
                    }
                    return removed;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Page<Chapter> loadBookChapters(String bookId, PageRequest pageRequest) {
        log.debug("Loading all chapters from db: {}", bookId);
        var chapters = chapterRepository.findAllByBookId(bookId, pageRequest.withSort(Sort.by("number")));
        return chapters.map(chapterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<Chapter> loadBookChapters(String bookId, Integer startNumber, Integer endNumber, PageRequest pageRequest) {
        log.debug("Loading specific chapters from db: {}, start={}, end={}", bookId, startNumber, endNumber);
        var chapters = chapterRepository.findAllByBookIdAndNumberBetween(bookId, startNumber, endNumber,
                pageRequest.withSort(Sort.by("number")));
        return chapters.map(chapterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<Chapter> findByNumber(String bookId, Integer chapterNumber) {
        if (chapterNumber <= 0) {
            return Optional.empty();
        }

        log.debug("Loading book chapter chapter from db: {}, number={}", bookId, chapterNumber);
        return chapterRepository.findOneByBookIdAndNumber(bookId, chapterNumber)
                .map(chapterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<Chapter> loadReadyChapters(String bookId, Integer startNumber, Integer endNumber) {
        log.debug("Loading ready chapters from db: {}", bookId);
        var chapters = chapterRepository.findReadyChapters(bookId, startNumber, endNumber, Sort.by("number"));
        return chapterMapper.toDto(chapters);
    }

    @Transactional
    public void saveWithContext(@NonNull FlowContext<Chapter> ctx) {
        //TODO: add persist on GLOSSARY_CONSOLIDATION
        log.debug("Prepare to update chapter in db: {}", ctx);
        var state = (BookProcessor.ProcessorState) ctx.getState();
        var chapterInfo = ctx.getCurrentItem();

        switch (state) {
            case CLEANING -> {
                ctx.optionalValue(FlowContext::textArg)
                        .ifPresent(cleanText -> {
                            ctx.getCurrentItem().setText(cleanText);
                            chapterEntityListener.trackChange(chapterMapper.toEntity(ctx.getCurrentItem()), CLEAN_TEXT);
                            chapterRepository.updateCleanText(chapterInfo.getId(), cleanText);
                        });
            }
            case SUMMARY -> {
                //TODO track changes for summary field
                ctx.optionalValue(FlowContext::chapContextArg)
                        .ifPresent(context -> {
                            ctx.getCurrentItem().setSummary(context);
                            chapterRepository.updateSummary(chapterInfo.getId(), context);
                        });
            }
            case GLOSSARY -> {
                ctx.optionalValue(AppFlowActions::glossaryArg)
                        .ifPresent(glossary -> {
                            ctx.getCurrentItem().setNames(glossary);
                            var glossaryText = objectMapperHolder.execute(mapper -> toJsonString(mapper, glossary));
                            chapterRepository.updateGlossary(chapterInfo.getId(), glossaryText);
                        });
                ctx.optionalValue(FlowContext::chunksArg)
                        .ifPresent(chunks -> {
                            ctx.getCurrentItem().setCleanChunks(chunks);
                            var chunksText = objectMapperHolder.execute(mapper -> toJsonString(mapper, chunks));
                            chapterRepository.updateCleanChunks(chapterInfo.getId(), chunksText);
                        });
            }
            case PROOFREAD -> {
                ctx.optionalValue(FlowContext::proofreadArg)
                        .ifPresent(proofreadText -> {
                            ctx.getCurrentItem().setText(proofreadText);
                            chapterEntityListener.trackChange(chapterMapper.toEntity(ctx.getCurrentItem()), CLEAN_TEXT);
                            chapterRepository.updateCleanText(chapterInfo.getId(), proofreadText);
                        });
            }
            case TRANSLATE_GLOSSARY -> {
                ctx.optionalValue(AppFlowActions::glossaryArg)
                        .ifPresent(glossary -> {
                            ctx.getCurrentItem().setNames(glossary);
                            var glossaryText = objectMapperHolder.execute(mapper -> toJsonString(mapper, glossary));
                            chapterRepository.updateGlossary(chapterInfo.getId(), glossaryText);
                        });
            }
            case TRANSLATE_TITLE -> {
                //TODO add result handler for batch result
                ctx.optionalValue(context -> context.arg(TRANSLATED_TITLE))
                        .map(TextUtil::valueOf)
                        .ifPresent(translatedTitle -> {
                            ctx.getCurrentItem().setTranslatedTitle(translatedTitle);
                            chapterRepository.updateTranslatedTitle(chapterInfo.getId(), translatedTitle);
                        });
                ctx.optionalValue(FlowContext::resultArg)
                        .filter(val -> val instanceof List<?> l && l.getFirst() instanceof Pack)
                        .map(val -> (List<Pack<Chapter, String>>) val)
                        .ifPresent(result -> {
                            for (Pack<Chapter, String> pack : result) {
                                var value = pack.getArgument().stringValue();
                                chapterRepository.updateTranslatedTitle(pack.getItem().getId(), value);
                                ctx.getCurrentItem().setTranslatedTitle(value);
                            }
                        });
            }
            case TRANSLATE, COPYEDIT -> {
                ctx.optionalValue(FlowContext::translatedTextArg)
                        .ifPresent(translatedText -> {
                            ctx.getCurrentItem().setTranslatedText(translatedText);
                            chapterEntityListener.trackChange(chapterMapper.toEntity(ctx.getCurrentItem()), TRANSLATED_TEXT);
                            chapterRepository.updateTranslatedText(chapterInfo.getId(), translatedText);
                        });
                ctx.optionalValue(FlowContext::translatedChunksArg)
                        .ifPresent(translatedChunks -> {
                            ctx.getCurrentItem().setTranslatedChunks(translatedChunks);
                            var chunksText = objectMapperHolder.execute(mapper -> toJsonString(mapper, translatedChunks));
                            chapterRepository.updateTranslatedChunks(chapterInfo.getId(), chunksText);
                        });
            }
            case SYNTHESIZE -> log.trace("Ignore chapter changing on audio generation...");
            case FINISHED -> {
            }
            default -> throw new AppIllegalStateException("Unknown state: %s".formatted(state));
        }
    }

    @Transactional
    public String save(@NonNull Chapter chapter) {
        log.debug("Prepare to save chapter to db: {}", chapter);
        var entity = chapterMapper.toEntity(chapter);
        var saved = chapterRepository.save(entity);

        return saved.getId();
    }

    @Transactional
    public List<Chapter> saveAll(@NonNull List<Chapter> chunk) {
        log.debug("Prepare to save batch of chapters to db: {}", chunk.size());
        if (chunk.isEmpty()) {
            return chunk;
        }

        var entities = chapterMapper.toEntity(chunk);
        var persisted = chapterRepository.saveAll(entities);
        return chapterMapper.toDto(persisted);
    }

    @Transactional
    public String saveWithIndex(@NonNull Chapter chapter) {
        var id = save(chapter);
        chapterIndexRepository.index(getById(id));

        return id;
    }

    @Transactional(readOnly = true)
    public Optional<Chapter> findPrevious(String chapterId) {
        log.debug("Prepare to find previous chapter from db: {}", chapterId);
        return chapterRepository.findPrevious(chapterId)
                .map(chapterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Chapter getById(@NonNull String id) {
        return findById(id)
                .orElseThrow(AppIllegalStateException::new);
    }

    @Transactional(readOnly = true)
    public Optional<Chapter> findById(@NonNull String id) {
        log.debug("Prepare to load chapter from db: {}", id);
        return chapterRepository.findById(id)
                .map(chapterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<Chapter> findSame(@NonNull Chapter chapter) {
        log.debug("Prepare to load existed chapter from db: {}", chapter);
        return chapterRepository.findOneByBookIdAndTitleAndNumber(chapter.getBookId(), chapter.getTitle(), chapter.getNumber())
                .map(chapterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Chapter refresh(FlowContext<Chapter> context) {
        var flow = context.getFlow();
        var currentState = context.getState();
        var nextState = flow.nextState(currentState);
        var initState = flow.isInitState(currentState);

        //For second step and next, we try to refresh item from db
        if (Objects.nonNull(nextState) && !initState) {
            var currentItem = context.getCurrentItem();
            log.debug("Refreshing data from db for: {}", currentItem);

            return getById(currentItem.getId());
        }

        return context.getCurrentItem();
    }

    @Transactional(readOnly = true)
    public Page<Chapter> findByChapterText(ChapterSearchRequest request) {
        log.debug("Loading chapters by query from db: {}, query={}", request.getBookId(), request.getQuery());
        var chapters = chapterRepository.searchInText(request.getBookId(), request.getQuery(),
                request.isChapterMatchCase(), request.isChapterWholeWord(), request.isChapterRegex(),
                request.getPageRequest());
        return chapters.map(chapterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<Chapter> findByChapterNames(ChapterSearchRequest request) {
        log.debug("Loading chapters by query to names from db: {}, query={}", request.getBookId(), request.getQuery());
        var chapters = chapterRepository.searchInGlossary(request.getBookId(), request.getQueryNames(),
                request.isNamesMatchCase(), request.isNamesWholeWord(), request.isNamesRegex(), request.getPageRequest());
        return chapters.map(chapterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<Chapter> findByCombinedCriteria(String bookId, String query, String nameQuery, PageRequest pageRequest) {
        log.debug("Loading chapters by queries to chapter's text and glossary from db: {}, query={}, nameQuery={}", bookId, query, nameQuery);
        var chapters = chapterRepository.searchByCombinedCriteria(bookId, query, nameQuery, pageRequest);
        return chapters.map(chapterMapper::toDto);
    }

    //TODO move to facade
    @Transactional
    public void importTranslation(String bookId, List<Chapter> list) {
        log.debug("Import translation for book: {}, list={}", bookId, list.size());

        boolean importMode = parseImportMode(list);

        for (int i = 0; i < list.size(); i++) {
            var chapterInfo = list.get(i);
            var translatedText = importMode ? chapterInfo.getTranslatedText() : chapterInfo.getText();
            var translatedTitle = importMode ? chapterInfo.getTranslatedTitle() : chapterInfo.getTitle();

            if (isNotEmpty(translatedText)) {
                var entity = chapterMapper.toEntity(chapterInfo).toBuilder()
                        .translatedText(translatedText)
                        .build();
                chapterEntityListener.overrideChange(entity, TRANSLATED_TEXT);
                chapterRepository.updateTranslatedTextByKey(bookId, chapterInfo.getSourceKey(), translatedText);
            }
            if (isNotEmpty(translatedTitle)) {
                chapterRepository.updateTranslatedTitleByKey(bookId, chapterInfo.getSourceKey(), translatedTitle);
            }

            if (i % batchSize == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        log.debug("Translation was imported for book: {}, list={}", bookId, list.size());
    }

    @Transactional
    public void addWarning(FlowContext<Chapter> ctx) {
        var chapterInfo = ctx.getCurrentItem();

        ctx.optionalValue(AppFlowActions::warningArg)
                .ifPresent(warning -> {
                    var warnings = ctx.getCurrentItem().addWarning(warning).getWarnings();
                    var warningText = objectMapperHolder.execute(mapper -> toJsonString(mapper, warnings));
                    chapterRepository.updateWarning(chapterInfo.getId(), warningText);
                });
    }

    @Transactional(readOnly = true)
    public Page<Chapter> findTitles(String bookId, PageRequest pageRequest) {
        return chapterRepository.findTitles(bookId, pageRequest)
                .map(chapterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<Chapter> findMissingTitles(String bookId, PageRequest pageRequest) {
        return chapterRepository.findMissingTitles(bookId, pageRequest)
                .map(chapterMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<Chapter> findAberrationTitles(String bookId, PageRequest pageRequest) {
        return chapterRepository.findAberrationTitles(bookId, pageRequest)
                .map(chapterMapper::toDto);
    }

    @Transactional
    public Chapter updateTitleFields(String id, String title, String translatedTitle) {
        ChapterEntity entity = chapterRepository.findById(id)
                .orElseThrow(() -> new AppIllegalStateException("Chapter not found for id: %s", id));

        if (title != null) {
            entity.setTitle(title);
        }

        if (translatedTitle != null) {
            entity.setTranslatedTitle(translatedTitle);
        }

        return chapterMapper.toDto(chapterRepository.save(entity));
    }

    @Transactional
    public void updateGlossaryTranslation(Chapter chapter, List<ObjectName> newNames) {
        boolean persist = false;

        for (var newObjectName : newNames) {
            var chapterName = chapter.findObjectName(newObjectName.getName());
            if (Objects.nonNull(chapterName)) {
                chapter.replaceObjectName(chapterName, chapterName.withRuName(newObjectName.ruName()));
                persist = true;
            }
        }

        if (persist) {
            chapterRepository.updateGlossary(chapter.getId(), objectMapperHolder.execute(mapper -> mapper.writeValueAsString(chapter.getNames())));
        }
    }

    @Transactional
    public void updateGlossaryName(Chapter chapter, List<ObjectName> newNames) {
        boolean persist = false;

        for (var newObjectName : newNames) {
            var newName = Objects.toString(newObjectName.getMetadata().get(NEW_EN_NAME), null);
            var objectInChapter = chapter.findObjectName(newObjectName.getName());
            if (Objects.nonNull(objectInChapter)) {
                int targetIndex = chapter.getNames().indexOf(objectInChapter);
                chapter.getNames().set(targetIndex, objectInChapter.toBuilder()
                        .name(newName)
                        .build());
                persist = true;
            }
        }

        if (persist) {
            chapterRepository.updateGlossary(chapter.getId(), objectMapperHolder.execute(
                    mapper -> mapper.writeValueAsString(chapter.getNames())));
        }
    }

    @Transactional(readOnly = true)
    public int countByBookId(@NonNull String id) {
        return Math.toIntExact(chapterRepository.countByBookId(id));
    }

    /* ============= */

    private String toJsonString(ObjectMapper mapper, Object value) throws JsonProcessingException {
        return mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(value);
    }

    @Deprecated(forRemoval = true)
    private List<String> findMissingNames(List<GlossaryByQueryResult> pairs, List<String> dtoNames) {
        // Find Pair's first values that are not in dtoNames
        return pairs.stream()
                .map(GlossaryByQueryResult::getName)
                .filter(name -> !dtoNames.contains(name))
                .collect(Collectors.toList());
    }

    private boolean parseImportMode(List<Chapter> list) {
        // if true then use content of translated fields, otherwise use regular text fields
        if (!list.isEmpty()) {
            var first = list.getFirst();
            var text = first.getText();
            var translatedText = first.getTranslatedText();

            var lang = LanguageDetectorUtil.detectLang(text);
            return isNotEmpty(translatedText) && RUSSIAN.equals(lang);
        } else {
            return true;
        }
    }

}
