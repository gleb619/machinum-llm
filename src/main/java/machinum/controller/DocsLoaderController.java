package machinum.controller;

import machinum.service.DocsLoaderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/docs")
public class DocsLoaderController {

    @Autowired
    private DocsLoaderService docsLoaderService;

    // API endpoint to load documents when called
    @GetMapping("/load")
    public String loadDocuments() {
        return docsLoaderService.loadDocs();
    }
}
