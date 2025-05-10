package machinum.model.core;

import org.springframework.ai.chat.messages.Message;
import org.springframework.data.util.Pair;

public record StringContainer<T>(String representation, T value) {

    public static StringContainer<Void> of(String text) {
        return new StringContainer<>(text, null);
    }

    public static StringContainer<Pair<Message, Message>> of(Message first, Message second) {
        String firstText = first.getText().replaceAll("[\r\n]", " ");
        String secondText = second.getText().replaceAll("[\r\n]", " ");

        return new StringContainer<>(firstText + "\n" + secondText,
                Pair.of(first, second));
    }

    @Override
    public String toString() {
        return representation;
    }

}
