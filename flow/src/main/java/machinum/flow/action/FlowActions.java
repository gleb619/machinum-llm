package machinum.flow.action;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Utility class for Flow-related operations.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FlowActions {

    /**
     * Retrieves the key immediately following the specified key in a LinkedHashMap.
     *
     * @param map The input LinkedHashMap.
     * @param key The key to find the next key for.
     * @param <K> The type of keys in the map.
     * @param <V> The type of values in the map.
     * @return The next key after the specified key, or null if the key is not found or is the last key.
     */
    public static <K, V> K getNextKey(Map<K, V> map, K key) {
        boolean foundKey = false;

        for (K currentKey : map.keySet()) {
            if (foundKey) {
                return currentKey; // Return the next key
            }
            if (currentKey.equals(key)) {
                foundKey = true; // Mark that the specified key has been found
            }
        }

        return null; // Key not found or it was the last key
    }

    /**
     * Checks if the specified key is the first key in the map.
     *
     * @param map The input map.
     * @param key The key to check.
     * @param <K> The type of keys in the map.
     * @param <V> The type of values in the map.
     * @return true if the key is the first key, false otherwise.
     */
    public static <K, V> boolean isFirstKey(Map<K, V> map, K key) {
        for (K currentKey : map.keySet()) {
            return currentKey.equals(key);
        }
        return false;
    }
}
