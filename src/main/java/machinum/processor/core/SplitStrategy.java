package machinum.processor.core;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.extract.util.TextBalancer;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static machinum.util.TextUtil.toChunkSize;

public interface SplitStrategy {

    static SplitStrategy defaults() {
        return new SpringStandardSplitter();
    }

    List<String> split(String text);

    interface Balanced {

        static List<String> rebalance(List<String> input) {
            if (input == null || input.isEmpty()) return input;

            int totalLength = input.stream().mapToInt(String::length).sum();
            int averageLength = Math.round((float) totalLength / input.size());

            List<StringBuilder> builders = new ArrayList<>();
            for (String s : input) {
                builders.add(new StringBuilder(s));
            }

            boolean modified;
            do {
                modified = false;
                for (int i = 0; i < builders.size(); i++) {
                    StringBuilder current = builders.get(i);
                    if (current.length() > averageLength) {
                        for (int j = 0; j < builders.size(); j++) {
                            if (i != j && builders.get(j).length() < averageLength) {
                                builders.get(j).append(current.charAt(current.length() - 1));
                                current.deleteCharAt(current.length() - 1);
                                modified = true;
                                break;
                            }
                        }
                    }
                }
            } while (modified);

            List<String> result = new ArrayList<>();
            for (StringBuilder sb : builders) {
                result.add(sb.toString());
            }
            return result;
        }

        static List<String> overlap(List<String> input, int overlapSize) {
            if (input == null || input.isEmpty() || overlapSize <= 0) return input;

            List<StringBuilder> builders = new ArrayList<>();
            for (String s : input) {
                builders.add(new StringBuilder(s));
            }

            for (int i = 1; i < builders.size(); i++) {
                StringBuilder previous = builders.get(i - 1);
                StringBuilder current = builders.get(i);

                int copyLength = Math.min(overlapSize, current.length());
                previous.append(current.substring(0, copyLength));
            }

            List<String> result = new ArrayList<>();
            for (StringBuilder sb : builders) {
                result.add(sb.toString());
            }
            return result;
        }

    }

    @Slf4j
    class SingleSplitter implements SplitStrategy {

        @Override
        public List<String> split(String text) {
            return List.of(text);
        }

    }

    @Slf4j
    class SpringStandardSplitter extends SpringCustomSplitter {

        public SpringStandardSplitter() {
            super(new TokenTextSplitter(), "Spring Standard");
        }

    }

    @Slf4j
    class SpringMinSplitter extends SpringCustomSplitter {

        public SpringMinSplitter() {
            super(new TokenTextSplitter(600, 400, 3, 10000, true), "Spring Min");
        }

    }

    @Slf4j
    @RequiredArgsConstructor
    class SpringCustomSplitter implements SplitStrategy {

        private final TokenTextSplitter splitter;
        private final String name;

        public SpringCustomSplitter(TokenTextSplitter splitter) {
            this.splitter = splitter;
            this.name = "Spring Custom";
        }

        @Override
        public List<String> split(String text) {
            List<String> result = splitter.apply(List.of(new Document(text))).stream()
                    .map(Document::getText)
                    .collect(Collectors.toList());

            log.debug("Splitting up document via '{}': chunks[{}]={}", name, result.size(), toChunkSize(result));

            return result;
        }

    }

    @Slf4j
    class LinesSplitter extends WhiteSpacesSplitter {

        public LinesSplitter(int maxTokensPerChunk) {
            this(maxTokensPerChunk, "Lines");
        }

        public LinesSplitter(int maxTokensPerChunk, String name) {
            super(maxTokensPerChunk, name);
        }

        @Override
        protected String getRegex() {
            return "[\r\n]";
        }

        @Override
        protected String getDelimiter() {
            return "\n";
        }

    }

    @Slf4j
    @RequiredArgsConstructor
    class WhiteSpacesSplitter implements SplitStrategy {

        public static final int MAX_TOKENS_PER_CHUNK = 2000;

        public final int maxTokensPerChunk;
        public final String name;

        public WhiteSpacesSplitter() {
            this(MAX_TOKENS_PER_CHUNK, "White Spaces");
        }

        @Override
        public List<String> split(String text) {
            List<String> chunks = new ArrayList<>();
            String[] words = text.split(getRegex());
            StringBuilder chunk = new StringBuilder();
            int tokenCount = 0;

            for (String target : words) {
                // Estimate token count for the word (approximated by character length for simplicity)
                int wordTokens = target.length() / 4;  // Rough estimate: 1 token = ~4 characters

                if (tokenCount + wordTokens > maxTokensPerChunk) {
                    chunks.add(chunk.toString());
                    chunk.setLength(0); // Clear the buffer
                    tokenCount = 0;
                }

                appendToChunk(chunk, target);
                tokenCount += wordTokens;
            }

            if (!chunk.isEmpty()) {
                chunks.add(chunk.toString());
            }

            log.debug("Splitting up document via '{}': chunks[{}]={}", name, chunks.size(), toChunkSize(chunks));

            return chunks;
        }

        protected void appendToChunk(StringBuilder chunk, String targetText) {
            chunk.append(targetText).append(getDelimiter());
        }

        protected String getRegex() {
            return "\\s+";
        }

        protected String getDelimiter() {
            return " ";
        }

    }

    @Slf4j
    class BalancedLinesSplitter extends LinesSplitter implements Balanced {

        private final int overlapCharactersSize;

        public BalancedLinesSplitter(int maxTokensPerChunk, int overlapCharactersSize) {
            super(maxTokensPerChunk, "Balanced");
            this.overlapCharactersSize = overlapCharactersSize;
        }

        @Override
        public List<String> split(String text) {
            List<String> result = super.split(text);
            List<String> balanced = Balanced.rebalance(result);
            List<String> output = Balanced.overlap(balanced, overlapCharactersSize);

            return output;
        }

    }

    @Slf4j
    @RequiredArgsConstructor
    class SentenceSplitter implements SplitStrategy {

        private static final Pattern SENTENCE_END = Pattern.compile("[.!?](?:\\s|$)");

        protected final int maxCharactersSize;

        private final String name;

        public SentenceSplitter(int maxCharactersSize) {
            this(maxCharactersSize, "Sentence");
        }

        /**
         * Creates initial chunks based solely on maxChunkSize.
         */
        private static List<String> createRawChunks(String text, int maxChunkSize) {
            List<String> rawChunks = new ArrayList<>();
            int length = text.length();

            for (int i = 0; i < length; i += maxChunkSize) {
                int end = Math.min(i + maxChunkSize, length);
                rawChunks.add(text.substring(i, end));
            }

            return rawChunks;
        }

        /**
         * Reconstructs chunks to ensure sentence integrity.
         */
        private static List<String> reconstructChunks(List<String> rawChunks, int maxChunkSize) {
            List<String> result = new ArrayList<>();
            StringBuilder currentChunk = new StringBuilder();

            for (int i = 0; i < rawChunks.size(); i++) {
                String chunk = rawChunks.get(i);

                // If this is the last chunk, handle differently
                if (i == rawChunks.size() - 1) {
                    handleLastChunk(currentChunk, chunk, result, maxChunkSize);
                    break;
                }

                // Look for sentence endings in current chunk
                Matcher matcher = SENTENCE_END.matcher(chunk);
                int lastSentenceEnd = -1;

                while (matcher.find()) {
                    lastSentenceEnd = matcher.end();
                }

                if (lastSentenceEnd != -1) {
                    // We found a sentence end in current chunk
                    currentChunk.append(chunk, 0, lastSentenceEnd);

                    // If current chunk is getting too big, save it
                    if (currentChunk.length() >= maxChunkSize * 0.8) {
                        result.add(currentChunk.toString());
                        currentChunk = new StringBuilder();
                    }

                    // Start new chunk with remaining text
                    if (lastSentenceEnd < chunk.length()) {
                        currentChunk.append(chunk.substring(lastSentenceEnd));
                    }
                } else {
                    // No sentence end found, look ahead to next chunk
                    String nextChunk = rawChunks.get(i + 1);
                    Matcher nextMatcher = SENTENCE_END.matcher(nextChunk);

                    if (nextMatcher.find()) {
                        int nextEnd = nextMatcher.end();
                        // If adding text until next sentence end won't exceed maxChunkSize
                        if (currentChunk.length() + chunk.length() + nextEnd <= maxChunkSize * 1.2) {
                            currentChunk.append(chunk).append(nextChunk, 0, nextEnd);
                            result.add(currentChunk.toString());
                            currentChunk = new StringBuilder();
                            // Replace next chunk with remaining text
                            rawChunks.set(i + 1, nextChunk.substring(nextEnd));
                        } else {
                            // Force split at current chunk
                            if (currentChunk.length() > 0) {
                                result.add(currentChunk.toString());
                            }
                            result.add(chunk);
                            currentChunk = new StringBuilder();
                        }
                    } else {
                        // No sentence end in next chunk either, force split
                        if (currentChunk.length() > 0) {
                            result.add(currentChunk.toString());
                        }
                        result.add(chunk);
                        currentChunk = new StringBuilder();
                    }
                }
            }

            return result;
        }

        /**
         * Handles the last chunk of text.
         */
        private static void handleLastChunk(StringBuilder currentChunk, String lastChunk,
                                            List<String> result, int maxChunkSize) {
            // If adding last chunk won't exceed maxChunkSize, add it to current
            if (currentChunk.length() + lastChunk.length() <= maxChunkSize) {
                currentChunk.append(lastChunk);
                result.add(currentChunk.toString());
            } else {
                // Add current chunk if not empty
                if (currentChunk.length() > 0) {
                    result.add(currentChunk.toString());
                }
                // Add last chunk
                result.add(lastChunk);
            }
        }

        /**
         * Splits text into chunks by first creating fixed-size chunks and then
         * reconstructing them to preserve sentence integrity.
         *
         * @param text The input text to be split
         * @return List of chunks with preserved sentences
         */
        public List<String> split(@NonNull String text) {
            if (text.trim().isEmpty()) {
                throw new IllegalArgumentException("Text cannot be empty");
            }
            if (maxCharactersSize < 1) {
                throw new IllegalArgumentException("Max chunk size must be positive");
            }

            // Phase 1: Create initial fixed-size chunks
            List<String> rawChunks = createRawChunks(text, maxCharactersSize);

            // Phase 2: Reconstruct chunks to preserve sentences
            List<String> chunks = reconstructChunks(rawChunks, maxCharactersSize);

            log.debug("Splitting up document via '{}': chunks[{}]={}", name, chunks.size(), toChunkSize(chunks));

            return chunks;
        }

    }

    @Slf4j
    @RequiredArgsConstructor
    class BalancedSentenceSplitter implements SplitStrategy {

        protected final int maxCharactersSize;


        @Override
        public List<String> split(String text) {
            return split(text, text.length() / maxCharactersSize);
        }

        public List<String> split(String text, int chunksSize) {
            if (chunksSize <= 1) {
                return List.of(text);
            }

            return TextBalancer.balanceText(text, chunksSize);
        }

    }

}
