package machinum.extract;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static machinum.util.JavaUtil.calculatePercentDifference;
import static org.springframework.util.CollectionUtils.isEmpty;

@Slf4j
@Getter
@Component
@RequiredArgsConstructor
public class TranslationScoring extends AbstractScoring {

    @Value("${app.translate.scoring.model}")
    protected final String chatModel;

    @Value("${app.translate.scoring.temperature}")
    protected final Double temperature;

    @Value("${app.translate.scoring.numCtx}")
    protected final Integer contextLength;

    @Override
    public String getOperation() {
        return "translateScoring";
    }

}
