package machinum.extract;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.flow.FlowContext;
import machinum.flow.FlowContextActions;
import machinum.model.Chapter;
import machinum.service.AudioService;
import machinum.service.MinioService;
import machinum.service.TTSRestClient.Metadata;
import machinum.service.TTSRestClient.TTSRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import static machinum.config.Constants.TRANSLATED_TITLE;
import static machinum.util.TextUtil.toShortDescription;

@Slf4j
@Service
@RequiredArgsConstructor
public class Synthesizer {

    @Value("${app.tts-service.cover-url:none}")
    private final String coverUrl;
    @Value("classpath:/static/android-chrome-192x192.png")
    private final Resource defaultCover;

    private final AudioService audioService;
    private final MinioService minioService;
    private final Metadata chapterMetadata;

    public FlowContext<Chapter> synthesize(FlowContext<Chapter> context) {
        var translatedTitle = context.arg(TRANSLATED_TITLE).stringValue();
        var text = context.translatedText();
        log.debug("Prepare to synthesize: text={}...", toShortDescription(text));

        var chapter = context.getCurrentItem();
        var chapterId = chapter.getId();
        var audioFile = audioService.generate(TTSRequest.builder()
                .text("""
                        %s 
                          
                        %s 
                        """.formatted(translatedTitle, text))
                //.voice("") //Use default one
                .outputFile("%s.mp3".formatted(chapterId))
                .enhance(Boolean.TRUE)
                .returnZip(Boolean.TRUE)
                .chapterId(chapterId)
                .chapterTitle(chapter.getTitle())
                .coverArt(resolveCoverArt())
                //TODO refactor book, take metadata info from there
                .metadata(chapterMetadata.toBuilder()
                        .title(chapter.getTranslatedTitle())
                        .track(String.valueOf(chapter.getNumber()))
                        .build())
                .build());

        return context.rearrange(FlowContext::resultArg, FlowContextActions.result(audioFile));
    }

    @SneakyThrows
    private byte[] resolveCoverArt() {
        if (!"none".equalsIgnoreCase(coverUrl)) {
            if (coverUrl.startsWith("http")) {
                return minioService.downloadContent(coverUrl);
            } else {
                var parts = coverUrl.split("/");
                var url = minioService.getPreSignedUrl(parts[0], parts[1]);
                return minioService.downloadContent(url);
            }
        } else {
            return defaultCover.getContentAsByteArray();
        }
    }

}
