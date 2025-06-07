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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static machinum.flow.FlowContextActions.alt;
import static machinum.flow.FlowContextActions.glossary;
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
            }, FlowContext::consolidatedContextArg, FlowContext::oldContexArg);
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
            }, FlowContext::consolidatedGlossaryArg, FlowContext::oldGlossaryArg);
        }

        if (items.contains(HistoryItem.GLOSSARY)) {
            historyContext.getFlowContext().hasArgument(FlowContext::glossaryArg, glossary -> {
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

        int itemTokens = glossary.countTokens();

        boolean canAllocate = false;
        String glossaryText;

        if (historyContext.getBudget().canAllocate(itemTokens)) {
            glossaryText = uniqueGlossary.stringValue();
            canAllocate = true;
        } else {
            var shortStringValue = uniqueGlossary.shortStringValue();
            int shortItemTokens = countTokens(shortStringValue);

            if (historyContext.getBudget().canAllocate(shortItemTokens)) {
                glossaryText = shortStringValue;
                canAllocate = true;
                itemTokens = shortItemTokens;
            } else {
                boolean hasArgument = historyContext.getFlowContext().hasArgument(alt(FlowContext::glossaryArg));
                if (hasArgument) {
                    var glossaryArg = historyContext.getFlowContext().resolve(alt(FlowContext::glossaryArg));
                    Integer currentChapTokens = glossaryArg.countTokens();
                    if (historyContext.getBudget().canAllocate(currentChapTokens)) {
                        glossaryText = glossaryArg.stringValue();
                        canAllocate = true;
                        itemTokens = currentChapTokens;
                    } else {
                        var shortCurrentChapValue = glossaryArg.shortStringValue();
                        int shortCurrentChapTokens = countTokens(shortStringValue);

                        if (historyContext.getBudget().canAllocate(shortCurrentChapTokens)) {
                            glossaryText = shortCurrentChapValue;
                            canAllocate = true;
                            itemTokens = shortCurrentChapTokens;
                        } else {
                            var smallArg = uniqueGlossary.map(val -> cut(val, subList -> glossary(subList).countTokens(), historyContext.getBudget()::canAllocate));
                            glossaryText = smallArg.stringValue();
                            canAllocate = true;
                            itemTokens = smallArg.countTokens();
                        }
                    }
                } else {
                    var smallArg = uniqueGlossary.map(val -> cut(val, subList -> glossary(subList).countTokens(), historyContext.getBudget()::canAllocate));
                    glossaryText = smallArg.stringValue();
                    canAllocate = true;
                    itemTokens = smallArg.countTokens();
                }
            }
        }

        if (canAllocate) {
            history.add(new UserMessage(templateText));
            history.add(new AssistantMessage(glossaryText));

            names.addAll(glossary.getValue());

            historyContext.getBudget().allocate(itemTokens);
        } else {
            //TODO add logs here
        }
    }

}
