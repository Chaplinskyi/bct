package ua.karpaty.barcodetracker.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ua.karpaty.barcodetracker.Dto.MonthlyStatDto;
import ua.karpaty.barcodetracker.Entity.Barcode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BarcodeRepository extends JpaRepository<Barcode, String>, JpaSpecificationExecutor<Barcode> {

    Optional<Barcode> findByCode(String code);

    // Повертає LIST для експорту
    List<Barcode> findByStatusOrderByLastUpdatedDesc(String status);
    // Повертає PAGE для пагінації
    Page<Barcode> findByStatusOrderByLastUpdatedDesc(String status, Pageable pageable);

    // Повертає LIST для експорту
    List<Barcode> findByStatusAndLastUpdatedBetweenOrderByLastUpdatedDesc(
            String status, LocalDateTime start, LocalDateTime end);
    // Повертає PAGE для пагінації
    Page<Barcode> findByStatusAndLastUpdatedBetweenOrderByLastUpdatedDesc(
            String status, LocalDateTime start, LocalDateTime end, Pageable pageable);

    @Query("""
        SELECT b FROM Barcode b
        WHERE b.status = :status
          AND b.lastUpdated BETWEEN :from AND :to
          AND b.apn = :apn
        ORDER BY b.lastUpdated DESC
        """)
        // Повертає LIST для експорту
    List<Barcode> findByStatusAndDateRangeAndApn(
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("apn") String apn);

    @Query("""
        SELECT b FROM Barcode b
        WHERE b.status = :status
          AND b.lastUpdated BETWEEN :from AND :to
          AND b.apn = :apn
        ORDER BY b.lastUpdated DESC
        """)
        // Повертає PAGE для пагінації
    Page<Barcode> findByStatusAndDateRangeAndApn(
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("apn") String apn,
            Pageable pageable);

    // Повертає LIST для експорту
    List<Barcode> findByStatusAndApnOrderByLastUpdatedDesc(String status, String apn);
    // Повертає PAGE для пагінації
    Page<Barcode> findByStatusAndApnOrderByLastUpdatedDesc(String status, String apn, Pageable pageable);


    boolean existsByCode(String code);

    Page<Barcode> findAllByStatusNot(String status, Pageable pageable);

    Page<Barcode> findByApnContainingAndStatusNot(String apn, String status, Pageable pageable);

    Page<Barcode> findAllByStatus(String status, Pageable pageable);

    @Modifying
    @Query("UPDATE Barcode b SET b.status = 'out', b.lastUpdated = CURRENT_TIMESTAMP WHERE b.code IN :codes")
    void markAllOut(@Param("codes") List<String> codes);

    List<Barcode> findByStatusNot(String status);
    List<Barcode> findByApnContainingIgnoreCaseAndStatusNot(String apn, String status);

    List<Barcode> findByCodeIn(List<String> codes);

    // Новий метод для пошуку застарілих штрих-кодів
    List<Barcode> findByCreationDateBeforeAndStatusNot(LocalDateTime date, String status);

    // Новий метод для пошуку застарілих штрих-кодів з пагінацією
    Page<Barcode> findByCreationDateBeforeAndStatusNot(LocalDateTime date, String status, Pageable pageable);

    // Новий метод для отримання штрих-кодів з певного імпорту
    Page<Barcode> findByImportBatchId(Long batchId, Pageable pageable);

    // Нові методи для статистики
    long countByStatus(String status);
    long countByStatusNot(String status);

    @Query("SELECT b.location FROM Barcode b WHERE b.status <> 'out' GROUP BY b.location ORDER BY COUNT(b.location) DESC")
    Page<String> findTopLocation(Pageable pageable);

    long countByImportBatchId(Long batchId);

    // НОВИЙ МЕТОД: для графіку доданих штрих-кодів
    @Query(value = "SELECT YEAR(b.creation_date) as \"year\", MONTH(b.creation_date) as \"month\", COUNT(b.id) as \"count\" " +
            "FROM barcode b " +
            "WHERE b.creation_date >= :startDate " +
            "GROUP BY YEAR(b.creation_date), MONTH(b.creation_date) " +
            "ORDER BY \"year\", \"month\"", nativeQuery = true)
    List<MonthlyStatDto> getMonthlyAddedStats(@Param("startDate") LocalDateTime startDate);

    // 2. Новий метод для отримання списку стелажів для фільтра
    // (Ми беремо лише ті, що не списані)
    @Query("SELECT DISTINCT b.rack FROM Barcode b WHERE b.status <> 'out' AND b.rack IS NOT NULL ORDER BY b.rack")
    List<String> findDistinctRacks();

    // 3. Новий метод для отримання списку прольотів для фільтра
    @Query("SELECT DISTINCT b.bay FROM Barcode b WHERE b.status <> 'out' AND b.bay IS NOT NULL ORDER BY b.bay")
    List<String> findDistinctBays();

    // 4. Новий метод для пошуку за APN (для режиму акордеону)
    // Ми сортуємо тут, щоб LinkedHashMap зберіг порядок
    List<Barcode> findByApnAndStatusNotOrderByRackAscBayAsc(String apn, String status);
}