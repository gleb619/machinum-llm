package machinum.service;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static machinum.config.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterFacade {

    private final ChapterService chapterService;
    private final LineService lineService;
    private final TemplateAiFacade templateAiFacade;

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
                                               PageRequest request) {
        if (findAberrationsInTranslate && (suspiciousOriginalWords || suspiciousTranslatedWords)) {
            throw new IllegalStateException("Not supported!");
        } else if (findAberrationsInTranslate) {
            return getAberrationChapters(bookId, request);
        } else if (suspiciousOriginalWords) {
            return getSuspiciousOriginalChapters(bookId, request);
        } else if (suspiciousTranslatedWords) {
            return getSuspiciousTranslatedChapters(bookId, request);
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

}
