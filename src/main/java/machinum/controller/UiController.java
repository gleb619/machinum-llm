package machinum.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiController {

    @GetMapping({"/index", "/"})
    public String showBookPage(Model model) {
        return "book";
    }

    @GetMapping({"/chapter", "/chapter/"})
    public String showChapterPage(Model model) {
        return "chapter";
    }

}
