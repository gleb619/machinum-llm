package machinum.flow;

import lombok.*;

import java.util.Objects;
import java.util.function.Function;

//Target name for this class was 'package', but it's a keyword :(
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Pack<I, T> {

    private I item;

    private FlowArgument<T> argument;

    public static <I, T> Pack<I, T> createNew(Function<PackBuilder<I, T>, PackBuilder<I, T>> builderFn) {
        return builderFn.apply(builder()).build();
    }

    public static <A, B> Function<FlowContext<A>, Pack<A, B>> anArgument(Function<FlowContext<A>, FlowArgument<B>> parser) {
        return ctx -> Pack.<A, B>builder()
                .item(ctx.getCurrentItem())
                .argument(parser.apply(ctx))
                .build();
    }

    public static <I, T> boolean isEmpty(Pack<I, T> pack) {
        return Objects.isNull(pack) || Objects.isNull(pack.getItem()) ||
                Objects.isNull(pack.getArgument()) || pack.getArgument().isEmpty();
    }

}
