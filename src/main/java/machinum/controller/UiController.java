package machinum.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiController {

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
        return "chapter";
    }

}
