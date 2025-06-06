package machinum.processor.core;

import lombok.*;
import machinum.flow.FlowArgument;
import machinum.flow.FlowContext;
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
import java.util.List;
import java.util.stream.Collectors;

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

    default List<Message> fulfillHistory(HistoryContext historyContext) {
        var items = List.of(historyContext.getAllowedItems().isEmpty() ? HistoryItem.values() : historyContext.getAllowedItems().toArray(HistoryItem[]::new));
        var names = new ArrayList<ObjectName>();
        var history = new ArrayList<Message>();
        var previousContext = new StringBuilder();
        history.add(new SystemMessage(historyContext.getSystemMessage()));
        var systemTokens = countTokens(historyContext.getSystemMessage());
        historyContext.getBudget().allocate(systemTokens);

        if (items.contains(HistoryItem.CONSOLIDATED_CONTEXT)) {
            historyContext.getFlowContext().hasAnyArgument(oldContext -> {
                var context = oldContext.stringValue();
                int itemTokens = countTokens(context);

                if (historyContext.getBudget().canAllocate(itemTokens)) {
                    previousContext.append(context);

                    history.add(new UserMessage(USER_PREVIOUS_CONTEXT_TEMPLATE));
                    history.add(new AssistantMessage(context));

                    historyContext.getBudget().allocate(itemTokens);
                }
            }, FlowContext::consolidatedContextArg, FlowContext::oldContexArg);
        }

        if (items.contains(HistoryItem.CONTEXT)) {
            historyContext.getFlowContext().hasArgument(FlowContext::contextArg, context -> {
                var currentContext = context.stringValue();

                if (!previousContext.toString().equals(currentContext)) {
                    int itemTokens = countTokens(currentContext);

                    if (historyContext.getBudget().canAllocate(itemTokens)) {
                        history.add(new UserMessage(USER_CONTEXT_TEMPLATE));
                        history.add(new AssistantMessage(currentContext));

                        historyContext.getBudget().allocate(itemTokens);
                    }
                }
            });
        }

        if (items.contains(HistoryItem.CONSOLIDATED_GLOSSARY)) {
            historyContext.getFlowContext().hasAnyArgument(oldGlossary -> {
                int itemTokens = oldGlossary.countTokens();

                if (historyContext.getBudget().canAllocate(itemTokens)) {
                    history.add(new UserMessage(USER_PREVIOUS_GLOSSARY_TEMPLATE));
                    var arg = oldGlossary.map(list -> uniqueBy(list, ObjectName::getName));

                    if (arg.countWords() > 700) {
                        history.add(new AssistantMessage(arg.shortStringValue()));
                    } else {
                        history.add(new AssistantMessage(arg.stringValue()));
                    }

                    names.addAll(oldGlossary.getValue());

                    historyContext.getBudget().allocate(itemTokens);
                }
            }, FlowContext::consolidatedGlossaryArg, FlowContext::oldGlossaryArg);
        }

        if (items.contains(HistoryItem.GLOSSARY)) {
            historyContext.getFlowContext().hasArgument(FlowContext::glossaryArg, glossary -> {
                int itemTokens = glossary.countTokens();

                if (historyContext.getBudget().canAllocate(itemTokens)) {
                    var smallList = glossary.map(chapterNames -> uniqueItems(names, chapterNames, ObjectName::getName));

                    if (!smallList.isEmpty()) {
                        history.add(new UserMessage(USER_GLOSSARY_TEMPLATE));

                        if (smallList.countWords() > 1_500) {
                            history.add(new AssistantMessage(smallList.shortStringValue()));
                        } else {
                            history.add(new AssistantMessage(smallList.stringValue()));
                        }

                        historyContext.getBudget().allocate(itemTokens);
                    }
                }
            });
        }

        return history;
    }

    default List<Message> fulfillShortHistory(Resource systemMessage, FlowContext<Chapter> flowContext) {
        var history = new ArrayList<Message>();
        history.add(new SystemMessage(systemMessage));

        flowContext.hasAnyArgument(oldContext -> {
            history.add(new UserMessage(USER_PREVIOUS_CONTEXT_TEMPLATE));
            history.add(new AssistantMessage(oldContext.stringValue()));
        }, FlowContext::consolidatedContextArg, FlowContext::contextArg);

        flowContext.hasArgument(FlowContext::glossaryArg, glossary -> {
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

}
