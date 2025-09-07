package machinum.flow.model;

import lombok.*;

import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a pack containing an item and a flow argument.
 *
 * @param <I> the type of the item
 * @param <T> the type of the argument
 */
//Target name for this class was 'package', but it's a keyword :(
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Pack<I, T> {

    private I item;

    private FlowArgument<T> argument;

    /**
     * Creates a new Pack instance using a builder function.
     * @param <I> the type of the item
     * @param <T> the type of the argument
     * @param builderFn the function to configure the builder
     * @return the new Pack instance
     */
    public static <I, T> Pack<I, T> createNew(Function<PackBuilder<I, T>, PackBuilder<I, T>> builderFn) {
        return builderFn.apply(builder()).build();
    }

    /**
     * Creates a function that builds a Pack from a FlowContext using a parser.
     * @param <A> the type of the item in context
     * @param <B> the type of the argument
     * @param parser the function to parse the argument from context
     * @return a function that creates a Pack from FlowContext
     */
    public static <A, B> Function<FlowContext<A>, Pack<A, B>> anArgument(Function<FlowContext<A>, FlowArgument<B>> parser) {
        return ctx -> Pack.<A, B>builder()
                .item(ctx.getCurrentItem())
                .argument(parser.apply(ctx))
                .build();
    }

    /**
     * Checks if the pack is empty.
     * @param <I> the type of the item
     * @param <T> the type of the argument
     * @param pack the pack to check
     * @return true if the pack is null or has null item or empty argument
     */
    public static <I, T> boolean isEmpty(Pack<I, T> pack) {
        return Objects.isNull(pack) || Objects.isNull(pack.getItem()) ||
                Objects.isNull(pack.getArgument()) || pack.getArgument().isEmpty();
    }

}
