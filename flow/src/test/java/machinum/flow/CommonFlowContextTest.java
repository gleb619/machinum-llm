package machinum.flow;

import machinum.flow.model.FlowArgument;
import machinum.flow.model.FlowContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;

import static machinum.flow.model.helper.FlowContextActions.*;

@ExtendWith(MockitoExtension.class)
class CommonFlowContextTest {

    @Mock
    List<FlowArgument> arguments;

    FlowContext<?> flowContext = of();

    @Test
    void testText() {
        var result = flowContext
                .addArgs(text("example1"))
                .text();
        Assertions.assertEquals("example1", result);
    }

    @Test
    void testContext() {
        var result = flowContext
                .addArgs(context("example2"))
                .chapContext();
        Assertions.assertEquals("example2", result);
    }

    @Test
    void testConsolidatedContext() {
        var result = flowContext
                .addArgs(consolidatedContext("example3"))
                .consolidatedChapContext();
        Assertions.assertEquals("example3", result);
    }

    @Test
    void testProofread() {
        var result = flowContext
                .addArgs(proofread("example4"))
                .proofread();
        Assertions.assertEquals("example4", result);
    }

    @Test
    void testOldText() {
        var result = flowContext
                .addArgs(text("example1-old").asObsolete())
                .oldText();
        Assertions.assertEquals("example1-old", result);
    }

    @Test
    void testOldContext() {
        var result = flowContext
                .addArgs(context("example2-old").asObsolete())
                .oldChapContext();
        Assertions.assertEquals("example2-old", result);
    }

    @Test
    void testOldConsolidatedContext() {
        var result = flowContext
                .addArgs(consolidatedContext("example3-old").asObsolete())
                .oldConsolidatedChapContext();
        Assertions.assertEquals("example3-old", result);
    }

    @Test
    void testOldProofread() {
        var result = flowContext
                .addArgs(proofread("example4-old").asObsolete())
                .oldProofread();
        Assertions.assertEquals("example4-old", result);
    }

    @Test
    void testReplace() {
        var result = flowContext
                .addArgs(text("example1"))
                .replace(FlowContext::textArg, text("example2"))
                .text();
        Assertions.assertEquals("example2", result);
    }

    @Test
    void testRearrange() {
        var context = flowContext
                .addArgs(text("example1"))
                .rearrange(FlowContext::textArg, text("example2"));
        var result = context
                .text();
        var oldResult = context
                .oldText();

        Assertions.assertEquals("example2", result);
        Assertions.assertEquals("example1", oldResult);
    }

    @Test
    void testRearrange2() {
        var context = flowContext
                .addArgs(text("example1"))
                .rearrange(FlowContext::textArg, b -> b.argument(text("example2")));
        var result = context
                .text();
        var oldResult = context
                .oldText();

        Assertions.assertEquals("example2", result);
        Assertions.assertEquals("example1", oldResult);
    }

    @Test
    void testCopy() {
        var result = flowContext.copy(Function.identity());

        Assertions.assertNotSame(result, flowContext);
    }

    @Test
    void testThen() {
        var contextFlow = flowContext
                .addArgs(text("example1"))
                .then(ctx -> ctx.copy(b -> b.argument(context("example2"))));
        var textResult = contextFlow.text();
        var contextResult = contextFlow.chapContext();

        Assertions.assertEquals("example1", textResult);
        Assertions.assertEquals("example2", contextResult);
    }

    @Test
    void testHasArgument() {
        boolean hasArg = flowContext
                .addArgs(consolidatedContext("example1"))
                .hasArgument(FlowContext::consolidatedChapContextArg, arg -> {
                });

        boolean hasntArg = flowContext
                .hasArgument(FlowContext::consolidatedChapContextArg, arg -> {
                });

        Assertions.assertTrue(hasArg);
        Assertions.assertFalse(hasntArg);
    }

    @Test
    void testObsolete() {
        var argWithValue = text("example1");
        var oldArgWithValue = text("example1").asObsolete();

        var emptyArgWithValue = text("example1").toBuilder()
                .value(null)
                .build();
        var emptyOldArgWithValue = text("example1").asObsolete().toBuilder()
                .value(null)
                .build();

        Assertions.assertFalse(argWithValue.isEmpty());
        Assertions.assertFalse(oldArgWithValue.isEmpty());
        Assertions.assertTrue(emptyArgWithValue.isEmpty());
        Assertions.assertTrue(emptyOldArgWithValue.isEmpty());

        Assertions.assertNotSame(emptyArgWithValue.asObsolete(), emptyArgWithValue);
        Assertions.assertSame(emptyOldArgWithValue.asObsolete(), emptyOldArgWithValue);

        Assertions.assertFalse(oldArgWithValue.isEmpty());
        Assertions.assertTrue(oldArgWithValue.asObsolete().isEmpty());
    }

    @Test
    void testRemoveArgs() {
        var context = flowContext
                .addArgs(text("example1"))
                .removeArgs(text("example1"));

        var result = context.hasArgument(FlowContext::textArg);

        Assertions.assertFalse(result);
    }

    @Test
    void testHasArguments() {
        var result = flowContext
                .addArgs(text("example1"), text("example1").asObsolete())
                .hasArguments(FlowContext::textArg, FlowContext::oldTextArg);

        var result2 = flowContext
                .addArgs(text("example1"))
                .hasArguments(FlowContext::textArg, FlowContext::oldTextArg);

        Assertions.assertTrue(result);
        Assertions.assertFalse(result2);
    }
}
