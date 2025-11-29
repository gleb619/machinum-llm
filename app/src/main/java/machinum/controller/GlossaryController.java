package machinum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.controller.core.ControllerTrait;
import machinum.model.ChapterGlossary;
import machinum.service.ChapterGlossaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GlossaryController implements ControllerTrait {

    private final ChapterGlossaryService chapterGlossaryService;

    @GetMapping("/api/glossary/{glossaryId}")
    public ResponseEntity<ChapterGlossary> getGlossaryById(@PathVariable("glossaryId") String glossaryId) {
        log.info("Getting glossary by ID: {}", glossaryId);
        return Optional.ofNullable(chapterGlossaryService.getById(glossaryId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}
