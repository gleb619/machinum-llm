package machinum.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import machinum.TestApplication;
import machinum.controller.BookOperationController.BookOperationRequest;
import machinum.model.Chapter;
import machinum.model.ChapterHistory;
import machinum.repository.ChapterHistoryRepository;
import machinum.util.TestUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static machinum.listener.ChapterEntityListener.ChapterInfoConstants.CLEAN_TEXT;
import static machinum.listener.ChapterEntityListener.ChapterInfoConstants.TRANSLATED_TEXT;
import static machinum.service.BookProcessor.Operations.COMPLEX_FLOW;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = TestApplication.class,
        properties = {
                "spring.ai.ollama.base-url=${wiremock.server.baseUrl}"
                , "test.init-script-path=sql/init_only_book.sql"
                , "app.logic-splitter.chunk-size=1"
                , "app.flow.batch-size=2"
                , "app.tts-service.url=${wiremock.server.baseUrl}"
                , "app.minio.enabled=true",
        }
)
@EnableWireMock({
        @ConfigureWireMock(filesUnderClasspath = "stubs")
})
class BookProcessorStubTest extends NormalTest {

    @Autowired
    BookProcessor bookProcessor;

    @Autowired
    ChapterService chapterService;

    @Autowired
    StatisticService statisticService;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    ChapterHistoryService chapterHistoryService;

    @Autowired
    ChapterHistoryRepository chapterHistoryRepository;

    @Autowired
    AudioService audioService;

    @Value("classpath:json/book-processor-result.json")
    Resource resultFile;

    @Value("classpath:audio/tts_example.mp3")
    Resource audioResource;

    @Value("classpath:json/audio-metadata.json")
    Resource audioMetadata;

    private String bookId;
    private List<Chapter> awaitedChapters;

    @BeforeEach
    void setTestUp() throws IOException {
        bookId = "00000000-0000-0000-0000-000000000001";
        awaitedChapters = List.of(mapper.readValue(resultFile.getInputStream(), Chapter[].class));

        byte[] audioBytes = Files.readAllBytes(audioResource.getFile().toPath());
        byte[] zipArchive = TestUtil.createZipArchive(
                new TestUtil.ZipItem("audio.mp3", audioBytes),
                new TestUtil.ZipItem("metadata.json", audioMetadata.getContentAsByteArray())
        );

        stubFor(post(urlEqualTo("/api/tts"))
                .withHeader("Content-Type", containing("multipart/form-data"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withBody(zipArchive)
                        .withHeader("Content-Type", "audio/mpeg")));
    }

    @Test
    void testStart() {
        System.out.println("\n-------------\n");
        bookProcessor.doStart(BookOperationRequest.builder()
                .id(bookId)
                .operationName(COMPLEX_FLOW)
                .build());

        var statistics = statisticService.currentStatistics();
        var chapters = new ArrayList<>(chapterService.loadBookChapters(bookId, PageRequest.of(0, 10_000))
                .getContent());
        chapters.sort(Comparator.comparing(Chapter::getNumber));
        var patches = chapterHistoryService.getPatches(chapters.getLast().getId());
        var audioFiles = audioService.getAllByBookId(bookId);

        assertThatJson(chapters)
                .isNotNull()
                .isEqualTo(awaitedChapters);

        assertThat(statistics)
                .isNotNull();

        assertThat(patches)
                .isNotEmpty()
                .hasSize(2)
                .extracting(ChapterHistory::getFieldName)
                .containsExactly(TRANSLATED_TEXT, CLEAN_TEXT);

        assertThat(statistics)
                .isNotEmpty()
                .hasSizeGreaterThanOrEqualTo(15);

        assertThat(statistics.getFirst().getMessages())
                .isNotEmpty()
                .hasSize(3);

        verify(3, postRequestedFor(urlEqualTo("/api/tts")));
        assertThat(audioFiles)
                .isNotEmpty()
                .hasSize(3);
    }

}
