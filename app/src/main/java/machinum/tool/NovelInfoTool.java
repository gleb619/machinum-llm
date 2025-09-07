package machinum.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Deprecated
@Slf4j
@Component
@RequiredArgsConstructor
public class NovelInfoTool {

    //TODO add another server for ai call
    private final ChatClient aiClient;
    private final VectorStore vectorStore;

    @Tool(name = "queryNovelInfo", description = """
            Answer questions related to web novels using vector store data. If no data found return 'No data'
            """)
    public String queryNovelInfo(String question) {
//    public String queryNovelInfo(@ToolParam(description = "Formal question for vector store(no more that 10 words)") String question) {
        log.debug("Got request for: {}", question);
        var searchResults = vectorStore.similaritySearch(question);

        if (searchResults.isEmpty()) {
            return "No data";
        }

        StringBuilder context = new StringBuilder();
        searchResults.forEach(result -> context.append(result.getText()).append("\n---\n"));

        String prompt = """
                    Based on the following context, answer the question:\s
                    %s
                
                    Question:\s
                    %s
                """.formatted(context, question);

        String content = aiClient.prompt(prompt).call().content();

        log.debug("Got response: {}", content);

        return content;
    }


    @RequiredArgsConstructor
    public static class Fn implements Function<String, String> {

        private final NovelInfoTool novelInfoTool;

        @Override
        public String apply(String s) {
            return novelInfoTool.queryNovelInfo(s);
        }

    }

}
