package machinum.controller;

import com.fasterxml.jackson.annotation.JsonView;
import machinum.controller.ChapterHistoryController.ChapterInfoViews.Public;
import machinum.model.ChapterHistory;
import machinum.service.ChapterHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static machinum.listener.ChapterEntityListener.TRACKED_FIELDS;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chapters")
public class ChapterHistoryController {

    private final ChapterHistoryService historyService;


    /**
     * Get all patches for a specific field of a chapter
     *
     * @param chapterId ID of the chapter
     * @param fieldName Field name (text or translatedText)
     * @return List of JSON patches
     */
    @JsonView(Public.class)
    @GetMapping("/{chapterId}/history/{fieldName}")
    public ResponseEntity<List<ChapterHistory>> getFieldPatches(@PathVariable("chapterId") String chapterId,
                                                                @PathVariable("fieldName") String fieldName) {
        validateFieldName(fieldName);
        List<ChapterHistory> patches = historyService.getPatches(chapterId, fieldName);

        return ResponseEntity.ok(patches);
    }

    /**
     * Get content of a specific field at a point in time
     *
     * @param chapterId ID of the chapter
     * @param fieldName Field name (text or translatedText)
     * @param point     Point in history to retrieve content for
     * @return Content of the field at the specified time
     */
    @GetMapping("/{chapterId}/history/{fieldName}/at")
    public ResponseEntity<String> getContentAtPoint(
            @PathVariable("chapterId") String chapterId,
            @PathVariable("fieldName") String fieldName,
            @RequestParam("number") Integer point) {

        validateFieldName(fieldName);
        String content = historyService.rebuildContentAtPoint(chapterId, fieldName, point);

        return ResponseEntity.ok(content);
    }

    /**
     * Validate that the field name is either "text" or "translatedText"
     *
     * @param fieldName Field name to validate
     * @throws IllegalArgumentException if field name is invalid
     */
    private void validateFieldName(String fieldName) {
        if (!TRACKED_FIELDS.contains(fieldName)) {
            throw new IllegalArgumentException("Field name must be tracked");
        }
    }

    public static class ChapterInfoViews {

        public static class Public {
        }

        public static class Internal extends Public {
        }

    }

}
