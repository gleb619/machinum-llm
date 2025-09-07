package machinum.flow;

import machinum.config.Constants;
import machinum.flow.argument.FlowArgument;
import machinum.flow.core.FlowContext;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static machinum.flow.action.FlowContextActions.consolidatedContext;
import static machinum.flow.action.FlowContextActions.of;

@ExtendWith(MockitoExtension.class)
class FlowContextTest {
    @Mock
    List<FlowArgument> arguments;

    FlowContext<?> flowContext = of();

    @Spy
    Chapter chapter = Chapter.builder()
            .sourceKey("test1")
            .build();

    @Spy
    Chapter chapterInfo = Chapter.builder()
            .title("test2")
            .build();

    @Test
    void testGlossary() {
        var expectedObjectName = new ObjectName("name",
                "category",
                "description",
                List.of(),
                Map.of("metadata", "metadata"));
        var expectedList = List.of(expectedObjectName);
        var result = AppFlowActions.glossary(flowContext
                .addArgs(AppFlowActions.glossary(expectedList)));

        Assertions.assertEquals(expectedList, result);
    }

    @Test
    void testOldGlossary() {
        var expectedObjectName = new ObjectName("name-old",
                "category-old",
                "description-old",
                List.of(),
                Map.of("metadata-old", "metadata-old"));
        var expectedList = List.of(expectedObjectName);
        var result = AppFlowActions.oldGlossary(flowContext
                .addArgs(AppFlowActions.glossary(expectedList).asObsolete()));

        Assertions.assertEquals(expectedList, result);

    }

    @Test
    void testParseArgument() {
        String hasArg = flowContext
                .addArgs(consolidatedContext("example1"))
                .parseArgument(FlowContext::consolidatedContextArg,
                        FlowArgument::getValue,
                        unused -> Constants.EMPTY_PLACEHOLDER);

        String hasntArg = flowContext
                .parseArgument(FlowContext::consolidatedContextArg,
                        FlowArgument::getValue,
                        unused -> Constants.EMPTY_PLACEHOLDER);

        Assertions.assertEquals("example1", hasArg);
        Assertions.assertEquals(Constants.EMPTY_PLACEHOLDER, hasntArg);
    }

}
