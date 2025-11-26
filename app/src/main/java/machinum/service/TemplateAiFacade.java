package machinum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Constants;
import machinum.exception.AppIllegalStateException;
import machinum.extract.*;
import machinum.flow.AppFlowActions;
import machinum.flow.model.Chunks;
import machinum.flow.model.FlowContext;
import machinum.flow.model.helper.FlowContextActions;
import machinum.model.Chapter;
import machinum.model.EmbeddingExecutionType;
import machinum.util.TextUtil;
import org.springframework.stereotype.Service;

import static machinum.config.Constants.TITLE;
import static machinum.config.Constants.TRANSLATED_TITLE;
import static machinum.flow.model.helper.FlowContextActions.*;
import static machinum.util.TextUtil.isNotEmpty;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateAiFacade {

    private final Summarizer summarizer;
    private final CoT coT;
    private final Glossary glossary;
    private final RewriterXml rewriter;
    private final ProofreaderEnXml proofreaderEn;
    private final Translater translater;
    private final Splitter splitter;
    private final SSMLConverter ssmlConverter;
    private final Synthesizer synthesizer;
    private final ExternalTitleTranslater externalTitleTranslater;
    private final EmbeddingService embeddingService;


    public FlowContext<Chapter> rewrite(FlowContext<Chapter> context) {
        return rewriter.rewrite(context);
    }

    public FlowContext<Chapter> summary(FlowContext<Chapter> context) {
        return summarizer.summarize(context);
    }

    public FlowContext<Chapter> selfConsistency(FlowContext<Chapter> context) {
        return coT.createCoT(context);
    }

    public FlowContext<Chapter> glossary(FlowContext<Chapter> context) {
        return glossary.extractGlossary(context);
    }

    public FlowContext<Chapter> proofread(FlowContext<Chapter> context) {
        return proofreaderEn.proofread(context);
    }

    public FlowContext<Chapter> proofreadRu(FlowContext<Chapter> context) {
        return translater.proofread(context);
    }

    public FlowContext<Chapter> glossaryTranslate(FlowContext<Chapter> context) {
        return glossary.glossaryTranslateWithCache(context);
    }

    public FlowContext<Chapter> glossaryTranslateExternal(FlowContext<Chapter> context) {
        return glossary.glossaryTranslateWithCacheExternal(context);
    }

    public FlowContext<Chapter> translate(FlowContext<Chapter> context) {
        return translater.translate(context);
    }

    public FlowContext<Chapter> translateAll(FlowContext<Chapter> context) {
        return translater.translateAll(context);
    }

    public FlowContext<Chapter> translateInChunks(FlowContext<Chapter> context) {
        return translater.translateInChunks(context);
    }

    public FlowContext<Chapter> scoreAndTranslate(FlowContext<Chapter> context) {
        return translater.scoreAndTranslate(context);
    }

    public FlowContext<Chapter> scoreAndTranslateInChunks(FlowContext<Chapter> context) {
        return translater.scoreAndTranslateInChunks(context);
    }

    public FlowContext<Chapter> batchTranslateTitle(FlowContext<Chapter> context) {
        return translater.batchTranslateTitle(context);
    }

    public FlowContext<Chapter> batchTranslateTitleExternal(FlowContext<Chapter> context) {
        return externalTitleTranslater.batchTranslate(context);
    }

    public FlowContext<Chapter> translateTitle(FlowContext<Chapter> context) {
        return translater.translateTitle(context);
    }

    public FlowContext<Chapter> editGrammar(FlowContext<Chapter> context) {
        return translater.fixGrammar(context);
    }

    public FlowContext<Chapter> editWithGlossary(FlowContext<Chapter> context) {
        return glossary.extractGlossaryFast(context)
                .then(translater::fixGrammar);
    }

    public FlowContext<Chapter> editGrammarInChunks(FlowContext<Chapter> context) {
        return translater.fixGrammarInChunks(context);
    }

    public FlowContext<Chapter> scoreAndEditGrammar(FlowContext<Chapter> context) {
        return translater.scoreAndFix(context);
    }

    public FlowContext<Chapter> scoreAndEditGrammarInChunks(FlowContext<Chapter> context) {
        return translater.scoreAndFixInChunks(context);
    }

    public FlowContext<Chapter> logicSplit(FlowContext<Chapter> context) {
        return splitter.split(context);
    }

    public FlowContext<Chapter> convertToSSML(FlowContext<Chapter> context) {
        return ssmlConverter.convert(context);
    }

    public FlowContext<Chapter> synthesize(FlowContext<Chapter> context) {
        return synthesizer.synthesize(context);
    }

    public FlowContext<Chapter> embedding(FlowContext<Chapter> context) {
        return embeddingService.processChapterEmbeddings(context, EmbeddingExecutionType.ONLY_NAMES);
    }

    public FlowContext<Chapter> consolidateGlossary(FlowContext<Chapter> flowContext) {
        return embeddingService.consolidateGlossary(flowContext);
    }

    public FlowContext<Chapter> bootstrapWith(FlowContext<Chapter> context) {
        var currentItem = context.getCurrentItem();
        log.debug("Bootstrap context from: {}", currentItem);

        var textValue = resolveTextValue(context, currentItem);
        var contextValue = currentItem.getSummary();
        var consolidatedContextValue = resolveConsolidatedSummary(context);
        var glossaryValue = currentItem.getNames();
        var title = currentItem.getTitle();
        var translatedTitle = currentItem.getTranslatedTitle();
        var translatedText = resolveTranslatedText(currentItem);
        var cleanChunks = currentItem.getCleanChunks();
        var translatedChunks = resolveTranslatedChunks(currentItem);

        return context.push(FlowContext::chapterNumberArg, chapterNumber(currentItem.getNumber()))
                .push(FlowContext::textArg, text(textValue))
                .push(FlowContext::chapContextArg, context(contextValue))
                .push(FlowContext::consolidatedChapContextArg, consolidatedContext(consolidatedContextValue))
                .push(AppFlowActions::glossaryArg, AppFlowActions.glossary(glossaryValue))
                .push(ctx -> ctx.arg(TITLE), FlowContextActions.createArg(TITLE, title))
                .push(ctx -> ctx.arg(TRANSLATED_TITLE), FlowContextActions.createArg(TRANSLATED_TITLE, translatedTitle))
                .push(FlowContext::translatedTextArg, FlowContextActions.translatedText(translatedText))
                .push(FlowContext::chunksArg, FlowContextActions.chunks(cleanChunks))
                .push(FlowContext::translatedChunksArg, FlowContextActions.translatedChunks(translatedChunks))
                .withoutEmpty();
    }

    /* ============= */


    private String resolveTextValue(FlowContext<Chapter> context, Chapter currentItem) {
        var proofreadText = currentItem.getProofreadText();
        var cleanText = currentItem.getText();

        if (isNotEmpty(proofreadText)) {
            return proofreadText;
        } else if (isNotEmpty(cleanText)) {
            return cleanText;
        } else {
            //TODO replce with feature method, with javadoc
            if (Boolean.TRUE.equals(context.metadata(Constants.ALLOW_EMPTY_TEXT))) {
                return "";
            } else {
                throw new AppIllegalStateException("Unknown state for text field: %s", currentItem);
            }
        }
    }

    private Chunks resolveTranslatedChunks(Chapter currentItem) {
//        var fixedTranslatedChunks = currentItem.getFixedTranslatedChunks();
        var translatedChunks = currentItem.getTranslatedChunks();

//        if(!Chunks.isEmpty(fixedTranslatedChunks)) {
//            return fixedTranslatedChunks;
//        }

        return translatedChunks;
    }

    private String resolveTranslatedText(Chapter currentItem) {
//        var fixedTranslatedText = currentItem.getFixedTranslatedText();
        var translatedText = currentItem.getTranslatedText();

//        if(TextUtil.isNotEmpty(fixedTranslatedText)) {
//            return fixedTranslatedText;
//        }

        return translatedText;
    }

    private String resolveConsolidatedSummary(FlowContext<Chapter> context) {
        var currentItem = context.getCurrentItem();
        var consolidatedSummary = currentItem.getConsolidatedSummary();

        if (TextUtil.isNotEmpty(consolidatedSummary)) {
            return consolidatedSummary;
        }

        try {
            var oldContext = context.oldChapContext();
            if (TextUtil.isNotEmpty(oldContext)) {
                return oldContext;
            }
        } catch (Exception skip) {
            //ignore
        }

        try {
            var previousItem = context.getPreviousItem();
            var previousSummary = previousItem.getSummary();

            if (TextUtil.isNotEmpty(previousSummary)) {
                return previousSummary;
            }
        } catch (Exception skip) {
            //ignore
        }

        return "";
    }

}
