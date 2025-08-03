package machinum.extract;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.flow.FlowContext;
import machinum.flow.FlowContextActions;
import machinum.flow.Pack;
import machinum.model.Chapter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import static machinum.config.Constants.TITLE;
import static machinum.config.Constants.TRANSLATED_TITLE;
import static machinum.util.TextUtil.indent;
import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalTitleTranslater extends MachinumAutomataTranslater {

    private static final String DELIMITER = " #>\n";

    public FlowContext<Chapter> batchTranslate(FlowContext<Chapter> flowContext) {
        List<Pack<Chapter, String>> items = flowContext.result();

        var titles = items.stream()
                .map(pack -> pack.getArgument().stringValue())
                .toList();
        var data = items.stream()
                .collect(Collectors.toMap(pack -> pack.getArgument().getValue(), Pack::getItem, (f, s) -> f, LinkedHashMap::new));

        log.debug("Prepare to translate batch: titles[{}]={}...", data.size(), toShortDescription(data.keySet()));

        var maResult = doTranslate(String.join(DELIMITER, titles));
        var translatedTitles = List.of(maResult.split("%s?".formatted(DELIMITER)));

        if (translatedTitles.size() < titles.size()) {
            throw new AppIllegalStateException("Lost title due translation process");
        }

        var output = new ArrayList<Pack<Chapter, String>>();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            int index = i;
            output.add(Pack.createNew(b -> b
                    .item(item.getItem())
                    .argument(FlowContextActions.createArg(TRANSLATED_TITLE, translatedTitles.get(index)))
            ));
        }

        log.debug("Prepared translated version: titles[{}]=\n{}...", translatedTitles.size(), indent(toShortDescription(translatedTitles)));

        return flowContext.rearrange(FlowContext::resultArg, FlowContextActions.result(output));
    }

    public FlowContext<Chapter> translate(FlowContext<Chapter> flowContext) {
        var titleArg = flowContext.arg(TITLE);
        var text = titleArg.stringValue();
        log.debug("Prepare to translate: title={}...", toShortDescription(text));

        var result = doTranslate(text);

        log.debug("Prepared translated version: title={}...", toShortDescription(result));

        return flowContext.rearrange(ctx -> ctx.arg(TRANSLATED_TITLE), FlowContextActions.createArg(TRANSLATED_TITLE, result));
    }

}
