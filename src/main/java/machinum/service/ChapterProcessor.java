package machinum.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Constants;
import machinum.controller.ChapterOperationController.ChapterOperationRequest;
import machinum.exception.AppIllegalStateException;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.service.BookProcessor.ProcessorState;
import org.springframework.async.AsyncHelper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static machinum.flow.FlowContextActions.iteration;
import static machinum.service.BookProcessor.ProcessorState.*;
import static machinum.service.ChapterProcessor.Operations.TRANSLATE;
import static machinum.service.ChapterProcessor.Operations.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterProcessor {

    private final AsyncHelper asyncHelper;
    private final RawProcessor.State state;
    private final ChapterService chapterService;
    private final TemplateAiFacade templateAiFacade;

    public CompletableFuture<Void> start(ChapterOperationRequest request) {
        return asyncHelper.runAsync(() -> {
            try {
                doStart(request);
            } catch (Exception e) {
                log.error("ERROR: ", e);
            }
        }).thenApply(unused -> {
            state.free();

            return unused;
        });
    }

    @SneakyThrows
    public void doStart(ChapterOperationRequest request) {
        log.debug("Prepare to process chapter with ai: {}", request);

        var flowContext = prepareContext(request);

        var result = switch (request.getOperationName()) {
            case SUMMARIZE -> templateAiFacade.summary(flowContext)
                    .withState(SUMMARY);
            case TRANSLATE -> templateAiFacade.translateAll(flowContext)
                    .withState(ProcessorState.TRANSLATE);
//            case TRANSLATE -> templateAiFacade.translateInChunks(flowContext)
//                    .withState(ProcessorState.TRANSLATE);
            case SCORE_AND_TRANSLATE -> templateAiFacade.scoreAndTranslateInChunks(flowContext)
                    .withState(ProcessorState.TRANSLATE);
            case SCORE_AND_FIX -> templateAiFacade.scoreAndEditGrammarInChunks(flowContext)
                    .withState(COPYEDIT);
//            case FIX_GRAMMAR -> templateAiFacade.editGrammarInChunks(flowContext)
            case FIX_GRAMMAR -> templateAiFacade.editWithGlossary(flowContext)
                    .withState(COPYEDIT);
            case GLOSSARY_EXTRACT -> templateAiFacade.glossary(flowContext)
                    .withState(GLOSSARY);
            case PROOFREAD_RU -> templateAiFacade.proofreadRu(flowContext)
                    .withState(COPYEDIT);
            case CONVERT_TO_SSML -> templateAiFacade.convertToSSML(flowContext);
            case Operations.SYNTHESIZE -> templateAiFacade.synthesize(flowContext);
            default -> throw new AppIllegalStateException("Unknown operation: " + request.getOperationName());
        };

        log.debug("Processed chapter: {}", result);

        if (request.isShouldPersist()) {
            chapterService.saveWithContext(result);
        }
    }

    private FlowContext<Chapter> prepareContext(ChapterOperationRequest request) {
        Optional<Chapter> previous = chapterService.findPrevious(request.getId());
        Chapter chapter = chapterService.getById(request.getId());

        return bootstrap(previous, FlowContext.<Chapter>builder()
                .state(defaultState())
                .metadata(Map.of(
                        Constants.BOOK_ID, chapter.getBookId(),
                        Constants.IGNORE_CACHE_MODE, request.isIgnoreCache()
                ))
                .currentItem(chapter)
                .build()
                .rearrange(FlowContext::iterationArg, iteration(1)), chapter);
    }

    private FlowContext<Chapter> bootstrap(Optional<Chapter> previous, FlowContext<Chapter> flowContext, Chapter chapter) {
        if (previous.isPresent()) {
            return previous.map(item -> templateAiFacade.bootstrapWith(flowContext.withCurrentItem(previous.get())))
                    .map(ctx -> templateAiFacade.bootstrapWith(ctx.withCurrentItem(chapter)))
                    .orElse(flowContext);
        } else {
            return templateAiFacade.bootstrapWith(flowContext);
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Operations {

        public static final String SUMMARIZE = "summarize";

        public static final String TRANSLATE = "translate";

        public static final String SCORE_AND_TRANSLATE = "scoreAndTranslate";

        public static final String SCORE_AND_FIX = "scoreAndFix";

        public static final String FIX_GRAMMAR = "fixGrammar";

        public static final String GLOSSARY_EXTRACT = "glossaryExtract";

        public static final String PROOFREAD_RU = "proofreadRu";

        public static final String CONVERT_TO_SSML = "convertToSSML";

        public static final String SYNTHESIZE = "synthesize";

    }

}
