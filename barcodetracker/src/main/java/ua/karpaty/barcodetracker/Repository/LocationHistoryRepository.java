package ua.karpaty.barcodetracker.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.karpaty.barcodetracker.Entity.Barcode;
import ua.karpaty.barcodetracker.Entity.LocationHistory;

import java.util.List;

public interface LocationHistoryRepository extends JpaRepository<LocationHistory, Long> {
    List<LocationHistory> findByBarcodeIdOrderByChangeTimeDesc(Long barcodeId);
}
