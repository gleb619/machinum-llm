package machinum.extract;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.flow.AppFlowActions;
import machinum.flow.FlowContext;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.util.LanguageDetectorUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static machinum.util.LanguageDetectorUtil.Lang.RUSSIAN;
import static machinum.util.TextUtil.cleanTerm;

/**
 * Service responsible for translating glossary terms from external sources into Russian.
 * This class handles the complete translation workflow including term extraction,
 * language detection, and result parsing. It integrates with the flow context to
 * manage glossary updates and ensures all terms are properly translated before
 * proceeding with further processing.
 *
 * <p>The translation process involves:
 * <ul>
 *   <li>Partitioning glossary terms into chunks based on maximum size limits</li>
 *   <li>Submitting chunks for external translation services</li>
 *   <li>Parsing translation results and extracting translated terms</li>
 *   <li>Validating translations against language detection rules</li>
 *   <li>Updating the flow context with translated terms</li>
 * </ul>
 *
 * <p>This service is designed to work within a larger automation framework where
 * glossary terms need to be consistently translated and validated for accuracy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalGlossaryTranslater extends MachinumAutomataTranslater {

    private static final String ITS_KEYWORD = "это";
    private static final Pattern TRANSLATION_PATTERN = Pattern.compile("^(.+?)\\s*[^\\p{IsAlphabetic}\\p{IsDigit}]+\\s*это\\b");
    private static final String CHUNK_DELIMITER = "#>\n";
    private static final String TRANSLATION_SUFFIX = "\n#123#";
    private static final int MAX_CHUNK_SIZE = 1200;

    /**
     * Translates glossary terms from the provided flow context into Russian.
     * This method processes all terms in the glossary, separating already translated
     * terms from those that need translation. It then translates the untranslated terms
     * in chunks to manage API call limitations and updates the flow context with
     * the complete translated glossary.
     *
     * @param flowContext the flow context containing the glossary terms to be translated
     * @return the updated flow context with all glossary terms translated into Russian
     * @throws TranslationException if any terms fail to translate properly
     */
    public FlowContext<Chapter> translate(FlowContext<Chapter> flowContext) {
        log.debug("Starting translation of {} glossary terms", AppFlowActions.glossary(flowContext).size());

        var glossaryTerms = AppFlowActions.glossary(flowContext);
        var termsByTranslationStatus = partitionTermsByTranslationStatus(glossaryTerms);

        var translatedTerms = termsByTranslationStatus.get(true);
        var termsToTranslate = termsByTranslationStatus.get(false);

        if (termsToTranslate.isEmpty()) {
            log.debug("All {} terms already translated", glossaryTerms.size());
            return flowContext;
        }

        var translationResults = translateTermsInChunks(termsToTranslate);
        return buildUpdatedFlowContext(flowContext, translatedTerms, termsToTranslate, translationResults);
    }

    /**
     * Partitions the list of glossary terms into two groups based on their translation status.
     * Terms that already have a Russian name are grouped under {@code true}, while those
     * without a Russian name are grouped under {@code false}.
     *
     * @param terms the list of glossary terms to be partitioned
     * @return a map with boolean keys ({@code true} for translated, {@code false} for untranslated)
     * and lists of ObjectName as values
     */
    private Map<Boolean, List<ObjectName>> partitionTermsByTranslationStatus(List<ObjectName> terms) {
        return terms.stream()
                .collect(Collectors.partitioningBy(ObjectName::hasRuName));
    }

    /**
     * Translates a list of glossary terms in chunks to manage API call limitations.
     * The terms are first divided into chunks based on maximum chunk size,
     * then each chunk is translated separately and the results are collected.
     *
     * @param terms the list of glossary terms to be translated
     * @return a list of translation results corresponding to the input terms
     */
    private List<String> translateTermsInChunks(List<ObjectName> terms) {
        log.debug("Translating {} terms in chunks", terms.size());

        var allResults = new ArrayList<String>();
        var chunks = createTranslationChunks(terms);

        for (var chunk : chunks) {
            var translatedChunk = translateChunk(chunk);
            allResults.addAll(translatedChunk.translated());
        }

        log.debug("Translation complete: {} results", allResults.size());
        return allResults;
    }

    /**
     * Creates translation chunks from a list of glossary terms to manage API call limitations.
     * This method groups terms into chunks based on the maximum chunk size limit, ensuring
     * that each chunk does not exceed {@link #MAX_CHUNK_SIZE}. Each term is first formatted
     * as a translation line using {@link #buildTranslationLine(ObjectName)}, then appended
     * to chunks with appropriate delimiters. The final chunk is added even if it's not full.
     *
     * @param terms the list of glossary terms to be grouped into translation chunks
     * @return a list of formatted chunk strings ready for translation API consumption
     */
    private List<String> createTranslationChunks(List<ObjectName> terms) {
        var chunks = new ArrayList<String>();
        var currentChunk = new StringBuilder();
        var currentSize = 0;

        for (int i = 0; i < terms.size(); i++) {
            var translationLine = buildTranslationLine(terms.get(i));
            var lineSize = translationLine.length() + CHUNK_DELIMITER.length();

            if (shouldStartNewChunk(currentSize, lineSize, currentChunk)) {
                chunks.add(formatChunkForTranslation(currentChunk.toString()));
                currentChunk = new StringBuilder();
                currentSize = 0;
            }

            appendToChunk(currentChunk, translationLine, i > 0 && !currentChunk.isEmpty());
            currentSize += lineSize;
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(formatChunkForTranslation(currentChunk.toString()));
        }

        return chunks;
    }

    /**
     * Builds a formatted translation line for a glossary term.
     * The line includes the cleaned term name, its category, and description
     * in a specific format suitable for translation API consumption.
     *
     * @param term the glossary term to build translation line for
     * @return a formatted string containing term information for translation
     */
    private String buildTranslationLine(ObjectName term) {
        var cleanName = cleanTerm(term.getName());
        return "%s - it's a %s; Example of usage: %s".formatted(
                cleanName, term.getCategory(), term.getDescription());
    }

    /**
     * Determines whether a new translation chunk should be started based on size constraints.
     * A new chunk is started if adding the current line would exceed the maximum chunk size limit.
     *
     * @param currentSize the current size of the chunk being built
     * @param lineSize    the size of the line to be added to the chunk
     * @param chunk       the StringBuilder representing the current chunk being constructed
     * @return {@code true} if a new chunk should be started; {@code false} otherwise
     */
    private boolean shouldStartNewChunk(int currentSize, int lineSize, StringBuilder chunk) {
        return currentSize + lineSize > MAX_CHUNK_SIZE && !chunk.isEmpty();
    }

    /**
     * Appends a translation line to the current chunk with optional delimiter.
     * If a delimiter is needed, it's added before appending the line.
     *
     * @param chunk          the StringBuilder representing the chunk to which the line will be appended
     * @param line           the translation line to append to the chunk
     * @param needsDelimiter {@code true} if a delimiter should be added before the line; {@code false} otherwise
     */
    private void appendToChunk(StringBuilder chunk, String line, boolean needsDelimiter) {
        if (needsDelimiter) {
            chunk.append(CHUNK_DELIMITER);
        }
        chunk.append(line);
    }

    /**
     * Formats a chunk of translation lines by appending a specific suffix.
     * This suffix is used to mark the end of a translation chunk for processing.
     *
     * @param chunk the chunk string to be formatted
     * @return the formatted chunk string with the translation suffix appended
     */
    private String formatChunkForTranslation(String chunk) {
        return chunk + TRANSLATION_SUFFIX;
    }

    /**
     * Translates a single chunk of glossary terms using the translation API and parses the raw result into structured translation data.
     * This method takes a formatted chunk string as input, sends it to the translation service via {@link #doTranslate(String)},
     * and then processes the response using {@link #parseTranslationResult(String)}. The method returns a {@link TranslationResult}
     * object containing lists of successfully translated terms and those that failed to translate properly. This approach allows
     * for efficient batch processing of glossary terms while maintaining proper error handling and result parsing.
     *
     * @param chunk the formatted chunk string containing glossary terms to be translated
     * @return a TranslationResult object containing lists of successfully translated terms and those that failed to translate
     */
    private TranslationResult translateChunk(String chunk) {
        log.debug("Translating chunk of {} symbols", chunk.length());
        var rawResult = doTranslate(chunk);
        return parseTranslationResult(rawResult);
    }

    /**
     * Builds and returns an updated flow context with the translated glossary terms.
     * This method validates the translation results, merges the already translated
     * terms with the newly translated ones, and updates the flow context with the
     * complete glossary. It also performs a final validation to ensure all terms
     * have been successfully translated.
     *
     * @param flowContext        the original flow context containing the glossary terms
     * @param alreadyTranslated  list of terms that were already translated
     * @param originalTerms      list of original terms that were translated
     * @param translationResults list of translation results corresponding to the original terms
     * @return an updated flow context with all glossary terms translated into Russian
     * @throws TranslationException if any terms fail to translate properly
     */
    private FlowContext<Chapter> buildUpdatedFlowContext(FlowContext<Chapter> flowContext,
                                                         List<ObjectName> alreadyTranslated,
                                                         List<ObjectName> originalTerms,
                                                         List<String> translationResults) {
        validateTranslationResults(originalTerms, translationResults);

        var updatedTerms = mergeTranslationResults(alreadyTranslated, originalTerms, translationResults);
        validateAllTermsTranslated(updatedTerms);

        log.debug("Updated glossary with {} terms", updatedTerms.size());
        return flowContext.replace(AppFlowActions::glossaryArg, AppFlowActions.glossary(updatedTerms));
    }

    /**
     * Validates that the number of translation results matches or exceeds the number of original terms.
     * This method ensures that no terms were lost during the translation process. If the number
     * of results is less than the number of original terms, an exception is thrown to indicate
     * a translation failure.
     *
     * @param originalTerms list of original terms before translation
     * @param results       list of translation results after translation
     * @throws AppIllegalStateException if translation results are fewer than original terms
     */
    private void validateTranslationResults(List<ObjectName> originalTerms, List<String> results) {
        if (originalTerms.size() > results.size()) {
            throw new AppIllegalStateException("Translation lost terms: expected %d, got %d",
                    originalTerms.size(), results.size());
        }
    }

    /**
     * Merges already translated terms with newly translated terms based on the translation results.
     * For each original term, if a valid translation is provided, it creates a new ObjectName with the Russian name;
     * otherwise, it keeps the original term unchanged. The method preserves the order of terms and ensures
     * that all previously translated terms are maintained in the result.
     *
     * @param alreadyTranslated  list of terms that were already translated and should be preserved
     * @param originalTerms      list of original terms that were subject to translation
     * @param translationResults list of translation results corresponding to the original terms
     * @return a new list containing merged terms with updated Russian names where translations were successful
     */
    private List<ObjectName> mergeTranslationResults(List<ObjectName> alreadyTranslated,
                                                     List<ObjectName> originalTerms,
                                                     List<String> translationResults) {
        var merged = new ArrayList<ObjectName>(alreadyTranslated);

        for (int i = 0; i < originalTerms.size(); i++) {
            var originalTerm = originalTerms.get(i);
            var translation = translationResults.get(i);

            if (isValidTranslation(translation)) {
                merged.add(originalTerm.withRuName(translation));
            } else {
                merged.add(originalTerm);
            }
        }

        return merged;
    }

    /**
     * Validates whether a given translation string is considered valid for use.
     * A translation is considered valid if it is not null and not blank (contains only whitespace characters).
     *
     * @param translation the translation string to validate
     * @return {@code true} if the translation is valid (not null and not blank); {@code false} otherwise
     */
    private boolean isValidTranslation(String translation) {
        return Objects.nonNull(translation) && !translation.isBlank();
    }

    /**
     * Validates that all terms in the provided list have been successfully translated.
     * This method checks if any terms still lack a Russian name after translation processing.
     * If untranslated terms are found, it logs a warning and throws a TranslationException
     * to indicate that the translation process failed for some terms.
     *
     * @param terms list of terms to validate for having Russian names
     * @throws TranslationException if any terms in the list do not have Russian names after translation
     */
    private void validateAllTermsTranslated(List<ObjectName> terms) {
        var untranslatedTerms = terms.stream()
                .filter(term -> !term.hasRuName())
                .collect(Collectors.toList());

        if (!untranslatedTerms.isEmpty()) {
            log.warn("Failed to translate {} terms: {}", untranslatedTerms.size(), untranslatedTerms);
            throw new TranslationException(terms);
        }
    }

    /**
     * Parses the raw translation result string into structured translation data.
     * This method splits the raw result by the chunk delimiter and processes each item
     * to extract translated terms. If a term is successfully detected as Russian,
     * it's added to the translated list; otherwise, it's added to the not translated list.
     * The method ensures that even failed extractions are accounted for in the output.
     *
     * @param rawResult the raw string result from the translation API
     * @return a TranslationResult object containing lists of successfully translated terms
     * and those that failed to translate properly
     */
    private TranslationResult parseTranslationResult(String rawResult) {
        var translated = new ArrayList<String>();
        var notTranslated = new ArrayList<String>();

        var items = rawResult.split("%s?".formatted(CHUNK_DELIMITER));
        log.debug("Parsing translation result from {} items", items.length);

        Stream.of(items).forEach(item -> {
            var extractedTerm = extractTranslatedTerm(item);

            if (isRussianTerm(extractedTerm)) {
                translated.add(extractedTerm);
            } else {
                translated.add("");
                notTranslated.add(extractedTerm != null ? extractedTerm : item);
            }
        });

        log.debug("Parsed {} translated, {} not translated", translated.size(), notTranslated.size());
        return new TranslationResult(translated, notTranslated);
    }

    /**
     * Extracts a translated term from a translation item string.
     * This method first attempts to extract the term using a predefined regex pattern.
     * If the pattern matching fails, it falls back to manual extraction logic
     * which searches for the keyword "its" (used as a marker) and extracts the term before it.
     *
     * @param translationItem the raw translation item string to process
     * @return the extracted translated term if successful, or null if extraction fails
     */
    private String extractTranslatedTerm(String translationItem) {
        var patternMatcher = TRANSLATION_PATTERN.matcher(translationItem);

        if (patternMatcher.find()) {
            return patternMatcher.group(1).trim();
        }

        return extractTermManually(translationItem);
    }

    /**
     * Determines whether the given term is detected as Russian language.
     * This method uses a language detection utility to identify the language of the term.
     * If the detected language matches the predefined Russian language constant, it returns true.
     *
     * @param term the term string to be checked for Russian language
     * @return {@code true} if the term is detected as Russian; {@code false} otherwise or if the term is null
     */
    private boolean isRussianTerm(String term) {
        if (term == null) return false;

        var detectedLanguage = LanguageDetectorUtil.detectLang(term);
        log.trace("Term '{}' detected as {}", term, detectedLanguage);
        return RUSSIAN.equals(detectedLanguage);
    }

    /**
     * Manually extracts a translated term from a line of text.
     * This method attempts to find a special character and then locate the keyword "its"
     * to determine where the term ends. It returns the substring before the special character
     * if the keyword is found, otherwise it logs a failure and returns null.
     *
     * @param line the raw line of text from which to extract the term
     * @return the extracted term if successful; {@code null} if extraction fails
     */
    private String extractTermManually(String line) {
        log.trace("Manual extraction for: {}", line);

        for (int i = 0; i < line.length() - 2; i++) {
            if (isSpecialCharacter(line.charAt(i))) {
                var keywordPosition = findKeywordPosition(line, i);
                if (keywordPosition != -1) {
                    return line.substring(0, i).trim();
                }
            }
        }

        log.trace("Manual extraction failed for: {}", line);
        return null;
    }

    /**
     * Checks if the given character is a special character.
     * A special character is defined as a character that is neither a letter nor a digit,
     * and also not a whitespace character.
     *
     * @param c the character to check
     * @return {@code true} if the character is a special character; {@code false} otherwise
     */
    private boolean isSpecialCharacter(char c) {
        return !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
    }

    /**
     * Finds the position of the keyword "its" in the given line starting from the specified index.
     * This method searches for the first letter character after the start index and checks
     * if the keyword "its" starts at that position. If found, it returns the position;
     * otherwise, it returns -1.
     *
     * @param line       the string to search in
     * @param startIndex the index to start searching from
     * @return the position where the keyword "its" starts, or -1 if not found
     */
    private int findKeywordPosition(String line, int startIndex) {
        for (int j = startIndex; j < line.length(); j++) {
            if (Character.isLetter(line.charAt(j))) {
                if (j + 2 < line.length() && line.startsWith(ITS_KEYWORD, j)) {
                    return j;
                }
                break;
            }
        }
        return -1;
    }

    /**
     * Record representing the result of a translation process.
     * Contains lists of successfully translated terms and those that failed to translate.
     */
    record TranslationResult(List<String> translated, List<String> notTranslated) {
    }

    /**
     * Exception thrown when translation process fails for one or more terms.
     * Contains the list of object names that were not successfully translated.
     */
    @Value
    @RequiredArgsConstructor
    public static class TranslationException extends AppIllegalStateException {

        List<ObjectName> objectNames;

    }

}