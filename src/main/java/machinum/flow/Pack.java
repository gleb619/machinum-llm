package machinum.flow;

import lombok.*;

import java.util.function.Function;

//Target name for this class was 'package', but it's a keyword :(
@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Pack<I, T> {

    private I item;

    private FlowArgument<T> argument;

    public static <A, B> Function<FlowContext<A>, Pack<A, B>> anArgument(Function<FlowContext<A>, FlowArgument<B>> parser) {
        return ctx -> Pack.<A, B>builder()
                .item(ctx.getCurrentItem())
                .argument(parser.apply(ctx))
                .build();
    }

}
