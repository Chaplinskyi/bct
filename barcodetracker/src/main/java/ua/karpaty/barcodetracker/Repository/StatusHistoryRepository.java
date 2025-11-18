package ua.karpaty.barcodetracker.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.karpaty.barcodetracker.Dto.MonthlyStatDto;
import ua.karpaty.barcodetracker.Entity.StatusHistory;

import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    List<StatusHistory> findByBarcodeIdOrderByChangeTimeDesc(Long barcodeId);

    @Query(value = "SELECT EXTRACT(YEAR FROM sh.change_time) as \"year\", EXTRACT(MONTH FROM sh.change_time) as \"month\", COUNT(DISTINCT sh.barcode_id) as \"count\" " +
            "FROM status_history sh " +
            "WHERE sh.new_status = 'out' AND sh.change_time >= :startDate " +
            "GROUP BY EXTRACT(YEAR FROM sh.change_time), EXTRACT(MONTH FROM sh.change_time) " +
            "ORDER BY \"year\", \"month\"", nativeQuery = true)
    List<MonthlyStatDto> getMonthlyDiscardStats(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT sh FROM StatusHistory sh JOIN FETCH sh.barcode b ORDER BY sh.changeTime DESC")
    List<StatusHistory> findRecentWithBarcode(Pageable pageable);
}
