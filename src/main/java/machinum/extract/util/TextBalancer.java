package machinum.extract.util;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TextBalancer {

    public static List<String> balanceText(String text, int numChunks) {
        // Step 1: Split the text into paragraphs/sentences
        String[] lines = text.split("[\r\n]", -1); // Keep empty lines (preserve newlines)
        List<String> lineList = new ArrayList<>(List.of(lines));

        // Step 2: Calculate the target size for each chunk
        int totalLength = lineList.stream().mapToInt(s -> s.length() + 1).sum(); // Include newline delimiters
        int targetChunkSize = totalLength / numChunks;
        double allowedDeviation = targetChunkSize * 0.05; // 5% deviation

        // Step 3: Initialize chunks
        List<LocalChunk> chunks = new ArrayList<>();
        for (int i = 0; i < numChunks; i++) {
            chunks.add(new LocalChunk(new ArrayList<>(), 0));
        }

        // Step 4: Distribute lines into chunks
        int currentChunkIndex = 0;
        for (String line : lineList) {
            LocalChunk currentChunk = chunks.get(currentChunkIndex);

            // Add the line to the current chunk
            currentChunk.addLine(line);

            // Check if we need to move to the next chunk
            if (currentChunk.getTotalLength() > targetChunkSize && currentChunkIndex < numChunks - 1) {
                currentChunkIndex++;
            }
        }

        // Step 5: Balance the chunks within the 5% deviation
        for (int i = 0; i < chunks.size() - 1; i++) {
            LocalChunk currentChunk = chunks.get(i);
            LocalChunk nextChunk = chunks.get(i + 1);

            // If the current chunk exceeds the allowed deviation, redistribute
            while (currentChunk.getTotalLength() > targetChunkSize + allowedDeviation &&
                    !nextChunk.getLines().isEmpty()) {
                String lastLine = currentChunk.getLines().get(currentChunk.getLines().size() - 1);
                currentChunk.removeLastLine();
                nextChunk.getLines().add(0, lastLine); // Move the line to the start of the next chunk
                nextChunk.setTotalLength(nextChunk.getTotalLength() + lastLine.length() + 1);
            }
        }

        // Step 6: Convert chunks back to lists of strings
        List<List<String>> result = new ArrayList<>();
        for (LocalChunk chunk : chunks) {
            result.add(chunk.getLines());
        }

        return chunks.stream()
                .map(LocalChunk::text)
                .collect(Collectors.toList());
    }

    @Data
    @AllArgsConstructor
    private static class LocalChunk {

        private List<String> lines;
        private int totalLength;

        public String text() {
            return String.join("\n", lines);
        }

        public void addLine(String line) {
            lines.add(line);
            totalLength += line.length() + 1; // Add 1 for the newline delimiter
        }

        public void removeLastLine() {
            if (!lines.isEmpty()) {
                String removedLine = lines.remove(lines.size() - 1);
                totalLength -= removedLine.length() + 1; // Subtract 1 for the newline delimiter
            }
        }
    }

}