package machinum.service;

import jakarta.persistence.EntityManager;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.flow.model.FlowContext;
import machinum.model.Book;
import machinum.model.Chapter;
import machinum.model.ChapterGlossary;
import machinum.model.ObjectName;
import machinum.processor.client.GeminiClient;
import machinum.util.TextUtil;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.async.AsyncHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.db.DbHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static machinum.config.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterFacade {

    private final ChapterService chapterService;
    private final ChapterGlossaryService chapterGlossaryService;
    private final LineService lineService;
    private final TemplateAiFacade templateAiFacade;
    private final LinesInfoDao lineDao;
    private final AsyncHelper asyncHelper;
    private final DbHelper dbHelper;

    @Value("${app.batch-size}")
    private final Integer batchSize;

    public Chapter refresh(FlowContext<Chapter> context) {
        return chapterService.refresh(context);
    }

    public FlowContext<Chapter> bootstrap(FlowContext<Chapter> context) {
        var bootstrap = Boolean.parseBoolean(context.metadata(BOOTSTRAP));
        if (!bootstrap) {
            if (context.isEmpty()) {
                var flow = context.getFlow();
                var currentChap = context.getCurrentItem();
                var previousChap = chapterService.findByNumber(currentChap.getBookId(),
                        currentChap.getNumber() - 1).orElse(context.getCurrentItem());
                var initState = flow.isInitState(context.getState());
                var actualChap = loadItemForBootstrap(initState, previousChap);

                var bootstrapContext = templateAiFacade.bootstrapWith(
                                context.copy(b -> b.currentItem(actualChap)
                                        .metadata(BOOTSTRAP, Boolean.TRUE)))
                        .withoutOldAndEmpty();

                var toRemove = bootstrapContext.getArguments().stream()
                        .filter(arg -> arg.isEmpty() || arg.isOld())
                        .collect(Collectors.toList());

                return bootstrapContext.removeArgs(toRemove);
            } else {
                return context.copy(b -> b.metadata(BOOTSTRAP, Boolean.TRUE));
            }
        }

        return context;
    }

    public void saveWithContext(FlowContext<Chapter> context) {
        //TODO: add persist on GLOSSARY_CONSOLIDATION
        chapterService.saveWithContext(context);
    }

    @SneakyThrows
    public Chapter updateChapter(Chapter updatedChapter) {
        var result = chapterService.updateChapter(updatedChapter);
        asyncHelper.inNewTransaction(() ->
                        lineDao.refreshView(updatedChapter.getBookId(), updatedChapter.getNumber()),
                (r, throwable) -> log.debug("MatView update status: {}", r ? "success" : "failed"));

        return result;
    }

    public FlowContext<Chapter> extend(FlowContext<Chapter> context) {
        List<String> processedChunks = context.metadata(PROCESSED_CHUNKS);
        if (processedChunks.isEmpty()) {
            return context;
        }

        log.debug("Prepare to extend new batch from another one");
        List<Chapter> processedChunk = context.metadata(PROCESSED_CHUNK);
        if (Objects.nonNull(processedChunk) && !processedChunk.isEmpty()) {
            var lastItem = processedChunk.getLast();

            if (context.isEmpty() && Objects.nonNull(lastItem)) {
                var itemFromDB = chapterService.getById(lastItem.getId());

                var contextWithArgs = templateAiFacade.bootstrapWith(
                        context.copy(b -> b.currentItem(itemFromDB)));
                return context.copy(b -> b.arguments(contextWithArgs.getArguments()));
            }
        }

        return context;
    }

    public Page<Chapter> getSuspiciousChapters(@NonNull String bookId, boolean findAberrationsInTranslate,
                                               boolean suspiciousOriginalWords, boolean suspiciousTranslatedWords,
                                               PageRequest request, boolean warnings) {
        if (findAberrationsInTranslate && (suspiciousOriginalWords || suspiciousTranslatedWords)) {
            throw new AppIllegalStateException("Not supported!");
        } else if (findAberrationsInTranslate) {
            return getAberrationChapters(bookId, request);
        } else if (suspiciousOriginalWords) {
            return getSuspiciousOriginalChapters(bookId, request);
        } else if (suspiciousTranslatedWords) {
            return getSuspiciousTranslatedChapters(bookId, request);
        } else if (warnings) {
            return chapterService.getChaptersWithWarnings(bookId, request);
        }

        return new PageImpl<>(Collections.emptyList());
    }

    public Page<Chapter> getAberrationChapters(@NonNull String bookId, PageRequest request) {
        var ids = lineService.getEnglishInTranslatedChapterIds(bookId, request);
        return new PageImpl<>(chapterService.getChapterByIds(ids.getContent()), ids.getPageable(), ids.getTotalElements());
    }

    public Page<Chapter> getSuspiciousOriginalChapters(@NonNull String bookId, PageRequest request) {
        var ids = lineService.getSuspiciousInOriginalChapterIds(bookId, request);
        return new PageImpl<>(chapterService.getChapterByIds(ids.getContent()), ids.getPageable(), ids.getTotalElements());
    }

    public Page<Chapter> getSuspiciousTranslatedChapters(@NonNull String bookId, PageRequest request) {
        var ids = lineService.getSuspiciousInTranslatedChapterIds(bookId, request);
        return new PageImpl<>(chapterService.getChapterByIds(ids.getContent()), ids.getPageable(), ids.getTotalElements());
    }

    public void handleChapterException(@NonNull FlowContext<Chapter> ctx, @NonNull Exception e) {
        if (e instanceof GeminiClient.BusinessGeminiException bge) {
            var context = ObjectUtils.firstNonNull(bge.getFlowContext(), ctx);
            chapterService.addWarning((FlowContext) context);
        }

        log.warn("Handled chapter's exception: context={}, e={}", ctx, e.getMessage());
    }

    /**
     * 'false' - pipe won't be executed
     * 'true' - pipe will be executed
     *
     * @param ctx
     * @return
     */
    public boolean checkExecutionIsAllowed(FlowContext<Chapter> ctx) {
        log.debug("Prepare to update chapter in db: {}", ctx);
        var state = (BookProcessor.ProcessorState) ctx.getState();

        switch (state) {
            case CLEANING, PROOFREAD -> {
                //TODO: This will not work
                if (1 < 2) {
                    throw new AppIllegalStateException("Stop execution");
                }
                return TextUtil.isEmpty(ctx.getCurrentItem().getText());
            }
            case SUMMARY -> {
                return TextUtil.isEmpty(ctx.getCurrentItem().getSummary());
            }
            case GLOSSARY, TRANSLATE_GLOSSARY -> {
                return CollectionUtils.isEmpty(ctx.getCurrentItem().getNames());
            }
            case TRANSLATE_TITLE -> {
                return TextUtil.isEmpty(ctx.getCurrentItem().getTranslatedTitle());
            }
            case TRANSLATE, COPYEDIT -> {
                return TextUtil.isEmpty(ctx.getCurrentItem().getTranslatedText());
            }
            case FINISHED -> {
                return true;
            }
            default -> throw new AppIllegalStateException("Unknown state: %s".formatted(state));
        }
    }

    public Chapter loadItemForBootstrap(boolean initState, Chapter currentItem) {
        //For second step and next, we try to refresh item from db
        if (!initState) {
            log.debug("Bootstrap data from db for: {}", currentItem);

            return chapterService.getById(currentItem.getId());
        }

        return currentItem;
    }

    public void importGlossaryTranslation(Book book, List<ObjectName> newNames) {
        log.debug("Import glossary translation for book: {}, list={}", book.getId(), newNames.size());

        dbHelper.doInNewTransaction(context -> {
            var entityManager = context.getBean(EntityManager.class);
            for (int i = 0; i < book.getChapters().size(); i++) {
                var chapter = book.getChapters().get(i);
                chapterService.updateGlossaryTranslation(chapter, newNames);

                if (i % batchSize == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
        });
    }

    public void updateGlossary(String chapterId, ChapterGlossary glossaryFromReq) {
        var template = chapterService.getById(chapterId);
        var glossaryFromDb = chapterGlossaryService.getById(glossaryFromReq.getId());
        var objectFromReq = glossaryFromReq.getObjectName();
        var objectFromDb = glossaryFromDb.getObjectName();
        var chapters = chapterGlossaryService.findChaptersByGlossary(List.of(objectFromDb.getName()), template.getBookId());

        boolean dbHasRuName = objectFromDb.hasRuName();
        boolean reqHasRuName = objectFromReq.hasRuName();
        if (dbHasRuName && reqHasRuName && !Objects.equals(objectFromDb.ruName(), objectFromReq.ruName())) {
            dbHelper.doInNewTransaction(() -> {
                for (var chapter : chapters) {
                    chapterService.updateGlossaryTranslation(chapter, List.of(objectFromReq));
                }
            });
        } else if (objectFromReq.getMetadata().containsKey("newName")) {
            dbHelper.doInNewTransaction(() -> {
                for (var chapter : chapters) {
                    chapterService.updateGlossaryName(chapter, List.of(objectFromReq));
                }
            });
        }
    }

}
