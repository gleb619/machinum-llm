package machinum.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Constants;
import machinum.controller.BookOperationController.BookOperationRequest;
import machinum.exception.AppIllegalStateException;
import machinum.extract.Glossary;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.model.Chunks;
import machinum.model.ObjectName;
import machinum.util.JavaUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

import static machinum.flow.FlowContextActions.glossary;
import static machinum.flow.FlowContextActions.iteration;
import static machinum.service.BookProcessor.ProcessorState.defaultState;
import static machinum.service.BookWindowProcessor.Operations.TRANSLATE_GLOSSARY;

@Slf4j
@Service
@RequiredArgsConstructor
@Deprecated(forRemoval = true)
public class BookWindowProcessor {

    private final BookFacade bookFacade;
    private final Glossary glossary;
    private final TemplateAiFacade templateAiFacade;


    @Deprecated(forRemoval = true)
    public void doStart(BookOperationRequest request) {
        log.debug("Prepare to eecute window operation for book: {}", request.getId());

        switch (request.getOperationName()) {
            case TRANSLATE_GLOSSARY -> translateGlossary(request);
            default -> throw new AppIllegalStateException("Unknown operation: " + request.getOperationName());
        }
    }

    private void translateGlossary(BookOperationRequest request) {
        var names = bookFacade.exportGlossaryTranslation(request.getId());
        var namesChunks = JavaUtil.toChunks(names, 5);
        var translatedNames = new ArrayList<ObjectName>();
        for (var namesChunk : namesChunks) {
            var bootstrapContext = prepareContext(request)
                    .replace(FlowContext::glossaryArg, glossary(namesChunk));
            var context = glossary.glossaryTranslate(bootstrapContext);
            translatedNames.addAll(context.glossary());
        }
        System.out.println("BookWindowProcessor.translateGlossary");

        if (request.isShouldPersist()) {
            bookFacade.importGlossaryTranslation(request.getId(), translatedNames);
        }
    }

    private FlowContext<Chapter> prepareContext(BookOperationRequest request) {
        var emptyChapter = Chapter.builder()
                .id("1")
                .text("")
                .title("")
                .cleanChunks(Chunks.createNew())
                .number(1)
                .bookId(request.getId())
                .sourceKey("")
                .build();

        return templateAiFacade.bootstrapWith(FlowContext.<Chapter>builder()
                .state(defaultState())
                .metadata(Map.of(
                        Constants.BOOK_ID, request.getId(),
                        Constants.IGNORE_CACHE_MODE, request.isIgnoreCache(),
                        Constants.ALLOW_EMPTY_TEXT, Boolean.TRUE
                ))
                .currentItem(emptyChapter)
                .build()
                .rearrange(FlowContext::iterationArg, iteration(1)));
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Operations {

        public static final String TRANSLATE_GLOSSARY = "translate-glossary";

    }

}
