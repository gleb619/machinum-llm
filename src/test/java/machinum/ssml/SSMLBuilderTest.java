package machinum.ssml;

import org.junit.jupiter.api.Test;

import static machinum.ssml.SSMLBuilder.Pitch.LOW;
import static machinum.ssml.SSMLBuilder.Rate.FAST;
import static org.assertj.core.api.Assertions.assertThat;

class SSMLBuilderTest {

    SSMLBuilder sSMLBuilder = new SSMLBuilder();

    @Test
    void testMain() {
        SSMLBuilder ssml = new SSMLBuilder();
        String result = ssml
                .paragraph("Первый параграф.")
                .paragraph("Второй параграф.")
                .sentence("Первое предложение.")
                .sentence("Второе предложение.")
                .prosody("я говорю довольно медленно", "x-slow", "")
                .rate("я говорю довольно быстро", FAST)
                .pitch("а, теперь понижаю тон", LOW)
                .pause("2000ms", "medium")
                .build();

        assertThat(result)
                .isNotEmpty()
                .isEqualTo(//language=xml
                        """
                                <speak>
                                  <p>Первый параграф.</p>
                                  <p>Второй параграф.</p>
                                  <s>Первое предложение.</s>
                                  <s>Второе предложение.</s>
                                  <prosody rate="x-slow">я говорю довольно медленно</prosody>
                                  <prosody rate="fast">я говорю довольно быстро</prosody>
                                  <prosody pitch="low">а, теперь понижаю тон</prosody>
                                  <break time="2000ms" strength="medium"/>
                                </speak>
                                """);
    }

}
