package machinum.extract;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import machinum.exception.AppIllegalStateException;
import machinum.flow.FlowContext;
import machinum.flow.FlowContextActions;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.util.LanguageDetectorUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static machinum.util.LanguageDetectorUtil.Lang.RUSSIAN;
import static machinum.util.TextUtil.cleanTerm;
import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalGlossaryTranslater extends MachinumAutomataTranslater {

    public static final String ITS_A_KEYWORD = "это";
    private static final Pattern PATTERN = Pattern.compile("^(.+?)\\s*[^\\p{IsAlphabetic}\\p{IsDigit}]+\\s*это\\b");
    private static final String DELIMITER = "#>\n";

    private static String extractTerm(String line) {
        int separatorStart = -1;
        int etoStart = -1;

        for (int i = 0; i < line.length() - 2; i++) {
            char c = line.charAt(i);

            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) {
                separatorStart = i;

                for (int j = i; j < line.length(); j++) {
                    char nextChar = line.charAt(j);
                    if (Character.isLetter(nextChar)) {
                        if (j + 2 < line.length() &&
                                line.startsWith(ITS_A_KEYWORD, j)) {
                            etoStart = j;
                            break;
                        } else {
                            break;
                        }
                    }
                }

                if (etoStart != -1) {
                    return line.substring(0, separatorStart).trim();
                }
            }
        }

        return null;
    }

    public FlowContext<Chapter> translate(FlowContext<Chapter> flowContext) {
        var names = new ArrayList<>(flowContext.glossary());
        var termsGroupedByRuName = flowContext.glossary().stream()
                .collect(Collectors.groupingBy(ObjectName::hasRuName,
                        Collectors.mapping(Function.identity(),
                                Collectors.toList())));

        var readyTerms = termsGroupedByRuName.getOrDefault(true, List.of());
        // false == ObjectName hasn't ruName
        var termsToWork = termsGroupedByRuName.getOrDefault(false, List.of());

        if (termsToWork.isEmpty()) {
            log.debug("All terms have already been translated: names={}", names.size());
            return flowContext;
        }

        var text = termsToWork.stream()
                .map(objectName -> {
                    var name = cleanTerm(objectName.getName());
                    return "%s - it's a %s; Example of usage: %s".formatted(name, objectName.getCategory(), objectName.getDescription());
                })
                .collect(Collectors.joining(DELIMITER));

        log.debug("Prepare to translate: names={}...", toShortDescription(text));

        var result = doTranslate(text);
        var translationResult = process(result);

        if (termsToWork.size() > translationResult.translated().size()) {
            throw new AppIllegalStateException("Names was lost due translation process: \n{}\n{}", termsToWork, translationResult.translated());
        }

        var output = new ArrayList<ObjectName>(names.size());

        output.addAll(readyTerms);
        for (int i = 0; i < termsToWork.size(); i++) {
            var objectName = termsToWork.get(i);
            var translatedName = translationResult.translated().get(i);
            if (Objects.nonNull(translatedName) && !translatedName.isBlank()) {
                output.add(objectName.withRuName(translatedName));
            } else {
                output.add(objectName);
            }
        }

        if (!translationResult.notTranslated().isEmpty()) {
            log.warn("Not all names have been translated: list={}", translationResult.notTranslated());
            throw new TranslationException(output);
        }

        log.debug("Prepared translated version: names={}...", toShortDescription(result));

        return flowContext.replace(FlowContext::glossaryArg, FlowContextActions.glossary(output));
    }

    private TranslationResult process(String result) {
        var translated = new ArrayList<String>();
        var notTranslated = new ArrayList<String>();

        Stream.of(result.split("%s?".formatted(DELIMITER)))
                .forEach(s -> {
                    String term;
                    var matcher = PATTERN.matcher(s);
                    if (matcher.find()) {
                        term = matcher.group(1).trim();
                    } else {
                        term = extractTerm(s);
                    }

                    if (Objects.nonNull(term)) {
                        var lang = LanguageDetectorUtil.detectLang(term);
                        if (lang.equals(RUSSIAN)) {
                            translated.add(term);
                        } else {
                            translated.add("");
                            notTranslated.add(term);
                        }
                    } else {
                        translated.add("");
                        notTranslated.add(s);
                    }
                });

        return new TranslationResult(translated, notTranslated);
    }

    record TranslationResult(List<String> translated, List<String> notTranslated) {
    }

    @Value
    @RequiredArgsConstructor
    public static class TranslationException extends AppIllegalStateException {

        List<ObjectName> objectNames;

    }

}
