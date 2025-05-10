package machinum.converter;

import machinum.entity.BookEntity;
import machinum.model.Book;
import machinum.service.BookProcessor;
import machinum.util.TextUtil;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import static machinum.config.Constants.*;

@Mapper(componentModel = "spring")
public interface BookMapper extends BaseMapper<BookEntity, Book> {

}
