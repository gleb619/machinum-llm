package machinum.extract;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.core.FlowContext;
import machinum.model.Chapter;
import machinum.processor.core.ChunkSupport;
import org.springframework.stereotype.Component;

import static machinum.config.Constants.FLOW_TYPE;

@Slf4j
@Component
@RequiredArgsConstructor
public class Summarizer implements ChunkSupport {

    private final SummaryExtractor summaryExtractor;
    private final SummaryConsolidator summaryConsolidator;

    //TODO move to the flow processor, maybe?
    public FlowContext<Chapter> summarize(FlowContext<Chapter> flowContext) {
        boolean isSimpleFlow = "simple".equals(flowContext.metadata(FLOW_TYPE, "none"));

        FlowContext<Chapter> chapterFlowContext;
        if (isSimpleFlow) {
            chapterFlowContext = summaryExtractor.simpleExtract(flowContext);
        } else {
            chapterFlowContext = summaryExtractor.extractSummary(flowContext);
            //                .then(summaryConsolidator::consolidate);
        }

        return chapterFlowContext;
    }

}
