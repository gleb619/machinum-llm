package machinum.extract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.difflib.DiffUtils;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;
import com.github.difflib.text.DiffRowGenerator;
import machinum.extract.util.PatchDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DiffRowGeneratorTest {

    ObjectMapper mapper = new ObjectMapper();

    DiffRowGenerator generator = DiffRowGenerator.create()
            .showInlineDiffs(true)
            .inlineDiffByWord(true)
            .oldTag(f -> "~")
            .newTag(f -> "**")
            .build();

    @BeforeEach
    void setUp() {
        mapper.registerModule(new SimpleModule().addDeserializer(Patch.class, new PatchDeserializer()));
    }

    @Test
    void testWorkWithPatch() throws IOException, PatchFailedException {
        List<String> originList = Arrays.asList("This is a test senctence.", "This is the second line.", "And here is the finish.");
        List<String> newList = Arrays.asList("This is a test for diffutils.", "This is the second line.");

        Patch<String> patch = DiffUtils.diff(originList, newList);

        String patchString = mapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(patch);
        Patch<String> parsedPatch = mapper.readValue(patchString, Patch.class);

        assertThat(patch)
                .usingRecursiveComparison()
                .isEqualTo(parsedPatch);

        assertThat(DiffUtils.patch(originList, parsedPatch))
                .isEqualTo(newList);
    }

}
