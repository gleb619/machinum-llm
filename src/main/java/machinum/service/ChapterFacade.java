package machinum.service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.model.ChapterGlossary;
import machinum.processor.core.GeminiClient;
import machinum.repository.LineDao;
import machinum.util.TextUtil;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.async.AsyncHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.db.DbHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static machinum.config.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterFacade {

    private final ChapterService chapterService;
    private final ChapterGlossaryService chapterGlossaryService;
    private final LineService lineService;
    private final TemplateAiFacade templateAiFacade;
    private final LineDao lineDao;
    private final AsyncHelper asyncHelper;
    private final DbHelper dbHelper;

    public Chapter refresh(FlowContext<Chapter> context) {
        return chapterService.refresh(context);
    }

    public FlowContext<Chapter> bootstrap(FlowContext<Chapter> context) {
        var bootstrap = Boolean.parseBoolean(context.metadata(BOOTSTRAP));
        if (!bootstrap) {
            var chapterInfo = chapterService.bootstrap(context);

            if (context.isEmpty()) {
                return templateAiFacade.bootstrapWith(
                        context.copy(b -> b.currentItem(chapterInfo)
                                .metadata(BOOTSTRAP, Boolean.TRUE)));
            }

            return context.copy(b -> b.currentItem(chapterInfo)
                    .metadata(BOOTSTRAP, Boolean.TRUE));
        }

        return context;
    }

    public void saveWithContext(FlowContext<Chapter> context) {
        chapterService.saveWithContext(context);
    }

    public Chapter updateChapter(Chapter updatedChapter) {
        var result = chapterService.updateChapter(updatedChapter);
        asyncHelper.runAsync(() -> dbHelper.doInNewTransaction(lineDao::refreshMaterializedView))
                .whenComplete((unused, throwable) -> log.debug("MatView has been updated"));

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
            case CLEANING, PROCESSING -> {
                //TODO: This will not work
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

    public Page<ChapterGlossary> findBookGlossary(String bookId, PageRequest pageRequest) {
        return chapterGlossaryService.findBookGlossary(bookId, pageRequest);
    }

}
