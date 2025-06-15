package machinum.processor.core;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.config.Constants;
import machinum.exception.AppIllegalStateException;
import machinum.flow.FlowContext;
import machinum.processor.core.AssistantContext.OutputType;
import machinum.processor.exception.NoDataException;
import machinum.util.CodeBlockExtractor;
import machinum.util.DurationUtil;
import machinum.util.PropertiesParser;
import machinum.util.TraceUtil;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.cache.CacheHelper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static machinum.config.Constants.CHAPTER;
import static machinum.processor.core.HashSupport.hashStringWithCRC32;
import static machinum.processor.core.PromptConstants.NO_DATA_KEYWORD;
import static machinum.service.plugin.StatisticPlugin.withStatistics;
import static machinum.util.DurationUtil.ArgumentPlugin.forArgument;
import static machinum.util.JavaUtil.calculatePercent;
import static machinum.util.TextUtil.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class Assistant {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{(\\w+)}");

    private final AssistantClient assistantClient;

    private final CacheHelper cacheHelper;

    private final AssistantConverter assistantConverter;


    public AssistantContext.Result process(AssistantContext assistantContext) {
        return TraceUtil.trace("assistant", () -> doProcess(assistantContext));
    }

    private AssistantContext.Result doProcess(AssistantContext assistantContext) {
        var localHistory = new ArrayList<>(assistantContext.getHistory());
        var chunkWordCount = countWords(assistantContext.getText());
        var historyWordCount = countHistoryWords(localHistory);
        var values = new HashMap<>(assistantContext.getInputs());
        var resultContext = AssistantContext.Result.createNew(b -> b.history(localHistory)
                .context(assistantContext)
                .resultHistory(assistantContext.getResult().getResultHistory())
        );
        assistantContext.setResult(resultContext);
        var name = assistantContext.getOperation() + hashStringWithCRC32(assistantContext.getText());
        var options = assistantClient.parseOptions(assistantContext);
        var chapterNumber = assistantContext.getFlowContext().optionalValue(FlowContext::chapterNumberArg).orElse(-1);
        var ignoreCacheMode = Boolean.TRUE.equals(assistantContext.getFlowContext().metadata(Constants.IGNORE_CACHE_MODE));

        var duration = DurationUtil.configure(
                forArgument(assistantContext),
                forArgument(CHAPTER, chapterNumber),
                withStatistics());

        var report = duration.measure(name, () -> {
            values.put("text", assistantContext.getText());

            var message = fillVars(values, assistantContext.getActionResource(), new PromptTemplate(assistantContext.getActionResource()))
                    .createMessage();

            localHistory.add(message);

            var totalTokens = countHistoryTokens(localHistory);
            var totalWords = countHistoryWords(localHistory);
            var rayId = TraceUtil.getCurrentRayId();

            log.debug("""
                            |--> Working with {}0: rayId={},\s
                              model={}, contextLength={},\s
                              inputText={}...; [{} tokens|{} words]\s
                              inputTotalTokens={}, inputTotalWords={},\s
                              inputTokensLeft={}, allocated={}%,\s
                              inputHistorySize={}, inputHistoryWords={}, inputHistoryTokens={},
                              inputHistory=
                            {}
                            """,
                    assistantContext.getOperation(), rayId,
                    options.getModel(), options.getNumCtx(),
                    toShortDescription(assistantContext.getText()), -1, chunkWordCount,
                    totalTokens, totalWords,
                    options.getNumCtx() - totalTokens, calculatePercent(totalTokens, options.getNumCtx()),
                    localHistory.size(), historyWordCount, -1,
                    indent(toShortHistoryDescription(localHistory)));

            var prompt = new Prompt(localHistory, options);
            try {
//                var spec = createRequest(assistantContext, prompt, glossaryTools, assistantContext.getTools());
//                work(prompt, Objects.requireNonNull(spec, "Ai can't return null"), forceMode);
                var response = work(assistantContext, prompt, ignoreCacheMode);
                var content = parseContent(response.getText());

                if (Objects.nonNull(assistantContext.getOutputClass())) {
                    resultContext.setEntity(parseEntity(content, assistantContext.getOutputType(), assistantContext.getOutputClass(), assistantContext.getMapper()));
                }

                localHistory.add(response.getMessage());
                resultContext.replaceResult(content);
                resultContext.setOllamaOptions(options);

                var contentWords = countWords(content);
                var contentTokens = countTokens(content);

                log.debug("""
                                |<-- The response is ready for {}: rayId={},\s
                                  outputText={}...; [{} tokens|{} words]\s
                                  outputTotalWords={}, outputTotalTokens={},\s
                                  outputTokensLeft={}, allocated={}%""",
                        assistantContext.getOperation(), rayId,
                        toShortDescription(content), contentTokens, contentWords,
                        totalWords + contentWords, totalTokens + contentTokens,
                        options.getNumCtx() - (totalTokens + contentTokens),
                        calculatePercent(totalTokens + contentTokens, options.getNumCtx()));

                return resultContext;
            } catch (Exception e) {
                var cacheKey = hashStringWithCRC32(prompt.getContents());
                cacheHelper.evictValue(cacheKey);
                ExceptionUtils.rethrow(e);
                return null;
            }
        });

        var result = DurationUtil.calculateTimePerWord(report.duration(), chunkWordCount);

        log.info("Time per word: chunk={}, text={}..., result={}..., {}sec", name, toShortDescription(assistantContext.getText()), toShortDescription(resultContext.result()), result);

        return resultContext;
    }

    /* ============= */

    private AssistantClient.Result work(AssistantContext assistantContext, Prompt prompt, Boolean ignoreCacheMode) {
        var key = hashStringWithCRC32(prompt.getContents());

        if (ignoreCacheMode) {
            log.debug("Clear cache, to execute again for: {}", key);
            cacheHelper.evictValue(key);
        } else {
            log.debug("Check operation result in cache for: {}", key);
        }

        return cacheHelper.getOrCreate(key, () -> assistantClient.call(assistantContext, prompt));
    }

    private PromptTemplate fillVars(Map<String, String> inputs, Resource template, PromptTemplate promptTemplate) {
        Set<String> requiredVars = extractVariables(template);

        for (String attribute : requiredVars) {
            log.trace("Found var in template: {}", attribute);

            if (inputs.containsKey(attribute)) {
                String value = inputs.get(attribute);
                log.debug("Filling in the template: {} => '{}...'", attribute, toShortDescription(value));
                promptTemplate.add(attribute, value);
            }
        }

        return promptTemplate;
    }

    @SneakyThrows
    private Set<String> extractVariables(Resource template) {
        var templateContent = StreamUtils.copyToString(template.getInputStream(), StandardCharsets.UTF_8);
        Set<String> variables = new HashSet<>();
        Matcher matcher = VAR_PATTERN.matcher(templateContent);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }

    private String parseContent(String content) {
        if (content.startsWith("<think>")) {
            content = content.substring(content.indexOf("</think>") + 8).trim();
        }

        content = extractCodeFromMarkdown(content);

        if (content.contains(NO_DATA_KEYWORD)) {
            throw NoDataException.noData();
        }

        return content;
    }

    private String extractCodeFromMarkdown(String content) {
        if (content.startsWith("```") && content.endsWith("```")) {
            // Remove the first line if it contains "```json"
            String[] lines = content.split("\n", 2);
            if (lines[0].trim().equalsIgnoreCase("```json")) {
                content = lines.length > 1 ? lines[1] : "";
            } else if (lines[0].trim().equalsIgnoreCase("```xml")) {
                content = lines.length > 1 ? lines[1] : "";
            } else {
                content = content.substring(3); // Remove leading ```
            }

            // Remove trailing ```
            content = content.substring(0, content.length() - 3);

            // Trim again to remove any potential whitespace
            content = content.trim();
        }

        return content;
    }

    private Object parseEntity(String content, OutputType outputType, ParameterizedTypeReference<?> outputClass,
                               Function<String, Object> mapper) {
        try {
            return switch (outputType) {
                case JSON, STRING -> {
                    var beanOutputConverter =
                            new BeanOutputConverter<>(outputClass);

                    yield beanOutputConverter.convert(content);
                }
                case PROPERTIES -> {
                    var cleanText = CodeBlockExtractor.extractCode(content);
                    var properties = PropertiesParser.parseProperties(cleanText);
                    yield assistantConverter.convert(properties, outputClass);
                }
                default -> throw new AppIllegalStateException("Unexpected value: " + outputType);
            };
        } catch (Exception e) {
            return mapper.apply(content);
        }
    }

    /* ============= */

    public enum Type {

        CHAT,

        @Deprecated
        TRANSFORM

    }

}
