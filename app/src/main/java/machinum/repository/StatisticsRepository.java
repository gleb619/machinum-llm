package machinum.repository;

import machinum.entity.StatisticsView;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@Deprecated(forRemoval = true)
public interface StatisticsRepository extends JpaRepository<StatisticsView, Long> {

    @Deprecated(forRemoval = true)
    List<StatisticsView> findByDate(LocalDate date, Sort sort);

}
