package ua.karpaty.barcodetracker.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.karpaty.barcodetracker.Entity.ImportBatch;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    // Метод для отримання останнього імпорту
    Optional<ImportBatch> findTopByOrderByIdDesc();

    // Метод для отримання історії імпортів
    List<ImportBatch> findAllByOrderByIdDesc();
}