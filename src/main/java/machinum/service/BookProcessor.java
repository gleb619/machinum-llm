package machinum.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.controller.BookOperationController.BookOperationRequest;
import machinum.converter.ChapterConverter;
import machinum.exception.AppIllegalStateException;
import machinum.flow.*;
import machinum.flow.OneStepRunner.Aggregation;
import machinum.model.Chapter;
import machinum.util.DurationUtil;
import machinum.util.TextUtil;
import machinum.util.TraceUtil;
import org.springframework.async.AsyncHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import static machinum.config.Constants.*;
import static machinum.controller.BookOperationController.BookOperationRequest.RuleConfig.RuleType.ALL;
import static machinum.controller.BookOperationController.BookOperationRequest.RuleConfig.RuleType.RANGE;
import static machinum.flow.Flow.FlowPredicateResult.accept;
import static machinum.flow.Flow.FlowPredicateResult.reject;
import static machinum.flow.OneStepRunner.Window.tumbling;
import static machinum.flow.Pack.anArgument;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookProcessor {

    private final BookFacade bookFacade;
    private final FlowFactory flowFactory;
    private final AsyncHelper asyncHelper;

    public CompletableFuture<Void> start(BookOperationRequest request) {
        return asyncHelper.runAsync(() -> {
            try {
                doStart(request);
            } catch (Exception e) {
                log.error("ERROR: ", e);
            }
        });
    }

    public void doStart(BookOperationRequest request) {
        log.debug("Prepare to process book with ai: {}", request);

        var book = bookFacade.get(request.getId());

        if (!List.of(ALL, RANGE).contains(request.getConfig().getRuleType())) {
            throw new AppIllegalStateException("RuleType[%s] is not supported", request.getConfig().getRuleType());
        }

        var chapters = new ArrayList<>(book.getChapters());
        var bookId = book.getId();
        var bookState = book.getBookState().state();

        var flow = enrichMetadata(flowFactory.createFlow(request.getOperationName(), bookId, chapters), request);
        flowFactory.createRunner(request, flow)
                .run(bookState);
    }

    private Flow<Chapter> enrichMetadata(Flow<Chapter> flow, BookOperationRequest request) {
        return flow.metadata(Map.of(
                ALLOW_OVERRIDE_MODE, request.isAllowOverride(),
                IGNORE_CACHE_MODE, request.isIgnoreCache(),
                AVAILABLE_STATES, request.availableStates(),
                BOOK_OPERATION_REQUEST, request
        ));
    }

    public enum ProcessorState implements Flow.State {

        CLEANING,
        SUMMARY,
        GLOSSARY,
        PROOFREAD,
        TRANSLATE_GLOSSARY,
        TRANSLATE_TITLE,
        TRANSLATE,
        COPYEDIT,
        SYNTHESIZE,
        FINISHED,
        ;

        public static ProcessorState defaultState() {
            return ProcessorState.CLEANING;
        }

        public static String defaultStateName() {
            return ProcessorState.CLEANING.name();
        }

        public static ProcessorState parse(String value) {
            try {
                return valueOf(value.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                return CLEANING;
            }
        }

    }

    @Configuration
    public static class ProcessorConfig {

        public BiFunction<String, List<Chapter>, Flow<Chapter>> baseFlow(ChapterConverter chapterConverter,
                                                                         ChapterFacade chapterFacade,
                                                                         BookFlowManager bookFlowManager,
                                                                         @Value("${app.run-id}") String runId) {
            //@formatter:off
            return (bookId, chapters) -> Flow.from(chapters)
                    .metadata(BOOK_ID, bookId)
//                    .map(chapterConverter::convert)
                    //.map(chapterConverter::restore)
                    .withStateManager(bookFlowManager)
                    .bootstrap(chapterFacade::bootstrap)
                    .refresh(chapterFacade::refresh)
                    .extend(chapterFacade::extend)
                    .beforeAll(ctx -> log.info("┌── Started book state[{}] processing: {}", ctx.getState(), runId))
                    .aroundAll((ctx, action) -> DurationUtil.measure("bookFlow-%s".formatted(ctx.getState()), action))
                    .aroundEachState((ctx, action) -> DurationUtil.measure("stateFlow-%s-%s".formatted(ctx.iteration(), ctx.getState()), () -> {
                        Map<ProcessorState, Boolean> availableStates = ctx.metadata(AVAILABLE_STATES);

                        if(Objects.nonNull(availableStates) && Boolean.FALSE.equals(availableStates.get(ctx.getState()))) {
                            log.info("|==✕ Execution of state is forbidden by settings : {}) {}", ctx.iteration(), ctx.getCurrentItem());
                        } else {
                            log.info("|== Current item is : {}) {}", ctx.iteration(), ctx.getCurrentItem());
                            TraceUtil.trace("pipeAction", action);
                        }
                    }))
                    .eachCondition(ctx -> {
                        Boolean allowOverrideMode = ctx.metadata(ALLOW_OVERRIDE_MODE, Boolean.FALSE);
                        if(allowOverrideMode && !chapterFacade.checkExecutionIsAllowed(ctx)) {
                            log.info("|--✕ Data already exists, skip execution of pipe №%s".formatted(ctx.getCurrentPipeIndex()));
                            return reject(ctx.preventSink());
                        }

                        return accept(ctx);
                    })
                    .aroundEach((ctx, action) -> DurationUtil.measure("pipeFlow-%s-%s-%s".formatted(ctx.getCurrentPipeIndex(), ctx.iteration(), ctx.getState()), () -> {
                        log.info("|-- Working with pipe №%s".formatted(ctx.getCurrentPipeIndex()));

                        var currentNumber = ctx.getCurrentItem().getNumber();
                        var bookOperationRequest = (BookOperationRequest) ctx.getFlow().getMetadata().get(BOOK_OPERATION_REQUEST);
                        if(bookOperationRequest.getConfig().getRuleType().equals(RANGE)) {
                            var range = bookOperationRequest.getConfig().getRange();
                            if(!range.supports(currentNumber)) {
                                log.error("Action is forbidden for {} chapter, due to range rule", currentNumber);
                                return ctx.preventSink();
                            }
                        }

//                        if(ctx.getCurrentItem().getNumber() >= 801) {
//                            throw new IllegalArgumentException("Stop");
//                        }
//                        if(ctx.getCurrentItem().getNumber() >= 338) {
//                            throw new IllegalArgumentException("Stop");
//                        }
//
//                        if(ctx.getCurrentPipeIndex() == 0 || ctx.getCurrentItem().getNumber() == 10) {
//                            var result = action.apply(ctx);
//
//                            if(ctx.getCurrentPipeIndex() == 0) {
//                                return result.preventChanges();
//                            } else {
//                                return result;
//                            }
//                        } else {
//                            log.error("Action is forbidden for {} chapter", ctx.chapterNumber());
//                            return ctx.preventSink();
//                        }

                        return action.apply(ctx);
//                        var result = action.apply(ctx);
//                        if(ctx.getCurrentPipeIndex() == 0) {
//                            return result.preventChanges();
//                        } else {
//                            return result;
//                        }
                    }).result())
                    .exception((ctx, e) -> {
                        log.error("|--! Got exception(%s iteration): {}|{}".formatted(ctx.iteration()), e.getClass(), e.getMessage());

                        chapterFacade.handleChapterException(ctx, e);
                    })
                    .afterAll(ctx -> log.info("└── Finished book state[{}] processing: {}\n\n\n\n", ctx.getState(), runId))
                    .sink(chapterFacade::saveWithContext);
            //@formatter:on
        }

        @Bean
        public BiFunction<String, List<Chapter>, Flow<Chapter>> simpleFlow(ChapterConverter chapterConverter,
                                                                           TemplateAiFacade templateAiFacade,
                                                                           ChapterFacade chapterFacade,
                                                                           BookFlowManager bookFlowManager,
                                                                           @Value("${app.run-id}") String runId,
                                                                           @Value("${app.flow.cooldown}") Duration cooldown) {
            //@formatter:off
            return baseFlow(chapterConverter, chapterFacade, bookFlowManager, runId)
                    .andThen(flow -> flow.copy(Function.identity()))
                    .andThen(flow -> flow
                        .metadata(FLOW_TYPE, "simple")
//                        .aroundEach((ctx, action) -> {
////                            if(ctx.getState().equals(ProcessorState.COPYEDIT)) {
////                                if(TextUtil.isEmpty(ctx.getCurrentItem().getTranslatedText()) &&
////                                   TextUtil.isEmpty(ctx.getCurrentItem().getFixedTranslatedText())) {
////
////                                   log.error("Action is forbidden for {} chapter", ctx.chapterNumber());
////                                   return ctx.preventSink();
////                                }
////                            }
//
//                            if(ctx.getState().equals(ProcessorState.CLEANING)) {
//                                return ctx.preventSink();
//                            }
//
//                            return action.apply(ctx);
//                        })
                        .onState(ProcessorState.CLEANING)
                            .comment("On %s state we use gemma2"::formatted)
//                            .nothing()
                            .pipeStateless(templateAiFacade::bootstrapWith)
                            .pipe(templateAiFacade::rewrite)
                        .onState(ProcessorState.SUMMARY)
                            .comment("On %s state we use qwen"::formatted)
                            .pipeStateless(templateAiFacade::bootstrapWith)
                            .pipe(templateAiFacade::summary)
                        .onState(ProcessorState.GLOSSARY)
                            .pipeStateless(templateAiFacade::bootstrapWith)
                            .pipe(templateAiFacade::glossary)
                        .onState(ProcessorState.COPYEDIT)
                            .comment("On %s state we use saiga"::formatted)
                            .pipeStateless(templateAiFacade::bootstrapWith)
                            .pipe(templateAiFacade::editWithGlossary)
//                        .onState(ProcessorState.FINISHED)
//                            .comment("Cooldown on last state[%s]"::formatted)
//                            .nothing()
//                            .waitFor(cooldown)
                        .build()
                    );
            //@formatter:on
        }

        @Bean
        public BiFunction<String, List<Chapter>, Flow<Chapter>> complexFlow(ChapterConverter chapterConverter,
                                                                            TemplateAiFacade templateAiFacade,
                                                                            ChapterFacade chapterFacade,
                                                                            BookFlowManager bookFlowManager,
                                                                            @Value("${app.run-id}") String runId,
                                                                            @Value("${app.flow.cooldown}") Duration cooldown,
                                                                            @Value("${app.flow.batch-size}") int batchSize) {
            //@formatter:off
            return baseFlow(chapterConverter, chapterFacade, bookFlowManager, runId )
                    .andThen(flow -> flow.copy(Function.identity()))
                    .andThen(flow -> flow
                        .metadata(FLOW_TYPE, "complex")
                        .onState(ProcessorState.CLEANING)
                            .comment("On %s state we use gemma2"::formatted)
                            .pipeStateless(templateAiFacade::bootstrapWith)
                            .pipe(templateAiFacade::rewrite)
                        //TODO move SUMMARY to first position
                        .onState(ProcessorState.SUMMARY)
                            .comment("On %s state we use qwen"::formatted)
                            .pipeStateless(templateAiFacade::bootstrapWith)
                            .pipe(templateAiFacade::summary)
                        .onState(ProcessorState.GLOSSARY)
                            .pipeStateless(templateAiFacade::bootstrapWith)
                            .pipe(templateAiFacade::glossary)
                            .pipe(templateAiFacade::logicSplit)
                        .onState(ProcessorState.PROOFREAD)
                            .comment("On %s state we use gemma2"::formatted)
                            .pipeStateless(templateAiFacade::bootstrapWith)
                            .pipe(templateAiFacade::proofread)
                        .onState(ProcessorState.TRANSLATE_GLOSSARY)
                            .comment("On %s state we use t-pro"::formatted)
                            .pipeStateless(templateAiFacade::bootstrapWith)
                            .pipe(templateAiFacade::glossaryTranslateExternal)
                        .onState(ProcessorState.TRANSLATE_TITLE)
                            .comment("On %s state we use t-pro"::formatted)
                            .pipeStateless(templateAiFacade::bootstrapWith)
//                            .pipe(templateAiFacade::translateTitle)
                            .window(tumbling(batchSize), Aggregation.<Chapter, String>pack(anArgument(ctx -> ctx.arg(TITLE)))
                                .onResult(templateAiFacade::batchTranslateTitleExternal))
                        .onState(ProcessorState.TRANSLATE)
                            .comment("On %s state we use t-pro"::formatted)
                            .pipeStateless(templateAiFacade::bootstrapWith)
                            .pipe(templateAiFacade::translateAll)
//                        .onState(ProcessorState.COPYEDIT)
//                            .comment("On %s state we use saiga"::formatted)
//                            .pipeStateless(templateAiFacade::bootstrapWith)
//                            .pipe(templateAiFacade::editGrammarInChunks)
                        .onState(ProcessorState.SYNTHESIZE)
                            .comment("On %s state we use tts"::formatted)
                            .pipeStateless(templateAiFacade::bootstrapWith)
                            .pipe(templateAiFacade::synthesize)
                        .onState(ProcessorState.FINISHED)
                            .comment("Cooldown on last state[%s]"::formatted)
                            .nothing()
//                            .waitFor(cooldown)
                        .build()
                    );
            //@formatter:on
        }

        @Bean
        public FlowFactory flowFactory(@Qualifier("simpleFlow") BiFunction<String, List<Chapter>, Flow<Chapter>> simpleFlow,
                                       @Qualifier("complexFlow") BiFunction<String, List<Chapter>, Flow<Chapter>> complexFlow,
                                       @Value("${app.flow.batch-size}") int batchSize) {
            return new FlowFactory(simpleFlow, complexFlow, batchSize);
        }

    }

    @RequiredArgsConstructor
    public static class FlowFactory {

        private final BiFunction<String, List<Chapter>, Flow<Chapter>> simpleFlow;
        private final BiFunction<String, List<Chapter>, Flow<Chapter>> complexFlow;
        private final int batchSize;

        public Flow<Chapter> createFlow(String discriminator, String bookId, List<Chapter> chapters) {
            return switch (discriminator) {
                case Operations.SIMPLE_FLOW -> simpleFlow.apply(bookId, chapters);
                case Operations.COMPLEX_FLOW -> complexFlow.apply(bookId, chapters);
                default -> throw new AppIllegalStateException("Unknown flow: " + discriminator);
            };
        }

        public FlowRunner<Chapter> createRunner(BookOperationRequest request, Flow<Chapter> flow) {
            var runner = new OneStepRunner<>(flow);
            var recursiveRunner = new RecursiveFlowRunner<>(runner);
            var batchRunner = new BatchFlowRunner<>(recursiveRunner, batchSize);

            if (TextUtil.isNotEmpty(request.getRunner())) {
                return switch (request.getRunner()) {
                    case "OneStepRunner" -> runner;
                    case "RecursiveFlowRunner" -> recursiveRunner;
                    case "BatchFlowRunner" -> batchRunner;
                    default -> throw new AppIllegalStateException("Unknown type of runner: " + request.getRunner());
                };
            }

            return switch (request.getOperationName()) {
                case Operations.SIMPLE_FLOW -> runner;
                case Operations.COMPLEX_FLOW -> batchRunner;
                default -> throw new AppIllegalStateException("Unknown flow: " + request.getOperationName());
            };
        }

    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Operations {

        public static final String SIMPLE_FLOW = "simpleFlow";

        public static final String COMPLEX_FLOW = "complexFlow";

    }

}
