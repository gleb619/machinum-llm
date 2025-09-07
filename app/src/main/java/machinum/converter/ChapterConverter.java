package machinum.converter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.core.FlowContext;
import machinum.model.Chapter;
import machinum.service.ChapterService;
import machinum.service.TemplateAiFacade;
import machinum.util.DurationMeasureUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheHelper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

import static machinum.config.Constants.BOOK_ID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterConverter {

    public static final String MAX_MODE = "max";
    public static final String NORMAL_MODE = "normal";

    @Value("${app.convert-mode:normal}")
    private final String convertMode;

    private final TemplateAiFacade templateAiFacade;

    private final CacheHelper cacheHelper;

    private final ChapterService chapterService;


    @Deprecated
    public Chapter convert(Map<String, Object> context, Integer number, Chapter source) {
        String bookId = (String) context.get(BOOK_ID);
        return convert(source, number, bookId);
    }

    public Chapter convert(Chapter source, Integer number, String bookId) {
        return source.toBuilder()
                .number(number)
                .bookId(bookId)
                .build();
    }

    public Chapter restore(Chapter source) {
        return chapterService.findSame(source)
                .orElseGet(() -> {
                    var id = chapterService.save(source);
                    return source.toBuilder()
                            .id(id)
                            .build();
                });
    }

    @Deprecated
    public FlowContext convert2(FlowContext flowContext) {
        Chapter source = null;
//        Chapter source = flowContext.getChapter();
        String id = source.getSourceKey();

        if (cacheHelper.cacheContainsKey(id)) {
            return cacheHelper.<FlowContext>getValue(id)
                    .orElseGet(() -> acquireChapterInfo(flowContext));
        }

        return acquireChapterInfo(flowContext);
    }

    @Deprecated
    private FlowContext<Chapter> acquireChapterInfo(FlowContext<Chapter> flowContext) {
        var source = flowContext.arg("chapter", Chapter.class)
                .getValue();
        var sourceKey = source.getSourceKey();
        log.debug("Prepare to create a chapter info: {}", sourceKey);
//        var text = source.getRawText();
        var state = flowContext.getState();

        return DurationMeasureUtil.measure("acquireChapterInfo", () -> {

            var chapterContext = flowContext;

//            var chapterContext = flowContext.copy(b -> b.argument(text(text)))
//                    .then(templateAiFacade::rewrite)
//                    .then(ctx -> {
//                        log.debug("Working with new text: {}", TextUtil.toShortDescription(ctx.text()));
//
//                        return ctx;
//                    })
//                    .then(this::toMin);


//            String proofreadText = templateAiFacade.proofread(text);
//            String coherentCoalesceText = templateAiFacade2.coherentCoalesce(proofreadText);
//            Chapter result;


//            templateAiFacade.extractContext(text);

//            if(convertMode.equalsIgnoreCase(MAX_MODE)) {
//                result = toMax(text);
//            } else if (convertMode.equalsIgnoreCase(NORMAL_MODE)) {
//                result = toNormal(text);
//            } else {
//                result = toMin(text);
//            }

//            chapterContext = toMin(chapterContext);

            var result = chapterContext.getCurrentItem();

            result.setText(chapterContext.text());
            result.setTitle(source.getTitle());
            result.setSourceKey(sourceKey);

            cacheHelper.setValue(sourceKey, result);

            return chapterContext.copy(Function.identity());
        }).result();
    }

    private FlowContext toMin(FlowContext flowContext) {
        return DurationMeasureUtil.measure("minInfo", () -> {
//            String summaryAlternative = templateAiFacade.retell(text);


//            var localContext = templateAiFacade.summary(flowContext)
//                    .then(templateAiFacade::glossary)
//                    .then(templateAiFacade::proofread);

//            localContext = templateAiFacade.glossary(localContext);
//            FlowContext proofread = templateAiFacade.proofread(localContext.copy(b -> b.argument(glossary(glossary))));

//            return localContext.copy(b -> b
//                    .chapterInfo(Chapter.builder()
//                            .summary(localContext.context())
////                    .selfConsistency(templateAiFacade.selfConsistency(flowContext))
//                            .names(localAppFlowActions.glossary(context))
//                            .proofreadText(localContext.proofread())
//                            .build()));

            return (FlowContext) null;
        }).result();
    }

//    private Chapter toNormal(String text) {
//        return DurationUtil.measure("normalInfo", () -> toMin(text).toBuilder()
////                .keywords(templateAiFacade.keywords(text))
////                .quotes(templateAiFacade.quotes(text))
////                .names(templateAiFacade.glossary(text, context))
////                .scenes(templateAiFacade.scenes(text))
//                .build()).result();
//    }
//
//    private Chapter toMax(String text) {
//        return DurationUtil.measure("maxInfo", () -> toNormal(text).toBuilder()
////                .themes(templateAiFacade.themes(text))
////                .perspective(templateAiFacade.perspective(text))
////                .tone(templateAiFacade.tone(text))
////                .foreshadowing(templateAiFacade.foreshadowing(text))
//                .build()).result();
//    }

}
