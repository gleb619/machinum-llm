package machinum.extract;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.flow.model.FlowContext;
import machinum.flow.model.Pack;
import machinum.flow.model.helper.FlowContextActions;
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

/**
 * Service for translating chapter titles using external translation mechanism.
 * Handles both single title translation and batch translation of multiple titles.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalTitleTranslater extends MachinumAutomataTranslater {

    private static final String DELIMITER = "#>\n";
    private static final String TRANSLATION_SUFFIX = "\n---";
    private static final int CHUNK_SIZE = 1300;

    /**
     * Translates a batch of chapter titles.
     *
     * @param flowContext the flow context containing chapters and their titles
     * @return updated flow context with translated titles
     */
    public FlowContext<Chapter> batchTranslate(FlowContext<Chapter> flowContext) {
        List<Pack<Chapter, String>> items = flowContext.result();
        var data = extractDataMap(items);
        var titles = extractTitles(items);

        log.debug("Prepare to translate batch: titles[{}]={}...", data.size(), toShortDescription(data.keySet()));

        var translatedTitles = translateInChunks(titles);
        validateTranslationResults(titles, translatedTitles);
        var output = buildOutputPacks(items, translatedTitles);

        log.debug("Prepared translated version: titles[{}]=\n{}...", translatedTitles.size(), indent(toShortDescription(translatedTitles)));
        return flowContext.rearrange(FlowContext::resultArg, FlowContextActions.result(output));
    }

    /**
     * Translates a single chapter title.
     *
     * @param flowContext the flow context containing the title to translate
     * @return updated flow context with translated title
     */
    public FlowContext<Chapter> translate(FlowContext<Chapter> flowContext) {
        var titleArg = flowContext.arg(TITLE);
        var text = titleArg.stringValue();

        log.debug("Prepare to translate: title={}...", toShortDescription(text));

        var result = translateSingleText(text);

        log.debug("Prepared translated version: title={}...", toShortDescription(result));
        return flowContext.rearrange(ctx -> ctx.arg(TRANSLATED_TITLE), FlowContextActions.createArg(TRANSLATED_TITLE, result));
    }

    /**
     * Extracts titles from the list of packs.
     *
     * @param items list of packs containing chapters and their titles
     * @return list of title strings
     */
    private List<String> extractTitles(List<Pack<Chapter, String>> items) {
        return items.stream()
                .map(pack -> pack.getArgument().stringValue())
                .toList();
    }

    /**
     * Extracts data map from the list of packs.
     *
     * @param items list of packs containing chapters and their titles
     * @return LinkedHashMap mapping title strings to chapters
     */
    private LinkedHashMap<String, Chapter> extractDataMap(List<Pack<Chapter, String>> items) {
        return items.stream()
                .collect(Collectors.toMap(pack -> pack.getArgument().getValue(), Pack::getItem, (f, s) -> f, LinkedHashMap::new));
    }

    /**
     * Translates titles in chunks to handle size limitations.
     *
     * @param titles list of titles to translate
     * @return list of translated titles
     */
    private List<String> translateInChunks(List<String> titles) {
        var combinedText = String.join(DELIMITER, titles);
        var chunks = splitIntoChunks(combinedText);
        var translatedChunks = new ArrayList<String>();

        for (String chunk : chunks) {
            var translated = doTranslate(chunk + TRANSLATION_SUFFIX);
            translatedChunks.add(translated);
        }

        var fullTranslatedText = String.join("", translatedChunks)
                .replaceAll(TRANSLATION_SUFFIX, "")
                .replaceAll(TRANSLATION_SUFFIX.replace("\n", ""), "");
        return List.of(fullTranslatedText.split("%s?".formatted(DELIMITER)));
    }

    /**
     * Translates a single text by splitting it into chunks.
     *
     * @param text the text to translate
     * @return translated text
     */
    private String translateSingleText(String text) {
        var chunks = splitIntoChunks(text);
        var translatedChunks = new ArrayList<String>();

        for (String chunk : chunks) {
            var translated = doTranslate(chunk + TRANSLATION_SUFFIX);
            translatedChunks.add(translated);
        }

        return String.join("", translatedChunks);
    }

    /**
     * Splits text into chunks of specified size.
     *
     * @param text the text to split
     * @return list of text chunks
     */
    private List<String> splitIntoChunks(String text) {
        var chunks = new ArrayList<String>();
        for (int i = 0; i < text.length(); i += CHUNK_SIZE) {
            int endIndex = Math.min(i + CHUNK_SIZE, text.length());
            chunks.add(text.substring(i, endIndex));
        }
        return chunks;
    }

    /**
     * Validates translation results to ensure no titles were lost.
     *
     * @param original   list of original titles
     * @param translated list of translated titles
     */
    private void validateTranslationResults(List<String> original, List<String> translated) {
        if (translated.size() < original.size()) {
            log.error("Found error, can't detect translation for:\n\noriginal={},\ntranslated={}", original, translated);
            throw new AppIllegalStateException("Lost title due translation process: %s <> %s", original.size(), translated.size());
        }
    }

    /**
     * Builds output packs with translated titles.
     *
     * @param items            original list of packs
     * @param translatedTitles list of translated titles
     * @return list of updated packs with translated titles
     */
    private List<Pack<Chapter, String>> buildOutputPacks(List<Pack<Chapter, String>> items, List<String> translatedTitles) {
        var output = new ArrayList<Pack<Chapter, String>>();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            int index = i;
            output.add(Pack.createNew(b -> b
                    .item(item.getItem())
                    .argument(FlowContextActions.createArg(TRANSLATED_TITLE, translatedTitles.get(index)))
            ));
        }
        return output;
    }

}