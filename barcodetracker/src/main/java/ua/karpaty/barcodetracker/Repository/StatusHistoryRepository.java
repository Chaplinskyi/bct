package ua.karpaty.barcodetracker.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.karpaty.barcodetracker.Entity.StatusHistory;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    List<StatusHistory> findByBarcodeIdOrderByChangeTimeDesc(Long barcodeId);
}
