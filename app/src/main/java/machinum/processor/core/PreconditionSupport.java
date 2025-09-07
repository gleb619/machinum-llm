package machinum.processor.core;

import machinum.util.TextUtil;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public interface PreconditionSupport {

    default String requireNotEmpty(String string) {
        if (TextUtil.isEmpty(string)) {
            throw new LengthValidationException("String can't be empty");
        }

        return string;
    }

    default AssistantContext.Result requiredNotEmpty(AssistantContext context,
                                                     Function<AssistantContext, AssistantContext.Result> processor) {
        return requiredAtLeast(context, processor, AssistantContext::getText, AssistantContext.Result::result, 0.01);
    }

    default AssistantContext.Result requiredAtLeast70Percent(AssistantContext context,
                                                             Function<AssistantContext, AssistantContext.Result> processor) {
        return requiredAtLeast(context, processor, AssistantContext::getText, AssistantContext.Result::result, 0.7);
    }

    default AssistantContext.Result requiredAtLeast80Percent(AssistantContext context,
                                                             Function<AssistantContext, AssistantContext.Result> processor) {
        return requiredAtLeast(context, processor, AssistantContext::getText, AssistantContext.Result::result, 0.8);
    }

    default AssistantContext.Result requiredAtLeast90Percent(AssistantContext context,
                                                             Function<AssistantContext, AssistantContext.Result> processor) {
        return requiredAtLeast(context, processor, AssistantContext::getText, AssistantContext.Result::result, 0.9);
    }

    /**
     * Processes the input string using the provided processing function.
     * Throws an exception if the processed string's length is less than given percent of the original.
     *
     * @param inputFn  The function to extract an input string to process.
     * @param outputFn
     * @param percent  The percent of changes in text length
     * @return The processed string if validation passes.
     * @throws LengthValidationException If the processed string's length is less than 90% of the original.
     */
    default AssistantContext.Result requiredAtLeast(AssistantContext context,
                                                    Function<AssistantContext, AssistantContext.Result> processor,
                                                    Function<AssistantContext, String> inputFn,
                                                    Function<AssistantContext.Result, String> outputFn,
                                                    double percent) {
        String input = inputFn.apply(context);
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("Input string cannot be null or empty.");
        }


        // Process the string
        var resultContext = processor.apply(context);
        var processedString = outputFn.apply(resultContext);

        // Calculate the length ratio
        double lengthRatio = (double) processedString.length() / input.length();

        // Validate the length
        if (lengthRatio < percent) {
            context.addResultHistory(processedString);
            LoggerFactory.getLogger(PreconditionSupport.class).warn("The final text size is smaller than expected: {} <> {}", processedString.length(), input.length());

            throw new LengthValidationException(
                    String.format("Processed string length (%d) is less than given percentage %d%% of the original length (%d).",
                            processedString.length(), (int) (percent * 100), input.length())
            );
        }

        return resultContext;
    }

    class LengthValidationException extends RuntimeException {

        public LengthValidationException(String message) {
            super(message);
        }

    }

}
