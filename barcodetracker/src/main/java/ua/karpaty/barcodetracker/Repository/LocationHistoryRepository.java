package ua.karpaty.barcodetracker.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ua.karpaty.barcodetracker.Entity.LocationHistory;

import org.springframework.data.domain.Pageable;
import java.util.List;

public interface LocationHistoryRepository extends JpaRepository<LocationHistory, Long> {
    List<LocationHistory> findByBarcodeIdOrderByChangeTimeDesc(Long barcodeId);

    @Query("SELECT lh FROM LocationHistory lh JOIN FETCH lh.barcode b ORDER BY lh.changeTime DESC")
    List<LocationHistory> findRecentWithBarcode(Pageable pageable);
}
