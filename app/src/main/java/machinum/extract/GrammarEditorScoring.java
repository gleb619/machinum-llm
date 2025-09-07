package machinum.extract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Component
@RequiredArgsConstructor
public class GrammarEditorScoring extends AbstractScoring {

    @Value("${app.translate.copy-editing-scoring.model}")
    protected final String chatModel;

    @Value("${app.translate.copy-editing-scoring.temperature}")
    protected final Double temperature;

    @Value("${app.translate.copy-editing-scoring.numCtx}")
    protected final Integer contextLength;

    @Override
    public String getOperation() {
        return "copyEditScoring";
    }

}
