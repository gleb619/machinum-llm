package machinum.converter;

import machinum.entity.BookEntity;
import machinum.entity.StatisticEntity;
import machinum.model.Book;
import machinum.model.Statistic;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StatisticMapper extends BaseMapper<StatisticEntity, Statistic> {

}
