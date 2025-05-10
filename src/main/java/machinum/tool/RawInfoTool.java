package machinum.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

import static machinum.config.Constants.*;

@Deprecated
@Slf4j
@Component
@RequiredArgsConstructor
public class RawInfoTool {

    private final ChatClient aiClient;
    private final VectorStore vectorStore;

    @Tool(name = "rawNovelInfo", description = """
            Answer questions related to web novels using vector store data. If no data found return 'No data'
            """)
//    public String queryNovelInfo(@ToolParam(description = "Formal question for vector store(no more that 10 words)") String question, ToolContext toolContext) {
    public String queryNovelInfo(String question, ToolContext toolContext) {
//    public String queryNovelInfo(String question) {
        log.debug("Got request for: {}", question);
        var b = new FilterExpressionBuilder();
        var context = toolContext.getContext();
        var searchResults = vectorStore.similaritySearch(SearchRequest.builder().query(question)
                .filterExpression(b
                        .and(
                                b.eq(DOCUMENT_TYPE_PARAM, RAW_VALUE),
                                b.lte(NUMBER_PARAM, context.get(CURRENT_NUMBER_PARAM))
                        )
                        .build())
                .build());

        if (searchResults.isEmpty()) {
            return "No data";
        }

        var contextRequest = new StringBuilder();
        searchResults.forEach(result -> contextRequest.append(result.getText()).append("\n---\n"));

        String prompt = """
                    Based on the following context, answer the question:\s
                    %s
                    
                    Question:\s
                    %s
                """.formatted(contextRequest, question);

        String content = aiClient.prompt(prompt).call().content();

        log.debug("Got response: {}", content);

        return content;
    }

    @Deprecated
    @Component
    @RequiredArgsConstructor
    public static class RawInfoToolFn implements Function<String, String> {

        private final RawInfoTool rawInfoTool;

        @Override
        @Tool(name = "rawNovelInfo2", description = "Answer some questions related to web novels using RAG")
        public String apply(String s) {
            return rawInfoTool.queryNovelInfo(s, new ToolContext(Map.of()));
        }

    }


}
