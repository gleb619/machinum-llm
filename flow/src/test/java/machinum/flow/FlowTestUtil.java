package machinum.flow;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FlowTestUtil {

    /**
     * This method overcomes the issue with the original Mockito.spy when passing a lambda which fails with an error
     * saying that the passed class is final.
     */
    @SuppressWarnings("unchecked")
    public static <T, P extends T> P spyLambda(Class<T> lambdaType, P lambda) {
        return (P) mock(lambdaType, delegatesTo(lambda));
    }

}
