package machinum.flow.model;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Interface for objects that support hash-based operations.
 * Provides a default implementation for generating hashes from a list of hash values.
 */
public interface HashSupport {

    /**
     * Hashes a string using CRC-32 algorithm and returns the result as a hexadecimal string.
     *
     * @param input the string to hash
     * @return hexadecimal string representation of the CRC-32 hash, or null if input is null
     */
    static String hashStringWithCRC32(String input) {
        if (input == null) {
            return null;
        }

        CRC32 crc = new CRC32();
        crc.update(input.getBytes(StandardCharsets.UTF_8));
        long hash = crc.getValue();

        // Convert to hexadecimal string
        return String.format("%08x", hash);
    }

    /**
     * Returns a list of string values that should be included in the hash calculation.
     * Implementing classes should provide the values that uniquely identify the object.
     *
     * @return a list of strings to be hashed
     */
    List<String> hashValues();

    /**
     * Generates a hash for this object by joining the hash values with semicolons
     * and applying CRC-32 hashing.
     *
     * @return the hash string representation of this object
     */
    default String hash() {
        return hashStringWithCRC32(
                String.join(";", hashValues())
        );
    }

}
