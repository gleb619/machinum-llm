package machinum.controller;

import machinum.model.Line;
import machinum.service.LineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Controller class for handling HTTP requests related to Lines.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class LineController {

    private final LineService service;

    /**
     * Retrieves all lines from the database.
     *
     * @return a list of all lines
     */
    @GetMapping("/lines")
    public List<Line> getAllLines() {
        log.info("Received request to get all lines");
        return service.getAllLines();
    }

    /**
     * Retrieves lines associated with a specific book by its ID.
     *
     * @param bookId the ID of the book
     * @return a list of lines for the specified book
     */
    @GetMapping("/books/{bookId}/lines")
    public List<Line> getLinesByBookId(@PathVariable("bookId") String bookId) {
        log.info("Received request to get lines by book ID: {}", bookId);
        return service.getLinesByBookId(bookId);
    }

    /**
     * Retrieves lines associated with a specific book by its ID.
     *
     * @return a list of lines for the specified book
     */
    @PostMapping("/books/{bookId}/lines/similar")
    public List<Line> findSimilarForBook(@PathVariable("bookId") String bookId, @RequestBody FindSimilarRequest request) {
        log.info("Received request to get similar lines for bookId: '{}', line={}", bookId, request.line());
        return service.findSimilarForBook(bookId, request);
    }

    /**
     * Retrieves lines associated with a specific chapter by its ID.
     *
     * @return a list of lines for the specified chapter
     */
    @PostMapping("/chapters/{chapterId}/lines/similar")
    public List<Line> findSimilarForChapter(@PathVariable("chapterId") String chapterId, @RequestBody FindSimilarRequest request) {
        log.info("Received request to get similar lines for chapterId: '{}', line={}", chapterId, request.line());
        return service.findSimilarForChapter(chapterId, request);
    }

    /**
     * Retrieves lines associated with a specific chapter by its ID.
     *
     * @param chapterId the ID of the chapter
     * @return a list of lines for the specified chapter
     */
    @GetMapping("/chapters/{chapterId}/lines")
    public List<Line> getLinesByChapterId(@PathVariable("chapterId") String chapterId) {
        log.info("Received request to get lines by chapter ID: {}", chapterId);
        return service.getLinesByChapterId(chapterId);
    }

    /**
     * Deletes multiple lines from the database by their IDs.
     *
     * @param request a list of IDs of the lines to delete
     */
    @DeleteMapping("/lines")
    public ResponseEntity<Void> removeAllLines(@RequestBody RemoveLineRequest request) {
        log.info("Received request to remove all lines with IDs: {}", request.ids());
        service.removeAllLines(request);

        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes a line from the database by its ID.
     *
     * @param request the ID of the line to delete
     */
    @DeleteMapping("/lines/{lineId}")
    public ResponseEntity<Void> removeLine(@PathVariable("lineId") String lineId, @RequestBody RemoveLineRequest request) {
        if (!lineId.equals(request.lineId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        log.info("Received request to delete line with ID: {}", request.lineId());
        service.deleteLine(request);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/lines/{lineId}")
    public ResponseEntity<Void> updateChapterLine(
            @PathVariable("lineId") String lineId,
            @RequestBody Line updatedLine) {
        log.info("Updating line: {}", lineId);

        // Ensure the path ID matches the line ID in the payload
        if (!lineId.equals(updatedLine.getId())) {
            return ResponseEntity.badRequest().build();
        }

        service.updateChapterLine(updatedLine);

        return ResponseEntity.ok().build();
    }

    public record FindSimilarRequest(List<String> fields, String line) {
    }

    public record RemoveLineRequest(List<String> fields, List<String> ids) {

        public String lineId() {
            return ids().getFirst();
        }

    }

}