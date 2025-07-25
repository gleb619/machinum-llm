package machinum.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Controller
@RequiredArgsConstructor
public class UiController {

    @Value("${app.minio.console}")
    private final String minioEndpoint;
    @Value("${app.minio.bucketName}")
    private final String minioBucketName;


    @GetMapping({"/index", "/"})
    public String showIndexPage(Model model) {
        return "index";
    }

    @GetMapping({"/books", "/books/"})
    public String showBookPage(Model model) {
        return "book";
    }

    @GetMapping({"/chapters", "/chapters/"})
    public String showChapterPage(Model model) {
        model.addAttribute("minioEndpoint", Base64.getEncoder().encodeToString(minioEndpoint.getBytes(StandardCharsets.UTF_8)));
        model.addAttribute("minioBucket", Base64.getEncoder().encodeToString(minioBucketName.getBytes(StandardCharsets.UTF_8)));

        return "chapter";
    }

}
