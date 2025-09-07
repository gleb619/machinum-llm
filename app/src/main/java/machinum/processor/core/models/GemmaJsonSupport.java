package machinum.processor.core.models;

public interface GemmaJsonSupport extends ChatModelSupport {

    default String gemmaParse(String text) {
        return parseJsonFromMarkdown(text);
    }

}
