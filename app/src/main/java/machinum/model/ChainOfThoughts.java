package machinum.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import machinum.exception.AppIllegalStateException;
import machinum.flow.model.Mergeable;
import machinum.processor.core.JsonSupport.JsonDescription;
import machinum.processor.core.JsonSupport.SchemaIgnore;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@JsonDescription("A collection of question and answer pairs.")
public class ChainOfThoughts implements Mergeable<ChainOfThoughts> {

    @NotNull
    @Size(min = 20)
    @Builder.Default
    @JsonDescription("A concise list of the key events, themes, and character actions in the text.")
    private List<@NotNull QuestionAndAnswer> questionsAndAnswers = new ArrayList<>();

    public static ChainOfThoughts createNew() {
        return ChainOfThoughts.builder()
                .build();
    }

    public static ChainOfThoughts fromList(List<String> list) {
        return ChainOfThoughts.builder()
                .questionsAndAnswers(list.stream()
                        .map(s -> {
                            String[] data = s.split("\n");
                            if (data.length != 2) {
                                throw new AppIllegalStateException("Unknown type of CoT found, please check logs: " + s);
                            }
                            return QuestionAndAnswer.builder()
                                    .question(data[0])
                                    .answer(data[1])
                                    .build();
                        })
                        .collect(Collectors.toList())
                )
                .build();
    }

    public List<String> questions() {
        return questionsAndAnswers.stream()
                .map(QuestionAndAnswer::getQuestion)
                .collect(Collectors.toList());
    }

    public List<String> answers() {
        return questionsAndAnswers.stream()
                .map(QuestionAndAnswer::getAnswer)
                .collect(Collectors.toList());
    }

    public List<String> toStringList() {
        return questionsAndAnswers.stream()
                .map(qna -> {
                    String question = qna.getQuestion().trim().replaceAll("[\r\n]", "");
                    String answer = qna.getAnswer().trim().replaceAll("[\r\n]", "");

                    return "%s\n%s".formatted(question, answer);
                })
                .collect(Collectors.toList());
    }

    public Stream<QuestionAndAnswer> stream() {
        return questionsAndAnswers.stream();
    }

    @Override
    public ChainOfThoughts recreate() {
        return this.toBuilder()
                .build();
    }

    @Override
    public ChainOfThoughts merge(ChainOfThoughts other) {
        var uniqueData = new LinkedHashSet<>(questionsAndAnswers);
        uniqueData.addAll(other.getQuestionsAndAnswers());

        return this.toBuilder()
                .questionsAndAnswers(new ArrayList<>(uniqueData))
                .build();
    }

    public boolean listIsEmpty() {
        return size() == 0;
    }

    public int size() {
        return Objects.nonNull(questionsAndAnswers) ? questionsAndAnswers.size() : 0;
    }

    @Data
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @NoArgsConstructor(access = AccessLevel.PUBLIC)
    @JsonDescription("A question and its corresponding answer.")
    public static class QuestionAndAnswer {

        @NotNull
        @NotEmpty
        @JsonDescription("A unique question about the text.")
        private String question;

        @NotNull
        @NotEmpty
        @JsonDescription("A concise answer to the question.")
        private String answer;

        @SchemaIgnore
        private String toolName;

        @Override
        public String toString() {
            return "%s\n%s".formatted(question, answer);
        }

    }

}
