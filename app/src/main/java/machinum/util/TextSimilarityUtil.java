package machinum.util;

import info.debatty.java.stringsimilarity.Cosine;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TextSimilarityUtil {

    public static List<String> search(List<String> texts, String searchTerm) {
        Map<String, String> textMap = texts.stream()
                .collect(Collectors.toMap(TextUtil.valueOf(Objects::hashCode), Function.identity(), (f, s) -> f));
        var result = search(textMap, searchTerm);

        return new ArrayList<>(result.values());
    }

    public static Map<String, String> search(Map<String, String> texts, String searchTerm) {
        return checkSimilarity(texts, searchTerm);
    }

    public static Map<String, String> checkSimilarity(Map<String, String> texts, String searchTerm) {
        var stringSimilarityFn = new Cosine();

        return texts.entrySet().stream()
                .max(Comparator.comparingDouble(e -> stringSimilarityFn.similarity(searchTerm, e.getValue())))
                .map(entry -> Map.of(entry.getKey(), entry.getValue()))
                .orElse(Collections.emptyMap());
    }

}
