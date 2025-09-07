package machinum.processor.core;

import lombok.*;
import machinum.flow.AppFlowActions;
import machinum.flow.argument.FlowArgument;
import machinum.flow.core.FlowContext;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.processor.HistoryService.TokenBudget;
import machinum.util.JavaUtil;
import machinum.util.TextUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static machinum.flow.action.FlowContextActions.alt;
import static machinum.util.JavaUtil.*;
import static machinum.util.TextUtil.countHistoryTokens;
import static machinum.util.TextUtil.countTokens;

public interface FlowSupport {

    String USER_PREVIOUS_CONTEXT_TEMPLATE = """
            Provide brief information about the last web novel's chapters
            """;

    String USER_CONTEXT_TEMPLATE = """
            Provide brief information about the previous web novel's chapter
            """;

    String USER_PREVIOUS_GLOSSARY_TEMPLATE = """
            Provide a glossary list for the last web novel's chapters
            """;

    String USER_GLOSSARY_TEMPLATE = """
            Provide a glossary list for previous novels' chapter
            """;

    static String compressContext(FlowArgument<?> arg, int targetLength, Resource systemTemplate, Integer contextLength) {
        String contextRaw = arg.stringValue();
        String context;

        var contextTokens = countTokens(contextRaw);
        var systemTokens = countTokens(systemTemplate);
        var freeTokens = contextLength - (targetLength + systemTokens);

        if (freeTokens > 0) {
            var percent = calculatePercentDifference(freeTokens, contextTokens);
            if (percent < 70) {
                context = TextUtil.shrinkText(contextRaw, percent);
            } else {
                context = "";
            }
        } else {
            context = "";
        }
        return context;
    }

    static Integer slidingContextWindow(Integer targetTokens, List<Message> history, Integer contextLength) {
        var currentContext = targetTokens + countHistoryTokens(history);
        int result = currentContext >= (contextLength * 0.8) ? currentContext + targetTokens : contextLength;
//        if(result >= 20_000) {
//            throw new AppIllegalStateException("Context window is too big: " + result);
//        }

        return result;
    }

    //TODO redo StringBuilder to arraylist
    private static void processContext(HistoryContext historyContext, FlowArgument<String> contextArg,
                                       StringBuilder previousContext, List<Message> history, String templateText) {
        var context = contextArg.stringValue();
        int itemTokens = contextArg.countTokens();

        if (historyContext.getBudget().canAllocate(itemTokens)) {
            previousContext.append(context);

            history.add(new UserMessage(templateText));
            history.add(new AssistantMessage(context));

            historyContext.getBudget().allocate(itemTokens);
        }
    }

    default List<Message> fulfillHistory(Resource systemMessage, FlowContext<Chapter> flowContext, List<HistoryItem> allowedItems) {
        return fulfillHistory(HistoryContext.builder()
                .systemMessage(systemMessage)
                .flowContext(flowContext)
                .budget(TokenBudget.defaultOne())
                .allowedItems(allowedItems)
                .build());
    }

    default List<Message> fulfillHistory(Resource systemMessage, FlowContext<Chapter> flowContext, HistoryItem... allowedItems) {
        return fulfillHistory(HistoryContext.builder()
                .systemMessage(systemMessage)
                .flowContext(flowContext)
                .budget(TokenBudget.defaultOne())
                .allowedItems(List.of(allowedItems))
                .build());
    }

    default List<Message> fulfillShortHistory(Resource systemMessage, FlowContext<Chapter> flowContext) {
        var history = new ArrayList<Message>();
        history.add(new SystemMessage(systemMessage));

        flowContext.hasAnyArgument(oldContext -> {
            history.add(new UserMessage(USER_PREVIOUS_CONTEXT_TEMPLATE));
            history.add(new AssistantMessage(oldContext.stringValue()));
        }, FlowContext::consolidatedContextArg, FlowContext::contextArg);

        flowContext.hasArgument(AppFlowActions::glossaryArg, glossary -> {
            history.add(new UserMessage(USER_GLOSSARY_TEMPLATE));
            var value = glossary.getValue().stream()
                    .map(ObjectName::shortStringValue)
                    .collect(Collectors.joining("\n"));
            history.add(new AssistantMessage(value));
        });

        return history;
    }

    @Deprecated
    default List<Message> compressHistory(List<Message> history, Integer percent) {
        var output = new ArrayList<Message>(history.size());
        if (percent > 0) {
            output.add(history.removeFirst());

            for (int i = 0; i < history.size(); i = i + 2) {
                var userMessage = history.get(i);
                var assistantMessage = history.get(i + 1);
                var text = assistantMessage.getText();

                if (!JavaUtil.isValidJson(text)) {
                    output.add(userMessage);
                    output.add(new AssistantMessage(TextUtil.shrinkText(text, percent)));
                } else {
                    output.add(userMessage);
                    output.add(assistantMessage);
                }
            }
        }

        return output;
    }

    default List<Message> fulfillHistory(HistoryContext historyContext) {
        var items = List.of(historyContext.getAllowedItems().isEmpty() ? HistoryItem.values() : historyContext.getAllowedItems().toArray(HistoryItem[]::new));
        var names = new HashSet<ObjectName>();
        var history = new ArrayList<Message>();
        var previousContext = new StringBuilder();
        history.add(new SystemMessage(historyContext.getSystemMessage()));
        var systemTokens = countTokens(historyContext.getSystemMessage());
        historyContext.getBudget().allocate(systemTokens);

        if (items.contains(HistoryItem.CONSOLIDATED_CONTEXT)) {
            historyContext.getFlowContext().hasAnyArgument(oldContext -> {
                processContext(historyContext, oldContext, previousContext, history, USER_PREVIOUS_CONTEXT_TEMPLATE);
            }, FlowContext::consolidatedContextArg, FlowContext::oldContextArg);
        }

        if (items.contains(HistoryItem.CONTEXT)) {
            historyContext.getFlowContext().hasArgument(FlowContext::contextArg, context -> {
                var currentContext = context.stringValue();
                if (!previousContext.toString().equals(currentContext)) {
                    processContext(historyContext, context, previousContext, history, USER_CONTEXT_TEMPLATE);
                }
            });
        }

        if (items.contains(HistoryItem.CONSOLIDATED_GLOSSARY)) {
            historyContext.getFlowContext().hasAnyArgument(oldGlossary -> {
                var uniqueGlossary = oldGlossary.map(list -> uniqueBy(list, ObjectName::getName));
                processGlossary(historyContext, oldGlossary, uniqueGlossary, history, names, USER_PREVIOUS_GLOSSARY_TEMPLATE);
            }, AppFlowActions::consolidatedGlossaryArg, AppFlowActions::oldGlossaryArg);
        }

        if (items.contains(HistoryItem.GLOSSARY)) {
            historyContext.getFlowContext().hasArgument(AppFlowActions::glossaryArg, glossary -> {
                var uniqueGlossary = glossary.map(chapterNames -> uniqueItems(names, chapterNames, ObjectName::getName));
                processGlossary(historyContext, glossary, uniqueGlossary, history, names, USER_GLOSSARY_TEMPLATE);
            });
        }

        return history;
    }

    private void processGlossary(HistoryContext historyContext, FlowArgument<List<ObjectName>> glossary,
                                 FlowArgument<List<ObjectName>> uniqueGlossary, List<Message> history,
                                 Set<ObjectName> names, String templateText) {
        if (uniqueGlossary.isEmpty()) {
            //TODO add logs here
            return;
        }

        var glossaryResult = findAffordableGlossary(historyContext, uniqueGlossary);

        if (glossaryResult.canAllocate()) {
            history.add(new UserMessage(templateText));
            history.add(new AssistantMessage(glossaryResult.text()));

            names.addAll(glossary.getValue());
            historyContext.getBudget().allocate(glossaryResult.tokenCount());
        }
        // TODO: add logs for failed allocation
    }

    private GlossaryResult findAffordableGlossary(HistoryContext historyContext, FlowArgument<List<ObjectName>> uniqueGlossary) {
        var budget = historyContext.getBudget();

        // Try full unique glossary
        var fullTokens = uniqueGlossary.countTokens();
        if (budget.canAllocate(fullTokens)) {
            return new GlossaryResult(uniqueGlossary.stringValue(), fullTokens, true);
        }

        // Try alt glossary argument if available
        var flowContext = historyContext.getFlowContext();
        if (flowContext.hasArgument(alt(AppFlowActions::glossaryArg))) {
            var glossaryArg = flowContext.resolve(alt(AppFlowActions::glossaryArg));

            // Try full existing glossary
            var altFullTokens = glossaryArg.countTokens();
            if (budget.canAllocate(altFullTokens)) {
                return new GlossaryResult(glossaryArg.stringValue(), altFullTokens, true);
            }
        }

//        // Try short unique glossary
//        var shortValue = uniqueGlossary.shortStringValue();
//        int shortTokens = countTokens(shortValue);
//        if (budget.canAllocate(shortTokens)) {
//            return new GlossaryResult(shortValue, shortTokens, true);
//        }

//        // Try existing glossary argument if available
//        if (flowContext.hasArgument(alt(AppFlowActions::glossaryArg))) {
//            var glossaryArg = flowContext.resolve(alt(AppFlowActions::glossaryArg));

//            // Try short existing glossary
//            var altShortValue = glossaryArg.shortStringValue();
//            int altShortTokens = countTokens(altShortValue);
//            if (budget.canAllocate(altShortTokens)) {
//                return new GlossaryResult(altShortValue, altShortTokens, true);
//            }
//        }

        // Fallback to truncated glossary
        var truncated = uniqueGlossary.map(val ->
                cut(val, subList -> AppFlowActions.glossary(subList).countTokens(), budget::canAllocate));

        return new GlossaryResult(truncated.stringValue(), truncated.countTokens(), true);
    }

    enum HistoryItem {

        CONSOLIDATED_CONTEXT,
        CONTEXT,
        CONSOLIDATED_GLOSSARY,
        GLOSSARY,
        NONE,

    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @ToString(onlyExplicitlyIncluded = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    class HistoryContext {

        @Builder.Default
        @ToString.Include
        @EqualsAndHashCode.Include
        private String id = newId("hctx-");

        private Resource systemMessage;
        private FlowContext<Chapter> flowContext;
        @ToString.Include
        private TokenBudget budget;
        @ToString.Include
        private List<HistoryItem> allowedItems;

    }

    record GlossaryResult(String text, int tokenCount, boolean canAllocate) {
    }

}
