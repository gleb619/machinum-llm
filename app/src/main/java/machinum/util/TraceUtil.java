package machinum.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Supplier;

import static machinum.util.JavaUtil.newId;
import static machinum.util.TextUtil.isEmpty;

/**
 * A utility class for tracing operations with unique ray IDs.
 * Uses ThreadLocal to store ray IDs per thread and logs operation execution.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TraceUtil {

    private static final ThreadLocal<String> CURRENT_RAY_ID = ThreadLocal.withInitial(TraceUtil::generateNewRayId);

    public static Optional<String> currentRayId() {
        return Optional.ofNullable(CURRENT_RAY_ID.get());
    }

    /**
     * Gets the current ray ID for the thread.
     *
     * @return the current ray ID
     */
    public static String getCurrentRayId() {
        return CURRENT_RAY_ID.get();
    }

    /**
     * Sets a new ray ID for the current thread.
     *
     * @param rayId the ray ID to set
     */
    public static void setCurrentRayId(String rayId) {
        CURRENT_RAY_ID.set(rayId);
    }

    /**
     * Generates a new ray ID for the current thread.
     *
     * @return the newly generated ray ID
     */
    public static String generateNewRayId() {
        return newId("ray-");
    }

    /**
     * Traces execution of a Runnable operation.
     *
     * @param operationName the name of the operation
     * @param runnable      the operation to execute
     */
    public static void trace(String operationName, Runnable runnable) {
        var ignore = trace(operationName, () -> {
            runnable.run();

            return null;
        });
    }

    /**
     * Traces execution of a Supplier operation and returns its result.
     *
     * @param operationName the name of the operation
     * @param supplier      the operation to execute
     * @param <T>           the type of result
     * @return the result of the supplier
     */
    public static <T> T trace(String operationName, Supplier<T> supplier) {
        var rayId = getCurrentRayId();
        if (isEmpty(rayId)) {
            setCurrentRayId(generateNewRayId());

            try {
                log.trace("[{}] executing", operationName);
                return supplier.get();
            } finally {
                log.trace("[{}] executed", operationName);
                cleanUp();
            }
        } else {
            return supplier.get();
        }
    }

    /**
     * Cleans up resources when the thread is done.
     * Should be called at the end of thread execution.
     */
    public static void cleanUp() {
        CURRENT_RAY_ID.remove();
    }

}
