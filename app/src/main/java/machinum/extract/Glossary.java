package machinum.extract;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.extract.ExternalGlossaryTranslater.TranslationException;
import machinum.extract.util.ProperNameExtractor;
import machinum.flow.AppFlowActions;
import machinum.flow.model.FlowArgument;
import machinum.flow.model.FlowContext;
import machinum.flow.model.helper.FlowContextActions;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.service.ChapterGlossaryService;
import machinum.util.JavaUtil;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static machinum.config.Constants.BOOK_ID;
import static machinum.util.JavaUtil.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class Glossary {

    private final GlossaryExtractor glossaryExtractor;

    private final GlossaryJsonTranslate glossaryJsonTranslate;

    private final GlossaryTranslate glossaryTranslate;

    private final ChapterGlossaryService chapterGlossaryService;

    private final ProperNameExtractor properNameExtractor;

    private final ExternalGlossaryTranslater externalGlossaryTranslater;


    public FlowContext<Chapter> extractGlossaryFast(FlowContext<Chapter> flowContext) {
        var chapterNumber = resolveChapterNumber(flowContext);
        var references = properNameExtractor.extract(flowContext.text()).stream()
                .map(ObjectName::forName)
                .toList();
        var bookId = flowContext.getCurrentItem().getBookId();

        var currentGlossary = AppFlowActions.glossary(flowContext);
        var additionalGlossary = chapterGlossaryService.findReferences(chapterNumber, references, bookId, 1);
        var newGlossary = joinNames(currentGlossary, additionalGlossary);

        return flowContext.replace(AppFlowActions::glossaryArg, AppFlowActions.glossary(newGlossary));
    }

    public FlowContext<Chapter> extractGlossary(FlowContext<Chapter> flowContext) {
        var namesExtractedByNLP = properNameExtractor.extract(flowContext.text()).stream()
                .map(ObjectName::forName)
                .toList();

        // Combine glossary from previous + current + db references
        var glossaryFromLastChapter = flowContext.optionalValue(AppFlowActions::oldGlossaryArg)
                .orElse(Collections.emptyList());
        var fullGlossary = enrichCurrentGlossaryFromDB(flowContext, glossaryFromLastChapter, namesExtractedByNLP);
        var currentChapterGlossary = chapterGlossaryService.findGlossary(resolveChapterNumber(flowContext), namesExtractedByNLP, resolveBookId(flowContext));

        //Run again
        return glossaryExtractor.secondExtract(flowContext.rearrange(AppFlowActions::glossaryArg, AppFlowActions.glossary(fullGlossary))
                        .rearrange(AppFlowActions::oldGlossaryArg, AppFlowActions.glossary(glossaryFromLastChapter).asObsolete())
                        .rearrange(AppFlowActions::consolidatedGlossaryArg, AppFlowActions.consolidatedGlossary(fullGlossary))
                        .addArgs(AppFlowActions.glossary(currentChapterGlossary).asAlternative())
                )
                .then(ctx -> {
                    var chapterNames = new ArrayList<>(AppFlowActions.glossary(ctx));

                    for (ObjectName name : namesExtractedByNLP) {
                        var objectNameFromDB = findBy(fullGlossary, ObjectName::getName, name.getName());
                        var chapterName = findBy(chapterNames, ObjectName::getName, name.getName());

                        if (Objects.nonNull(objectNameFromDB) && Objects.isNull(chapterName)) {
                            chapterNames.add(objectNameFromDB);
                        }
                    }

                    return ctx.replace(AppFlowActions::glossaryArg, AppFlowActions.glossary(chapterNames));
                })
                .removeArgs(FlowContextActions.alt(AppFlowActions::glossaryArg));
    }

    public FlowContext<Chapter> glossaryTranslateWithCache(FlowContext<Chapter> flowContext) {
        var list = withCache(flowContext);

        return glossaryJsonTranslate.translateWithCache(flowContext.replace(AppFlowActions::glossaryArg, AppFlowActions.glossary(list)));
    }

    public FlowContext<Chapter> glossaryTranslate(FlowContext<Chapter> flowContext) {
        return glossaryTranslate.translate(flowContext);
    }

    public FlowContext<Chapter> glossaryTranslateWithCacheExternal(FlowContext<Chapter> flowContext) {
        var list = withCache(flowContext);
        try {
            return externalGlossaryTranslater.translate(flowContext.replace(AppFlowActions::glossaryArg, AppFlowActions.glossary(list)));
        } catch (TranslationException te) {
            return glossaryJsonTranslate.translateWithCache(flowContext.replace(AppFlowActions::glossaryArg, AppFlowActions.glossary(te.getObjectNames())));
        } catch (AppIllegalStateException aese) {
            return glossaryTranslateWithCache(flowContext);
        }
    }

    /* ============= */

    //TODO limit max length of glossary to 0.4 of current context window
    private List<ObjectName> enrichCurrentGlossaryFromDB(FlowContext<Chapter> ctx,
                                                         List<ObjectName> glossaryFromLastChapter,
                                                         List<ObjectName> additionalNames) {
        var chapterNumber = resolveChapterNumber(ctx);

        return ctx.findCurrentItem().map(chapter -> {
                    var bookId = resolveBookId(ctx);

                    // Load from db for the terms who just founded
                    var references = ctx.optionalValue(AppFlowActions::glossaryArg)
                            .map(glossary -> glossary.stream()
                                    .flatMap(objectName -> objectName.getReferences().stream()
                                            .map(ObjectName::forName)
                                            .collect(Collectors.toCollection(() -> mutableListOf(additionalNames, objectName))).stream())
                                    .collect(Collectors.toList())).orElse(additionalNames);
                    var namesWithReferences = chapterGlossaryService.findReferences(chapterNumber, references, bookId, 3);

                    // Put it together
                    return joinNames(glossaryFromLastChapter, namesWithReferences);
                })
                .orElseGet(() -> glossaryFromLastChapter);
    }

    private List<ObjectName> joinNames(List<ObjectName> glossaryFromLastChapter, List<ObjectName> namesWithReferences) {
        var list = new ArrayList<ObjectName>();
        list.addAll(glossaryFromLastChapter);
        list.addAll(namesWithReferences);

        return uniqueBy(list, ObjectName::getName).stream()
                .sorted(Comparator.comparing(ObjectName::getCategory)
                        .thenComparing(ObjectName::getName))
                .toList();
    }

    private String resolveBookId(FlowContext<Chapter> ctx) {
        var chapter = Objects.requireNonNull(ctx.getCurrentItem(), "Chapter can't be null");

        if (Objects.nonNull(chapter.getBookId())) {
            return chapter.getBookId();
        }

        return Objects.requireNonNull(ctx.metadata(BOOK_ID), "Book id can't be null");
    }

    private Integer resolveChapterNumber(FlowContext<Chapter> ctx) {
        return ctx.optional(FlowContext::chapterNumberArg)
                .map(FlowArgument::getValue)
                .map(JavaUtil::parseInt)
                .orElse(Integer.MAX_VALUE - 1);
    }

    private List<ObjectName> withCache(FlowContext<Chapter> flowContext) {
        var list = new ArrayList<ObjectName>();

        var names = AppFlowActions.glossary(flowContext);
        var translatedNames = chapterGlossaryService.findTranslations(flowContext.getCurrentItem().getBookId(), names);

        for (var name : names) {
            var translatedName = findBy(translatedNames, ObjectName::getName, name.getName());
            if (Objects.nonNull(translatedName)) {
                list.add(name.withRuName(translatedName.ruName()));
            } else {
                list.add(name);
            }
        }

        return list;
    }

}
