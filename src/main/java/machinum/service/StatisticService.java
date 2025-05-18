package machinum.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import machinum.converter.StatisticMapper;
import machinum.converter.StatisticsMapper;
import machinum.model.Statistic;
import machinum.model.StatisticsDto;
import machinum.repository.StatisticRepository;
import machinum.repository.StatisticsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticService {

    private final StatisticRepository statisticRepository;
    private final StatisticMapper mapper;
    private final StatisticsRepository statisticsRepository;
    private final StatisticsMapper statisticsMapper;

    @Getter
    @Value("${app.run-id}")
    private final String runId;
    @Getter
    @Value("${app.mode}")
    private final String mode;

    @SneakyThrows
    @Transactional
    public Statistic currentStatistic() {
        return mapper.toDto(statisticRepository.getCurrent());
    }

    @Transactional
    public Statistic update(Statistic statistic) {
        var entity = mapper.toEntity(statistic);
        var result = statisticRepository.save(entity);
        return mapper.toDto(result);
    }

    @Transactional(readOnly = true)
    public List<StatisticsDto> getStatisticsUpToDate(LocalDate date) {
        log.info("Fetching statistics up to date: {}", date);
        var statisticsViews = statisticsRepository.findByDate(date, Sort.by("operationDate").descending());
        return statisticsMapper.toDto(statisticsViews);
    }

}
