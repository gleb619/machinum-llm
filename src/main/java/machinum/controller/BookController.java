package machinum.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import io.micrometer.core.instrument.util.IOUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.controller.core.ControllerTrait;
import machinum.converter.JsonlConverter;
import machinum.model.Book;
import machinum.model.Book.BookState;
import machinum.model.Chapter;
import machinum.model.ObjectName;
import machinum.repository.BookRepository.BookExportResult;
import machinum.service.BookFacade;
import machinum.service.BookService;
import machinum.util.TextUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookController implements ControllerTrait {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy_MM_dd_hh_mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy_MM_dd");

    private final JsonlConverter jsonlConverter;
    private final ObjectMapper mapper;
    private final BookService bookService;
    private final BookFacade bookFacade;

    @GetMapping
    public ResponseEntity<List<Book>> getAllChapters(@RequestParam(name = "query", required = false) String query,
                                                     @RequestParam(name = "bookId", required = false) String bookId,
                                                     @RequestParam(name = "page", defaultValue = "0") int page,
                                                     @RequestParam(name = "size", defaultValue = "10") int size) {
        var pageRequest = PageRequest.of(page, size);
        Page<Book> result;

        if (Objects.nonNull(bookId)) {
            List<Book> book = List.of(bookService.getById(bookId));
            result = new PageImpl<>(book);
        } else if (TextUtil.isNotEmpty(query) && TextUtil.isNotEmpty(query)) {
            result = bookService.findByCriteria(query, pageRequest);
        } else {
            result = bookService.getAllBooks(pageRequest);
        }

        return pageResponse(result);
    }

    @SneakyThrows
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadBook(@RequestParam("file") MultipartFile file,
                                                          @RequestParam("fileName") String fileName,
                                                          @RequestParam(value = "overwrite", defaultValue = "false") Boolean overwrite) {
        log.debug("Got request to save book: {}", fileName);

        List<Chapter> chapters;
        if (file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
            // Handle ZIP file
            chapters = processZipFile(file.getInputStream());
        } else if (file.getOriginalFilename().toLowerCase().endsWith(".jsonl")) {
            // Handle JSONL file directly
            var text = IOUtils.toString(file.getInputStream());
            chapters = jsonlConverter.convert(text);
        } else {
            log.debug("Unknown type of file: {}", file.getOriginalFilename());
            return ResponseEntity.badRequest().build();
        }

        var saved = bookFacade.save(overwrite, Book.builder()
                .title(fileName)
                .chapters(chapters.stream()
                        .peek(c -> c.setId(null))
                        .peek(c -> c.setBookId(null))
                        .collect(Collectors.toList()))
                .build());

        return new ResponseEntity<>(Map.of("id", saved.getId()), HttpStatus.OK);
    }

    @SneakyThrows
    @PostMapping("/{id}/upload/translation")
    public ResponseEntity<Void> importTranslation(@PathVariable("id") String bookId,
                                                  @RequestParam("file") MultipartFile file,
                                                  @RequestParam("fileName") String fileName) {
        log.debug("Got request to import translations for book: {}, file={}", bookId, fileName);

        List<Chapter> chapters;
        if (file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
            // Handle ZIP file
            chapters = processZipFile(file.getInputStream());
        } else if (file.getOriginalFilename().toLowerCase().endsWith(".jsonl")) {
            // Handle JSONL file directly
            var text = IOUtils.toString(file.getInputStream());
            chapters = jsonlConverter.convert(text);
        } else {
            log.debug("Unknown type of file: {}", file.getOriginalFilename());
            return ResponseEntity.badRequest().build();
        }

        bookFacade.importTranslation(Book.builder()
                .id(bookId)
                .title(fileName)
                .chapters(chapters)
                .build());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{bookId}/download/glossary-translation")
    public ResponseEntity<byte[]> exportGlossaryTranslation(@PathVariable("bookId") String bookId) {
        try {
            // Create CSV content
            var names = bookFacade.exportGlossaryTranslation(bookId);
            if (names.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            var dataBytes = exportToCsv(names);

            // Set response headers
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "glossary_%s.csv".formatted(LocalDateTime.now().format(DATETIME_FORMATTER)));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(dataBytes);
        } catch (IOException e) {
            log.error("Error exporting chapters", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @SneakyThrows
    @PostMapping("/{id}/upload/glossary-translation")
    public ResponseEntity<Void> importGlossaryTranslation(@PathVariable("id") String bookId,
                                                          @RequestParam("file") MultipartFile file,
                                                          @RequestParam("fileName") String fileName) {
        log.debug("Got request to import glossary translations for book: {}, file={}", bookId, fileName);

        List<ObjectName> names;
        if (file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            // Handle Csv file
            names = processGlossaryCsvFile(file.getInputStream());
        } else if (file.getOriginalFilename().toLowerCase().endsWith(".json")) {
            // Handle JSON file directly
            names = List.of(mapper.readValue(file.getInputStream(), ObjectName[].class));
        } else {
            log.debug("Unknown type of file: {}", file.getOriginalFilename());
            return ResponseEntity.badRequest().build();
        }

        bookFacade.importGlossaryTranslation(bookId, names);

        return ResponseEntity.noContent().build();
    }

    @SneakyThrows
    @PostMapping("/{id}/upload/chapters")
    public ResponseEntity<Void> importChapter(@PathVariable("id") String bookId,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestParam("fileName") String fileName) {
        log.debug("Got request to import chapters for book: {}, file={}", bookId, fileName);

        List<Chapter> chapters;
        if (file.getOriginalFilename().toLowerCase().endsWith(".json")) {
            // Handle JSON file directly
            chapters = List.of(mapper.readValue(file.getInputStream(), Chapter[].class));
        } else if (file.getOriginalFilename().toLowerCase().endsWith(".jsonl")) {
            // Parse from jsonl
            var text = IOUtils.toString(file.getInputStream());
            chapters = jsonlConverter.convert(text);
        } else {
            log.debug("Unknown type of file: {}", file.getOriginalFilename());
            return ResponseEntity.badRequest().build();
        }

        bookFacade.importChaptersText(bookId, chapters);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{bookId}/download")
    public ResponseEntity<byte[]> exportChapters(@PathVariable("bookId") String bookId) {
        try {
            // Create JSONL content
            var chapters = bookFacade.loadBookChapters(bookId);
            if (chapters.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            var jsonlContent = jsonlConverter.toJsonl(chapters.stream()
                    .peek(c -> c.setId(null))
                    .peek(c -> c.setBookId(null))
                    .collect(Collectors.toList()));

            // Create ZIP file with JSONL content
            var zipBytes = createZipFile(jsonlContent);

            // Set response headers
            var headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "chapters_%s.zip".formatted(LocalDateTime.now().format(DATETIME_FORMATTER)));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(zipBytes);
        } catch (IOException e) {
            log.error("Error exporting chapters", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @SneakyThrows
    @GetMapping("/{id}/chapters/ready")
    public ResponseEntity<String> readyChapters(@PathVariable("id") String id,
                                                @RequestParam(name = "from", required = false) Integer fromChapterNumber,
                                                @RequestParam(name = "to", required = false) Integer toChapterNumber) {
        log.debug("Got request to return a book: {}", id);

        List<Chapter> chapters;
        if (Objects.nonNull(fromChapterNumber) && Objects.nonNull(toChapterNumber)) {
            chapters = bookFacade.loadReadyChapters(id, fromChapterNumber, toChapterNumber);
        } else {
            chapters = bookFacade.loadReadyChapters(id);
        }

        var jsonl = jsonlConverter.toJsonl(chapters);

        return ResponseEntity
                .ok()
//                application/json-lines
//                text/jsonl
//                application/jsonl+json
//                application/jsonl
                .contentType(new MediaType("application", "jsonl+json"))
                .body(jsonl);
    }

    @SneakyThrows
    @DeleteMapping("/{id}")
    public ResponseEntity<String> removeBook(@PathVariable("id") String id) {
        log.debug("Got request to remove book: {}", id);

        bookService.remove(id);

        return ResponseEntity
                .noContent()
                .build();
    }

    @GetMapping("/titles")
    public ResponseEntity<List<BookExportResult>> getBooksForExport(@RequestParam(name = "page", defaultValue = "0") int page,
                                                                    @RequestParam(name = "size", defaultValue = "10") int size) {
        var result = bookService.getBooksForExport(page, size);

        return result.isEmpty() ? ResponseEntity.noContent().build() : withCacheControl(result);
    }

    @GetMapping("/{id}/state")
    public ResponseEntity<BookState> getBookState(@PathVariable("id") String id) {
        BookState bookState = bookService.getBookState(id);
        return ResponseEntity.ok(bookState);
    }

    @PatchMapping("/{id}/state")
    public ResponseEntity<Void> updateBookState(@PathVariable("id") String id, @RequestBody BookState bookState) {
        bookService.updateBookState(id, bookState);
        return ResponseEntity.noContent().build();
    }

    /* ============= */

    private List<Chapter> processZipFile(InputStream inputStream) throws IOException {
        try (var zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.getName().toLowerCase().endsWith(".jsonl")) {
                    var text = IOUtils.toString(zipInputStream);
                    return jsonlConverter.convert(text);
                }
            }
        }

        return new ArrayList<>();
    }

    private byte[] createZipFile(String jsonlContent) throws IOException {
        var baos = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(baos)) {
            // Add JSONL file to ZIP
            var entry = new ZipEntry("chapters_%s.jsonl".formatted(LocalDate.now().format(DATE_FORMATTER)));
            zos.putNextEntry(entry);

            // Write JSONL content
            zos.write(jsonlContent.getBytes(StandardCharsets.UTF_8));

            // Close the entry
            zos.closeEntry();
        }

        return baos.toByteArray();
    }

    @SneakyThrows
    private List<ObjectName> processGlossaryCsvFile(InputStream inputStream) throws IOException {
        try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream))) {
            return reader.readAll().stream()
                    .skip(1)
                    .map(row -> ObjectName.builder()
                            .name(row[0])
                            .build()
                            .ruName(row[1]))
                    .collect(Collectors.toList());
        }
    }

    private byte[] exportToCsv(List<ObjectName> objects) throws IOException {
        var stringWriter = new StringWriter();
        try (CSVWriter writer = new CSVWriter(stringWriter)) {
            // Write header
            writer.writeNext(new String[]{"name", "ruName", "category", "description"});

            // Map objects to string arrays and write
            objects.stream()
                    .map(obj -> new String[]{obj.getName(), obj.optionalRuName().orElse(""), obj.getCategory(), obj.getDescription()})
                    .forEach(writer::writeNext);
        }

        return stringWriter.toString().getBytes(StandardCharsets.UTF_8);
    }

}
