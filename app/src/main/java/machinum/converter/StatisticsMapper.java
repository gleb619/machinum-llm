package machinum.converter;

import machinum.entity.StatisticEntity;
import machinum.entity.StatisticsView;
import machinum.model.Statistic;
import machinum.model.StatisticsDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StatisticsMapper extends BaseMapper<StatisticsView, StatisticsDto> {
}
