package machinum.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestUtil {

    /**
     * This method overcomes the issue with the original Mockito.spy when passing a lambda which fails with an error
     * saying that the passed class is final.
     */
    @SuppressWarnings("unchecked")
    public static <T, P extends T> P spyLambda(Class<T> lambdaType, P lambda) {
        return (P) mock(lambdaType, delegatesTo(lambda));
    }

    /**
     * Creates a ZIP archive in memory containing two files: metadata.json and audio.mp3.
     *
     * @param items The records with zip entry content.
     * @return A byte array representing the complete ZIP archive.
     * @throws IOException if an I/O error occurs during ZIP creation.
     */
    public static byte[] createZipArchive(ZipItem... items) throws IOException {
        // Use ByteArrayOutputStream to build the ZIP file in memory.
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (ZipItem item : items) {
                // --- Add the JSON file to the ZIP ---
                // Create a new entry in the ZIP file.
                ZipEntry jsonEntry = new ZipEntry(item.name());
                zos.putNextEntry(jsonEntry);

                // Write the JSON content to the ZIP entry.
                zos.write(item.content());

                // Close the current entry to finish writing to it.
                zos.closeEntry();
            }

            // The ZipOutputStream is automatically closed by the try-with-resources statement.
            // This is important as it finalizes the ZIP archive structure.
            // We must call finish() or close() on ZipOutputStream before getting the bytes.
            zos.finish();

            // Return the raw byte data of the generated ZIP file.
            return baos.toByteArray();
        }
    }

    public record ZipItem(String name, byte[] content) {
    }

}
