package machinum.service.plugin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.model.Statistic;
import machinum.processor.core.AssistantContext;
import machinum.service.StatisticService;
import machinum.util.DurationUtil;
import machinum.util.DurationUtil.DurationContext;
import machinum.util.DurationUtil.DurationPlugin;
import machinum.util.TextUtil;
import machinum.util.TraceUtil;
import org.springframework.async.AsyncHelper;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static machinum.config.Constants.ARGUMENT;
import static machinum.config.Constants.CHAPTER;
import static machinum.util.JavaUtil.calculatePercent;
import static machinum.util.JavaUtil.round;
import static machinum.util.TextUtil.*;

@Slf4j
@RequiredArgsConstructor
public class StatisticPlugin implements DurationPlugin {

    private static final AtomicReference<StatisticPlugin> reference = new AtomicReference<>(StatisticPlugin.empty());
    private final StatisticService statisticService;
    private final AsyncHelper asyncHelper;

    private static StatisticPlugin empty() {
        return new StatisticPlugin(null, null);
    }

    public static StatisticPlugin withStatistics() {
        return reference.get();
    }

    public void init() {
        reference.set(this);
    }

    @Override
    public void onAction(DurationContext context) {
        if (Objects.isNull(statisticService)) {
            return;
        }

        asyncHelper.runAsync(() -> {
            try {
                doWork(context);
            } catch (Exception e) {
                log.error("ERROR: ", e);
            }
        });
    }

    private void doWork(DurationContext context) {
        var operationName = TextUtil.firstNotEmpty(context.operationName(), "unknown-0-0");
        var operationType = operationName.substring(0, Math.max(operationName.indexOf("-"), 1));
        var chapter = parseChapter(context.context().get(CHAPTER), operationName);
        var response = context.response();
        var arg = context.context().get(ARGUMENT);

        if (arg instanceof AssistantContext ctx) {
            var result = (AssistantContext.Result) response.result();
            var inputHistory = ctx.getHistory();
            var outputHistory = result.getHistory();
            var options = result.getOllamaOptions();
            var rayId = TraceUtil.getCurrentRayId();

            var inputHistoryTokens = countHistoryTokens(inputHistory);
            var inputHistoryWords = countHistoryWords(inputHistory);
            var inputTokens = countTokens(ctx.getText());
            var inputWords = countWords(ctx.getText());

            var outputTokens = countTokens(result.result());
            var outputWords = countWords(result.result());
            var outputHistoryTokens = countHistoryTokens(outputHistory);
            var outputHistoryWords = countHistoryWords(outputHistory);

            var percent = round(calculatePercent(outputHistoryTokens, options.getNumCtx()), 2);
            int left = options.getNumCtx() - outputHistoryTokens;
            var duration = response.duration();
            var report = DurationUtil.DurationConfig.humanReadableDuration(duration);

            var item = Statistic.StatisticItem.builder()
                    .mode(statisticService.getMode())
                    .runId(statisticService.getRunId())
                    .operationName(operationName)
                    .operationType(operationType)
                    .chapter(chapter)
                    .rayId(rayId)
                    .operationDate(LocalDateTime.now())
                    .operationTimeSeconds(duration.toSeconds())
                    .operationTimeString(report)
                    .inputHistoryTokens(inputHistoryTokens)
                    .inputHistoryWords(inputHistoryWords)
                    .inputTokens(inputTokens)
                    .inputWords(inputWords)
                    .outputTokens(outputTokens)
                    .outputWords(outputWords)
                    .outputHistoryTokens(outputHistoryTokens)
                    .outputHistoryWords(outputHistoryWords)
                    .conversionPercent(percent)
                    .tokens(options.getNumCtx())
                    .tokensLeft(left)
                    .aiOptions(options)
                    .build();

            var statistic = statisticService.currentStatistic();
            statisticService.update(statistic.addItem(item));
        }

    }

    private int parseChapter(Object o, String operationName) {
        if (o instanceof Integer i && i > -1) {
            return i;
        }

        var index = operationName.indexOf("-") + 1;
        var value = operationName.substring(index, operationName.indexOf("-", index));

        return Integer.parseInt(value);
    }

}
