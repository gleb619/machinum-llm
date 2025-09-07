package machinum.converter;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import machinum.entity.BookEntity;
import machinum.model.Book;
import machinum.repository.BookRepository;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-09-07T16:04:18+0500",
    comments = "version: 1.6.3, compiler: javac, environment: Java 21.0.4 (Eclipse Adoptium)"
)
@Component
public class BookMapperImpl implements BookMapper {

    @Override
    public List<BookEntity> toEntity(List<Book> list) {
        if ( list == null ) {
            return null;
        }

        List<BookEntity> list1 = new ArrayList<BookEntity>( list.size() );
        for ( Book book : list ) {
            list1.add( toEntity( book ) );
        }

        return list1;
    }

    @Override
    public List<Book> toDto(List<BookEntity> value) {
        if ( value == null ) {
            return null;
        }

        List<Book> list = new ArrayList<Book>( value.size() );
        for ( BookEntity bookEntity : value ) {
            list.add( toDto( bookEntity ) );
        }

        return list;
    }

    @Override
    public BookEntity toEntity(Book value) {
        if ( value == null ) {
            return null;
        }

        BookEntity.BookEntityBuilder bookEntity = BookEntity.builder();

        bookEntity.id( value.getId() );
        bookEntity.title( value.getTitle() );
        bookEntity.bookState( value.getBookState() );

        return bookEntity.build();
    }

    @Override
    public Book toDto(BookEntity value) {
        if ( value == null ) {
            return null;
        }

        Book.BookBuilder book = Book.builder();

        book.id( value.getId() );
        book.title( value.getTitle() );
        book.bookState( value.getBookState() );

        return book.build();
    }

    @Override
    public BookRepository.BookExportResult toExportDto(BookRepository.BookExportResult input) {
        if ( input == null ) {
            return null;
        }

        String id = null;
        String title = null;
        Long chaptersCount = null;

        id = input.id();
        title = input.title();
        chaptersCount = input.chaptersCount();

        BookRepository.BookExportResult bookExportResult = new BookRepository.BookExportResult( id, title, chaptersCount );

        return bookExportResult;
    }
}
