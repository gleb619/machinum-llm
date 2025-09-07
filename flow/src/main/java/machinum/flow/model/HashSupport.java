package machinum.flow.model;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

public interface HashSupport {

    /**
     * Hashes a string using CRC-32 algorithm and returns the result as a hexadecimal string.
     *
     * @param input the string to hash
     * @return hexadecimal string representation of the CRC-32 hash
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

    List<String> hashValues();

    default String hash() {
        return hashStringWithCRC32(
                String.join(";", hashValues())
        );
    }

}
