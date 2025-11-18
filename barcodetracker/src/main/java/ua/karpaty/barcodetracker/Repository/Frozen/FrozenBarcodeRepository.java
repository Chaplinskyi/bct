package ua.karpaty.barcodetracker.Repository.Frozen;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ua.karpaty.barcodetracker.Entity.Frozen.FrozenBarcode;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Цей репозиторій автоматично підхопиться конфігурацією FrozenDbConfig
@Repository
public interface FrozenBarcodeRepository extends JpaRepository<FrozenBarcode, Long> {

    // Метод для пошуку
    Optional<FrozenBarcode> findByCode(String code);

    // Метод для масової перевірки дублікатів
    List<FrozenBarcode> findByCodeIn(List<String> codes);

    @Query("SELECT f.code FROM FrozenBarcode f WHERE f.code IN :codes")
    Set<String> findExistingCodes(List<String> codes);

    Page<FrozenBarcode> findAll(Pageable pageable);

    // Знайти за датою списання
    Page<FrozenBarcode> findByDateDiscardedBetween(LocalDate start, LocalDate end, Pageable pageable);

    // Знайти за APN
    Page<FrozenBarcode> findByApnContainingIgnoreCase(String apn, Pageable pageable);

    // Знайти за APN та датою
    Page<FrozenBarcode> findByApnContainingIgnoreCaseAndDateDiscardedBetween(String apn, LocalDate start, LocalDate end, Pageable pageable);
}