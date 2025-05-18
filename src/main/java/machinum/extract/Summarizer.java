package machinum.extract;

import machinum.model.Chapter;
import machinum.processor.core.ChunkSupport;
import machinum.flow.FlowContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static machinum.config.Constants.FLOW_TYPE;
import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@Component
@RequiredArgsConstructor
public class Summarizer implements ChunkSupport {

    private final SummaryExtractor summaryExtractor;
    private final SummaryConsolidator summaryConsolidator;

    //TODO move to the flow processor, maybe?
    public FlowContext<Chapter> summarize(FlowContext<Chapter> flowContext) {
        boolean isSimpleFlow = "simple".equals(flowContext.metadata(FLOW_TYPE, "none"));

        if(isSimpleFlow) {
            return summaryExtractor.simpleExtract(flowContext);
        } else {
            return summaryExtractor.extractSummary(flowContext);
    //                .then(summaryConsolidator::consolidate);
        }
    }

}
