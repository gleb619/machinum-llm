package machinum.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static machinum.config.Constants.*;

@Deprecated
@Slf4j
@Component
public class Chatbot {

    private final String template = """
            You are assistant, your goal is to ensure that the translated content maintains the original meaning, tone, and style of
            the novel, while also addressing any linguistic nuances that may arise during the translation process. 
            
            Editing Suggestions:  
            Consistency: Ensure that character names, terminology, and specific phrases remain consistent throughout
            the novel. This includes verifying that any unique terms or names are uniformly translated and used. 
            
            Cultural Nuances:
            Be mindful of cultural references or idiomatic expressions that may not directly translate into Russian. Adapt these
            expressions appropriately to maintain the intended meaning and impact. 
            
            Dialogue Naturalness: Review dialogues to ensure
            they sound natural in Russian, preserving the characters' personalities and the original tone of their conversations.
            Pay attention to speech patterns and colloquialisms that may need adjustment. 
            
            Contextual Accuracy: Utilize the RAG
            system to cross-reference the English knowledge base when encountering ambiguous or unclear segments in the Russian
            translation. This will help in maintaining the accuracy and integrity of the original content. 
            
            Grammar and Syntax:
            Thoroughly check for grammatical errors or awkward sentence structures in the Russian text. Ensure that the syntax
            aligns with standard Russian language conventions. Emotional Tone: Preserve the emotional undertones present in the
            original English version. Ensure that the translated text conveys the same feelings and atmospheres intended by the
            author. 
            
            Feedback Integration: Be open to feedback from native Russian readers to refine translations and
            interpretations, enhancing the overall quality of the novel. 
            
            By adhering to these guidelines, you will contribute to a
            high-quality Russian version of "Generic" that resonates well with the target audience while staying true to
            the original work.
            
            Chapters:
            {chapters}
            """;
    private final ChatClient aiClient;
    private final VectorStore vectorStore;

    Chatbot(ChatClient aiClient, VectorStore vectorStore) {
        this.aiClient = aiClient;
        this.vectorStore = vectorStore;
    }

    private static String prepareChapters(List<Document> listOfSimilarDocuments) {
        var chapters = listOfSimilarDocuments
                .stream()
                .peek(document -> document.setContentFormatter(DefaultContentFormatter.builder()
                        .withExcludedInferenceMetadataKeys(CHAPTER_KEYWORD, FILE_KEYWORD, TITLE_KEYWORD)
                        .build()))
                .map(document -> document.getFormattedContent(MetadataMode.INFERENCE))
                .collect(Collectors.joining(System.lineSeparator()));
        return chapters;
    }

    public String chat(String message) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression expression = builder.eq("file", "chapter_2").build();
        var listOfSimilarDocuments = this.vectorStore.similaritySearch(SearchRequest
                .builder()
                .query(message)
                .filterExpression(expression)
                .build()
        );

        SearchRequest query2 = SearchRequest.builder()
                .similarityThresholdAll()
                .filterExpression(new FilterExpressionTextParser().parse("chapter == 2"))
                .build();

        var listOfSimilarDocuments2 = this.vectorStore.similaritySearch(query2);

        System.out.println("Chatbot.chat: " + listOfSimilarDocuments2);

        var chapters = prepareChapters(listOfSimilarDocuments);
        var systemMessage = new SystemPromptTemplate(this.template)
                .createMessage(Map.of("chapters", chapters));

        log.info("Prepare message: {}", systemMessage.getText());

        var userMessage = new UserMessage(message);
        var prompt = new Prompt(List.of(systemMessage, userMessage));
        var aiResponse = aiClient.prompt(prompt);

        return aiResponse.call().content();
    }

}
