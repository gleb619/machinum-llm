package machinum.processor.core.models;

import java.util.ArrayList;
import java.util.List;

public interface ChatModelSupport {

    String getChatModel();

    default String parseJsonFromMarkdown(String text) {
        List<String> jsonBlocks = new ArrayList<>();
        String startMarker = "```json";
        String endMarker = "```";
        int currentIndex = 0;

        while (currentIndex < text.length()) {
            int itemIndex = text.indexOf(startMarker, currentIndex);
            if (itemIndex == -1) {
                break;
            }

            int endIndex = text.indexOf(endMarker, itemIndex + startMarker.length());
            if (endIndex == -1) {
                break;
            }

            String jsonContent = text.substring(itemIndex + startMarker.length(), endIndex).trim();
            jsonBlocks.add(jsonContent);

            currentIndex = endIndex + endMarker.length();
        }

        return String.join("\n", jsonBlocks);
    }

}
