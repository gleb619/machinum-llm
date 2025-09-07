package machinum.extract.util;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ProperNameExtractor {

    private final NamedEntityRecognizer namedEntityRecognizer;

    public List<String> extract(String text) {
        var output = new ArrayList<String>();
        var entities = namedEntityRecognizer.recognizeUniqueEntities(text).stream()
                .map(NamedEntityRecognizer.Entity::getText)
                .toList();
        var names = SimpleProperNameExtractor.extractAllNames(text);

        output.addAll(entities);
        output.addAll(names);

        return output.stream()
                .filter(SimpleProperNameExtractor::wordAllowed)
                .distinct()
                .sorted()
                .toList();
    }

}
