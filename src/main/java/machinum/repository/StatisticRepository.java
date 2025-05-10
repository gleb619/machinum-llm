package machinum.repository;

import machinum.entity.StatisticEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

@Repository
public interface StatisticRepository extends JpaRepository<StatisticEntity, String> {

    Optional<StatisticEntity> findFirstByDate(LocalDate date);

    default StatisticEntity getCurrent() {
        LocalDate currentDate = LocalDate.now();
        return findFirstByDate(currentDate)
                .orElseGet(() -> {
                    // If no record exists for the current day, create a new one
                    var newItem = StatisticEntity.builder()
                            .build();
                    newItem.setDate(currentDate);
                    newItem.setData(new ArrayList<>());

                    return save(newItem);
                });
    }

}
