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
import ua.karpaty.barcodetracker.Dto.ApnSummaryDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BarcodeRepository extends JpaRepository<Barcode, Long>, JpaSpecificationExecutor<Barcode> {

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
    @Query(value = "SELECT EXTRACT(YEAR FROM b.creation_date) as \"year\", EXTRACT(MONTH FROM b.creation_date) as \"month\", COUNT(b.id) as \"count\" " + // <-- Змінено YEAR та MONTH
            "FROM barcode b " +
            "WHERE b.creation_date >= :startDate " +
            "GROUP BY EXTRACT(YEAR FROM b.creation_date), EXTRACT(MONTH FROM b.creation_date) " + // <-- Змінено YEAR та MONTH
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
    // ВИПРАВЛЕНО: сортуємо за реальним полем 'location'
    List<Barcode> findByApnAndStatusNotOrderByLocationAsc(String apn, String status);

    // 1. Немає фільтрів (тільки статус 'out' виключено)
    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' ORDER BY b.location ASC")
    Page<Barcode> findWarehouseViewAll(Pageable pageable);

    // 2. Лише фільтр Стелажа (rack)
    // Використовуємо lower() та trim() для надійності
    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' AND lower(trim(b.location)) LIKE lower(concat(trim(:rack), '%')) ORDER BY b.location ASC")
    Page<Barcode> findWarehouseViewByRack(@Param("rack") String rack, Pageable pageable);

    // 3. Лише фільтр Прольоту (bay)
    // Використовуємо lower() та trim()
    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' AND lower(trim(b.location)) LIKE lower(concat('%', trim(:bay))) ORDER BY b.location ASC")
    Page<Barcode> findWarehouseViewByBay(@Param("bay") String bay, Pageable pageable);

    // 4. Фільтри Стелажа (rack) та Прольоту (bay)
    // Використовуємо lower() та trim()
    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' AND lower(trim(b.location)) = lower(concat(trim(:rack), trim(:bay))) ORDER BY b.location ASC")
    Page<Barcode> findWarehouseViewByRackAndBay(@Param("rack") String rack, @Param("bay") String bay, Pageable pageable);

    // --- Метод для warehouse-view (Пошук за APN) ---
    // (Цей метод залишається без змін)
    @Query("SELECT b FROM Barcode b WHERE lower(b.apn) = lower(:apn) AND lower(b.status) <> 'out' ORDER BY b.location ASC")
    List<Barcode> findByApnAndStatusNotOrderByLocationAsc(@Param("apn") String apn);

    // --- Методи для warehouse-view (Огляд Складу) ---

    // 2. Лише фільтр Стелажа (LIKE) - для SK, ST...
    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' AND lower(trim(b.location)) LIKE lower(concat(trim(:rack), '%')) ORDER BY b.location ASC")
    Page<Barcode> findWarehouseViewByRackLike(@Param("rack") String rack, Pageable pageable);

    // 3. Лише фільтр Стелажа (EXACT) - для prestock, Tape...
    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' AND lower(trim(b.location)) = lower(trim(:rack)) ORDER BY b.location ASC")
    Page<Barcode> findWarehouseViewByRackExact(@Param("rack") String rack, Pageable pageable);

    // ВИПАДОК 1: Тільки СТЕЛАЖ (rack) - шукає всі локації, що починаються з rack (з/без excess)
    // Сортування за датою створення Asc
    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' AND " +
            "(lower(trim(b.location)) LIKE lower(concat(trim(:rack), ' %')) OR " +      // "SK %"
            " lower(trim(b.location)) LIKE lower(concat('excess ', trim(:rack), ' %'))) " + // "excess SK %"
            "AND lower(b.location) <> 'wires' " +
            "ORDER BY b.creationDate ASC") // Сортування за датою зростанням
    Page<Barcode> findByRackStartsWithOrderByCreationDateDesc(@Param("rack") String rack, Pageable pageable); // Змінено назву

    // ВИПАДОК 1.1: Тільки СТЕЛАЖ (rack) БЕЗ ПРОЛЬОТІВ - точний пошук (prestock, Tape)
    // Сортування за датою створення Asc
    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' AND lower(trim(b.location)) = lower(trim(:rack)) " +
            "AND lower(b.location) <> 'wires' " + // <-- Додати цю умову
            "ORDER BY b.creationDate ASC") // Сортування за датою зростанням
    Page<Barcode> findByRackExactOrderByCreationDateDesc(@Param("rack") String rack, Pageable pageable);

    // ВИПАДОК 2: СТЕЛАЖ (rack) І ПРОЛІТ (bay) - точний пошук (з/без excess)
    // Сортування за датою створення DESC
    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' AND " +
            "(lower(trim(b.location)) = lower(concat(trim(:rack), ' ', trim(:bay))) OR " +          // "SK 1"
            " lower(trim(b.location)) = lower(concat('excess ', trim(:rack), ' ', trim(:bay)))) " + // "excess SK 1"
            "AND lower(b.location) <> 'wires' " +
            "ORDER BY b.creationDate ASC") // Сортування за датою спаданням
    Page<Barcode> findByRackAndBayExactOrderByCreationDateDesc(@Param("rack") String rack, @Param("bay") String bay, Pageable pageable);

    // ВИПАДОК 3: Тільки ПРОЛІТ (bay) - шукає локації, що закінчуються на " <bay>"
    // Сортування за датою створення DESC
    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' AND lower(trim(b.location)) LIKE lower(concat('% ', trim(:bay))) " +
            "AND lower(b.location) <> 'wires' " + // <-- Додати цю умову
            "ORDER BY b.creationDate ASC")
    Page<Barcode> findByBayEndsWithOrderByCreationDateDesc(@Param("bay") String bay, Pageable pageable);

    // ВИПАДОК 4: БЕЗ ФІЛЬТРІВ - всі активні, сортування за локацією ASC
    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' " +
            "AND lower(b.location) <> 'wires' " + // <-- Додати цю умову
            "ORDER BY b.creationDate ASC")
    Page<Barcode> findWarehouseViewAllOrderByLocationAsc(Pageable pageable);

    @Modifying // Вказує, що це запит на зміну даних
    @Query("DELETE FROM LocationHistory lh WHERE lh.barcode.id IN (SELECT b.id FROM Barcode b)")
    void deleteAllLocationHistory();

    @Modifying
    @Query("DELETE FROM StatusHistory sh WHERE sh.barcode.id IN (SELECT b.id FROM Barcode b)")
    void deleteAllStatusHistory();

    @Query("SELECT new ua.karpaty.barcodetracker.Dto.ApnSummaryDto(b.apn, SUM(b.quantity), COUNT(b.id)) " +
            "FROM Barcode b " +
            "WHERE lower(b.status) <> 'out' " + // <-- Умова 'AND lower(b.location) <> 'wires'' ВИДАЛЕНА
            "GROUP BY b.apn " +
            "ORDER BY b.apn ASC")
    List<ApnSummaryDto> getApnSummary();

    // Новий метод для отримання штрих-кодів зі статусом 'check'
    @Query("SELECT b FROM Barcode b WHERE lower(b.status) = 'check' ORDER BY b.location ASC, b.apn ASC")
    List<Barcode> findAllByStatusCheck();

    @Query("SELECT b.code FROM Barcode b WHERE b.code IN :codes")
    Set<String> findExistingCodesByCodeIn(@Param("codes") List<String> codes);

    Page<Barcode> findAllByOrderByCreationDateAsc(Pageable pageable);
}
