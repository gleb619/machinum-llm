package machinum.converter;

import machinum.entity.BookEntity;
import machinum.model.Book;
import machinum.repository.BookRepository.BookExportResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BookMapper extends BaseMapper<BookEntity, Book> {

    BookExportResult toExportDto(BookExportResult input);

}
