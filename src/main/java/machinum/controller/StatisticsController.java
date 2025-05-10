package machinum.controller;

import machinum.model.StatisticsDto;
import machinum.service.StatisticService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

}
