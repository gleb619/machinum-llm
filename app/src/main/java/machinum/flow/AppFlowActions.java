package machinum.flow;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import machinum.flow.model.FlowArgument;
import machinum.flow.model.FlowContext;
import machinum.flow.model.helper.FlowContextActions;
import machinum.model.ObjectName;
import machinum.processor.core.ChapterWarning;

import java.util.List;

import static machinum.flow.constant.FlowContextConstants.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AppFlowActions {

    public static <T> FlowArgument<List<ObjectName>> glossaryArg(FlowContext<T> flowContext) {
        return flowContext.arg(GLOSSARY_PARAM);
    }

    public static <T> FlowArgument<List<ObjectName>> consolidatedGlossaryArg(FlowContext<T> flowContext) {
        return flowContext.arg(CONSOLIDATED_GLOSSARY_PARAM);
    }

    public static <T> List<ObjectName> glossary(FlowContext<T> flowContext) {
        return glossaryArg(flowContext).getValue();
    }

    public static <T> List<ObjectName> consolidatedGlossary(FlowContext<T> flowContext) {
        return consolidatedGlossaryArg(flowContext).getValue();
    }

    public static <T> List<ObjectName> oldGlossary(FlowContext<T> flowContext) {
        return oldGlossaryArg(flowContext).getValue();
    }

    public static <T> FlowArgument<List<ObjectName>> oldGlossaryArg(FlowContext<T> flowContext) {
        return flowContext.oldArg(GLOSSARY_PARAM);
    }

    public static <T> FlowArgument<ChapterWarning> warningArg(FlowContext<T> flowContext) {
        return flowContext.arg(WARNING_PARAM);
    }

    public static <T> ChapterWarning warning(FlowContext<T> flowContext) {
        return warningArg(flowContext).getValue();
    }

    public static <T> FlowArgument<ChapterWarning> oldWarningArg(FlowContext<T> flowContext) {
        return flowContext.oldArg(WARNING_PARAM);
    }

    public static <T> ChapterWarning oldWarning(FlowContext<T> flowContext) {
        return oldWarningArg(flowContext).getValue();
    }

    public static FlowArgument<List<ObjectName>> glossary(List<ObjectName> value) {
        return FlowContextActions.createArg(GLOSSARY_PARAM, value);
    }

    public static FlowArgument<List<ObjectName>> consolidatedGlossary(List<ObjectName> value) {
        return FlowContextActions.createArg(CONSOLIDATED_GLOSSARY_PARAM, value);
    }

    public static FlowArgument<ChapterWarning> warning(ChapterWarning chapterWarning) {
        return FlowContextActions.createArg(WARNING_PARAM, chapterWarning);
    }

}
