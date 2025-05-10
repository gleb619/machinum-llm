package machinum.extract.util;

import machinum.util.JavaUtil;
import lombok.*;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Arrays.copyOfRange;

@RequiredArgsConstructor
public class NamedEntityRecognizer {

    private static final SimpleTokenizer TOKENIZER = SimpleTokenizer.INSTANCE;

    private final Map<String, NameFinderME> nameFinders;

    @SneakyThrows
    public static NamedEntityRecognizer from(Map<String, String> modelPaths) {
        var nameFinders = new HashMap<String, NameFinderME>();
        for (Map.Entry<String, String> entry : modelPaths.entrySet()) {
            String modelName = entry.getKey();
            String modelPath = entry.getValue();
            try (InputStream modelIn = new FileInputStream(modelPath)) {
                TokenNameFinderModel model = new TokenNameFinderModel(modelIn);
                nameFinders.put(modelName, new NameFinderME(model));
            }
        }

        return new NamedEntityRecognizer(nameFinders);
    }

    /**
     * Perform Named Entity Recognition on the given text using all loaded models.
     *
     * @param text The input text to analyze.
     * @return A list of recognized entities with their positions and model types.
     */
    public List<Entity> recognizeEntities(String text) {
        // Tokenize the input text
        var tokens = TOKENIZER.tokenize(text);

        // Find named entities using each model
        var entities = new ArrayList<Entity>();
        for (Map.Entry<String, NameFinderME> entry : nameFinders.entrySet()) {
            var modelName = entry.getKey();
            var nameFinder = entry.getValue();

            var spans = nameFinder.find(tokens);
            for (var span : spans) {
                var entityText = getCoveredText(tokens, span);
                entities.add(Entity.createNew(b -> b
                        .text(entityText)
                        .type(modelName)
                        .start(span.getStart())
                        .end(span.getEnd())
                ));
            }

            // Clear adaptive data for the current model
            nameFinder.clearAdaptiveData();
        }

        return entities.stream()
                .sorted(Comparator.comparing(Entity::getText))
                .collect(Collectors.toList());
    }

    public List<Entity> recognizeUniqueEntities(String text) {
        return JavaUtil.uniqueBy(recognizeEntities(text), Entity::getText);
    }

    public String getCoveredText(String[] tokens, Span span) {
        return String.join(" ", copyOfRange(tokens, span.getStart(), span.getEnd()));
    }

    /**
     * Inner class to represent an entity with its details.
     */
    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    public static class Entity {

        private String text;
        private String type;
        private int start;
        private int end;

        public static Entity createNew(Function<EntityBuilder, EntityBuilder> creator) {
            return creator.apply(Entity.builder()).build();
        }

    }

}