package machinum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import machinum.service.TTSRestClient.Metadata;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static machinum.config.Holder.of;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TTSRestClientTest {

    TTSRestClient tTSRestClient;

    @Mock
    HttpClient httpClient;
    @Mock
    ObjectMapper objectMapper;
    @Mock
    HttpResponse httpResponse;

    @BeforeEach
    void setUp() {
        tTSRestClient = new TTSRestClient("http://ttsServiceUrl", of(httpClient), of(objectMapper));
    }

    @Test
    void testGenerate() throws IOException, InterruptedException {
        String awaitedResult = "test";
        byte[] awaitedBytes = awaitedResult.getBytes(StandardCharsets.UTF_8);

        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofByteArray())))
                .thenReturn(httpResponse);
        when(objectMapper.writeValueAsString(any(Metadata.class)))
                .thenReturn("metadata");
        when(httpResponse.statusCode())
                .thenReturn(HttpStatus.OK.value());
        when(httpResponse.body())
                .thenReturn(awaitedBytes);

        byte[] result = tTSRestClient.generate(new TTSRestClient.TTSRequest("text", "voice", "outputFile", Boolean.TRUE, Boolean.TRUE, "chapterId", "chapterTitle", new byte[]{(byte) 0}, new Metadata("title", "artist", "album", "year", "genre", "language", "track", "publisher", "copyright", "comments")));
        Assertions.assertArrayEquals(awaitedBytes, result);
    }

}
