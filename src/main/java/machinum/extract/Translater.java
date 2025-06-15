package machinum.extract;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.FlowContext;
import machinum.flow.FlowContextActions;
import machinum.model.Chapter;
import machinum.model.Chunks;
import machinum.model.ScoringResult;
import machinum.processor.core.FlowSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static machinum.config.Constants.SCORE;
import static machinum.flow.FlowContextActions.translatedChunk;
import static machinum.flow.FlowContextActions.translatedChunks;

@Slf4j
@Component
@RequiredArgsConstructor
public class Translater implements FlowSupport {

    private final TranslaterBody translaterBody;
    private final TranslaterXmlBody xmlTranslaterBody;
    private final TranslaterPlainHeader plainTranslaterHeader;
    private final TranslaterPropertiesHeader propertiesTranslaterHeader;
    private final TranslationScoring translationScoring;
    private final GrammarEditorScoring grammarEditorScoring;
    private final GrammarEditor grammarEditor;
    private final ProofreaderRu proofreaderRu;

    @Value("${app.translate.score.iterations}")
    private final Integer numberOfIterations;

    @Value("${app.translate.score.quality}")
    private final Integer awaitedQuality;

    @Value("${app.translate.fail-on-wrong-chunks}")
    private final Boolean failOnWrongChunks;

    private static List<Chunks.BiChunkItem> prepareChunks(FlowContext<Chapter> context, Boolean failOnWrongChunks) {
        var originMap = context.chunks().asMap();
        var translatedMap = context.translatedChunks().asMap();

        if (originMap.size() != translatedMap.size()) {
            if (failOnWrongChunks) {
                throw new IllegalArgumentException("Broken format: %s <> %s, maps do not match".formatted(originMap.size(), translatedMap.size()));
            } else {
                log.warn("Broken format: {} <> {}, maps do not match", originMap.size(), translatedMap.size());
            }
        }

        return originMap.entrySet().stream()
                .map(entry -> entry.getValue().toTranslated(translatedMap.get(entry.getKey())))
                .toList();
    }

    public FlowContext<Chapter> translate(FlowContext<Chapter> context) {
        return translaterBody.translate(context);
    }

    public FlowContext<Chapter> translateAll(FlowContext<Chapter> context) {
        return xmlTranslaterBody.translate(context);
    }

    public FlowContext<Chapter> translateTitle(FlowContext<Chapter> context) {
        return plainTranslaterHeader.translate(context);
    }

    public FlowContext<Chapter> batchTranslateTitle(FlowContext<Chapter> context) {
        return propertiesTranslaterHeader.batchTranslate(context);
    }

    public FlowContext<Chapter> translateWithScoring(FlowContext<Chapter> context) {
        return translate(context)
                .then(translationScoring::scoreTranslate)
                .then(this::translate)
                .removeArgs(ctx -> ctx.arg(SCORE), ctx -> ctx.arg(SCORE).asObsolete());
    }

    public FlowContext<Chapter> scoreAndTranslate(FlowContext<Chapter> context) {
        return translationScoring.scoreTranslate(context)
                .then(this::translate)
                .removeArgs(ctx -> ctx.arg(SCORE), ctx -> ctx.arg(SCORE).asObsolete());
    }

    public FlowContext<Chapter> translateWithScoringLoop(FlowContext<Chapter> context) {
        return translateWithScoringLoop(numberOfIterations, awaitedQuality, context, this::translate, translationScoring::scoreTranslate);
    }

    public FlowContext<Chapter> fixGrammar(FlowContext<Chapter> context) {
        return grammarEditor.fixTranslate(context);
    }

    public FlowContext<Chapter> fixGrammarWithScoring(FlowContext<Chapter> context) {
        return fixGrammar(context)
                .then(grammarEditorScoring::scoreTranslate)
                .then(this::fixGrammar)
                .removeArgs(ctx -> ctx.arg(SCORE), ctx -> ctx.arg(SCORE).asObsolete());
    }

    public FlowContext<Chapter> fixGrammarWithScoringLoop(FlowContext<Chapter> context) {
        return translateWithScoringLoop(numberOfIterations, awaitedQuality, context, this::fixGrammar, grammarEditorScoring::scoreTranslate);
    }

    public FlowContext<Chapter> scoreAndFix(FlowContext<Chapter> context) {
        return grammarEditorScoring.scoreTranslate(context)
                .then(this::fixGrammar)
                .removeArgs(ctx -> ctx.arg(SCORE), ctx -> ctx.arg(SCORE).asObsolete());
    }

    /**
     * Executes a process to generate ScoringResult instances, evaluates their scores,
     * and returns the best result based on the given conditions.
     *
     * @return The best ScoringResult instance based on the score.
     */
    public FlowContext<Chapter> translateWithScoringLoop(int numberOfIterations, int awaitedQuality,
                                                         FlowContext<Chapter> flowContext,
                                                         Function<FlowContext<Chapter>, FlowContext<Chapter>> fn,
                                                         Function<FlowContext<Chapter>, FlowContext<Chapter>> scoreFn) {
        if (numberOfIterations < 1) {
            throw new IllegalArgumentException("Number of iterations can't be less that one");
        } else if (numberOfIterations > 1) {
            log.debug("Prepare to refine translation: {} times", numberOfIterations);
        }

        var tempResults = new ArrayList<TranslationWithScore>();

        //TODO redo with proceeding of operation
        for (int i = 0; i < numberOfIterations; i++) {
            FlowContext<Chapter> context;
            if (!tempResults.isEmpty()) {
                context = tempResults.getLast().context();
            } else {
                context = flowContext;
            }
            // Simulate getting an ScoringResult instance (replace this with your actual logic)
            var evaluation = executeScoreOperation(context, fn, scoreFn);

            // Add the result to the temporary list
            tempResults.add(evaluation);

            // Check if the score is 9 or higher
            if (evaluation.getScore() >= awaitedQuality) {
                log.debug("Found translation that fit to given score, breaking loop");
                return evaluation.context(); // Stop early and return the result
            } else {
                log.debug("This translation is not as good as expected: {} <> {} (curr <> await)", evaluation.getScore(), awaitedQuality);
            }

            // If the score is already ${awaitedQuality - 1} or higher, no need to continue
//            if (evaluation.getScore() >= (awaitedQuality - 1)) {
//                break;
//            }
        }

        // Compare all results in the temporary list and return the best one
        return tempResults.stream()
                .max(TranslationWithScore::compareTo) // Use natural ordering defined by compareTo
                .map(TranslationWithScore::context)
                .orElseGet(() -> {
                    if (!tempResults.isEmpty()) {
                        return tempResults.getLast().context();
                    } else {
                        return flowContext;
                    }
                }) // Return default if no results are found
                //TODO: Is it necessary to save the SCORE to the db?
                .removeArgs(ctx -> ctx.arg(SCORE), ctx -> ctx.arg(SCORE).asObsolete()); //Clean context from score args
    }

    public FlowContext<Chapter> translateInChunks(FlowContext<Chapter> context) {
        return doTranslateChunks(context, this::translateWithScoring);
    }

    public FlowContext<Chapter> scoreAndTranslateInChunks(FlowContext<Chapter> context) {
        return doTranslateChunks(context, this::scoreAndTranslate);
    }

    public FlowContext<Chapter> fixGrammarInChunks(FlowContext<Chapter> context) {
        return doGrammarFixChunks(context, this::fixGrammar);
    }

    public FlowContext<Chapter> scoreAndFixInChunks(FlowContext<Chapter> context) {
        return doTranslateChunks(context, this::scoreAndFix);
    }

    /* ============= */

    public FlowContext<Chapter> proofread(FlowContext<Chapter> context) {
        return doGrammarFixChunks(context, proofreaderRu::proofread);
    }

    private FlowContext<Chapter> doTranslateChunks(FlowContext<Chapter> context, Function<FlowContext<Chapter>, FlowContext<Chapter>> fn) {
        var counter = new AtomicInteger(1);
        var store = new AtomicReference<>(context);
        var chunks = context.chunks();

        log.debug("Prepare to translate in chunks: {}", chunks.size());

        var result = chunks.stream()
                .map(chunk -> store.get().replace(FlowContext::textArg, FlowContextActions.text(chunk.stringValue()))
                        .rearrange(FlowContext::chunkArg, FlowContextActions.chunk(chunk))
                        .rearrange(FlowContext::subIterationArg, FlowContextActions.subIteration(counter.getAndIncrement()))
                )
                .map(fn::apply)
                .map(ctx -> ctx.rearrange(FlowContext::translatedChunkArg, translatedChunk(ctx.chunkArg()
                        .flatMap(chunk -> chunk.withText(ctx.translatedText())))
                ))
                .peek(store::set)
                .map(FlowContext::translatedChunk)
                .reduce(Chunks.createNew(),
                        Chunks::accumulate,
                        Chunks::merge);

        return context.rearrange(FlowContext::translatedTextArg, FlowContextActions.translatedText(result.stringValue()))
                .rearrange(FlowContext::translatedChunksArg, translatedChunks(result))
                .removeArgs(FlowContext::subIterationArg, FlowContext::oldSubIterationArg);
    }

    private FlowContext<Chapter> doGrammarFixChunks(FlowContext<Chapter> context,
                                                    Function<FlowContext<Chapter>, FlowContext<Chapter>> fn) {
        var counter = new AtomicInteger(1);
        var store = new AtomicReference<>(context);
        var biChunks = prepareChunks(context, failOnWrongChunks);
        log.debug("Prepare to fix grammar in chunks: {}", biChunks.size());

        var result = biChunks.stream()
                .map(chunk -> store.get().replace(FlowContext::textArg, FlowContextActions.text(chunk.getOrigin().stringValue()))
                        .replace(FlowContext::translatedTextArg, FlowContextActions.translatedText(chunk.getTranslated().stringValue()))
                        .rearrange(FlowContext::chunkArg, FlowContextActions.chunk(chunk.getOrigin()))
                        .rearrange(FlowContext::translatedChunkArg, translatedChunk(chunk.getTranslated()))
                        .rearrange(FlowContext::subIterationArg, FlowContextActions.subIteration(counter.getAndIncrement()))
                )
                .map(fn::apply)
                .map(ctx -> ctx.rearrange(FlowContext::translatedChunkArg, ctx.translatedChunkArg()
                        .mapValue(chunk -> chunk.withText(ctx.translatedText()))
                ))
                .peek(store::set)
                .map(FlowContext::translatedChunk)
                .reduce(Chunks.createNew(),
                        Chunks::accumulate,
                        Chunks::merge);

        return context.rearrange(FlowContext::translatedTextArg, FlowContextActions.translatedText(result.stringValue()))
                .rearrange(FlowContext::translatedChunksArg, translatedChunks(result))
                .removeArgs(FlowContext::subIterationArg, FlowContext::oldSubIterationArg);
    }

    /**
     * Simulates an operation that generates an ScoringResult instance.
     * Replace this with your actual logic to generate ScoringResult.
     *
     * @return A new ScoringResult instance.
     */
    private TranslationWithScore executeScoreOperation(FlowContext<Chapter> context,
                                                       Function<FlowContext<Chapter>, FlowContext<Chapter>> fn,
                                                       Function<FlowContext<Chapter>, FlowContext<Chapter>> scoreFn) {
        context.optionalValue(FlowContext::subIterationArg)
                .ifPresent(iteration -> log.info("|-- Executing operation with scoring: {} iteration", iteration));
        return fn.apply(context)
                .then(scoreFn::apply)
                .map(ctx -> new TranslationWithScore(ctx, ctx.<ScoringResult>arg(SCORE).getValue()));
    }

    private record TranslationWithScore(FlowContext<Chapter> context,
                                        ScoringResult scoring) implements Comparable<TranslationWithScore> {

        public double getScore() {
            return scoring.score();
        }

        @Override
        public int compareTo(Translater.TranslationWithScore other) {
            return scoring().compareTo(other.scoring());
        }

    }

}
