package machinum.service;

import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import machinum.model.Book;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinesInfoService {

    private final LinesInfoDao linesInfoDao;
    private final BookService bookService;

    @Synchronized
    @Transactional
    public void recreate(String bookId) {
        linesInfoDao.findOrCreateSettings(bookId);
        int result = linesInfoDao.createMatViews(bookId);
        if (result > 0) {
            var books = bookService.findAll();
            var ids = books.stream()
                    .map(Book::getId)
                    .toList();
            linesInfoDao.recreateLinesInfoView(ids);
        }
    }

}
