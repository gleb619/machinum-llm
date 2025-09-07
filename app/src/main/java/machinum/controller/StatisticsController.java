package machinum.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import machinum.model.Statistic;
import machinum.model.StatisticsDto;
import machinum.service.StatisticService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticService statisticsService;

    @GetMapping
    public ResponseEntity<List<StatisticsDto>> getStatistics(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("Received request for statistics up to date: {}", date);
        var statistics = statisticsService.getStatisticsUpToDate(date);
        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Statistic> getById(@PathVariable("id") String id) {
        log.info("Received request for statistic by id: {}", id);
        var statistic = statisticsService.getById(id);
        return ResponseEntity.ok(statistic);
    }

}
