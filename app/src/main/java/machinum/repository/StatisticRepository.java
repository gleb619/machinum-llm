package machinum.repository;

import machinum.entity.StatisticEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StatisticRepository extends JpaRepository<StatisticEntity, String> {

    Optional<StatisticEntity> findFirstByDate(LocalDate date);

    List<StatisticEntity> findAllByDate(LocalDate date);

}
