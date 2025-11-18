package ua.karpaty.barcodetracker.Repository;

import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ua.karpaty.barcodetracker.Dto.*;
import ua.karpaty.barcodetracker.Entity.Barcode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Repository
public interface BarcodeRepository extends JpaRepository<Barcode, Long>, JpaSpecificationExecutor<Barcode> {

    Optional<Barcode> findByCode(String code);

    List<Barcode> findByStatusOrderByLastUpdatedDesc(String status);

    Page<Barcode> findByStatusOrderByLastUpdatedDesc(String status, Pageable pageable);

    List<Barcode> findByStatusAndLastUpdatedBetweenOrderByLastUpdatedDesc(
            String status, LocalDateTime start, LocalDateTime end);

    Page<Barcode> findByStatusAndLastUpdatedBetweenOrderByLastUpdatedDesc(
            String status, LocalDateTime start, LocalDateTime end, Pageable pageable);

    @Query("""

            SELECT b FROM Barcode b
        WHERE b.status = :status
          AND b.lastUpdated BETWEEN :from AND :to
          AND b.apn = :apn
        ORDER BY b.lastUpdated DESC
        """)
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
    Page<Barcode> findByStatusAndDateRangeAndApn(
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("apn") String apn,
            Pageable pageable);
    List<Barcode> findByStatusAndApnOrderByLastUpdatedDesc(String status, String apn);

    Page<Barcode> findByStatusAndApnOrderByLastUpdatedDesc(String status, String apn, Pageable pageable);

    Page<Barcode> findAllByStatusNot(String status, Pageable pageable);

    Page<Barcode> findByApnContainingAndStatusNot(String apn, String status, Pageable pageable);

    List<Barcode> findByCodeIn(List<String> codes);

    List<Barcode> findByCreationDateBeforeAndStatusNot(LocalDateTime date, String status);

    Page<Barcode> findByCreationDateBeforeAndStatusNot(LocalDateTime date, String status, Pageable pageable);

    Page<Barcode> findByImportBatchId(Long batchId, Pageable pageable);

    @Query(value = "SELECT EXTRACT(YEAR FROM b.creation_date) as \"year\", EXTRACT(MONTH FROM b.creation_date) as \"month\", COUNT(b.id) as \"count\" " +
            "FROM barcode b " +
            "WHERE b.creation_date >= :startDate " +
            "GROUP BY EXTRACT(YEAR FROM b.creation_date), EXTRACT(MONTH FROM b.creation_date) " +
            "ORDER BY \"year\", \"month\"", nativeQuery = true)
    List<MonthlyStatDto> getMonthlyAddedStats(@Param("startDate") LocalDateTime startDate);

    List<Barcode> findByApnAndStatusNotOrderByCreationDateAsc(String apn, String status);

    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' AND " +
            "(lower(trim(b.location)) LIKE lower(concat(trim(:rack), ' %')) OR " +
            " lower(trim(b.location)) LIKE lower(concat('excess ', trim(:rack), ' %'))) " +
            "AND lower(b.location) <> 'wires' " +
            "ORDER BY b.creationDate ASC")
    Page<Barcode> findByRackStartsWithOrderByCreationDateAsc(@Param("rack") String rack, Pageable pageable);

    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' AND lower(trim(b.location)) = lower(trim(:rack)) " +
            "AND lower(b.location) <> 'wires' " +
            "ORDER BY b.creationDate ASC")
    Page<Barcode> findByRackExactOrderByCreationDateAsc(@Param("rack") String rack, Pageable pageable);

    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' AND " +
            "(lower(trim(b.location)) = lower(concat(trim(:rack), ' ', trim(:bay))) OR " +
            " lower(trim(b.location)) = lower(concat('excess ', trim(:rack), ' ', trim(:bay)))) " +
            "AND lower(b.location) <> 'wires' " +
            "ORDER BY b.creationDate ASC")
    Page<Barcode> findByRackAndBayExactOrderByCreationDateAsc(@Param("rack") String rack, @Param("bay") String bay, Pageable pageable);

    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' AND lower(trim(b.location)) LIKE lower(concat('% ', trim(:bay))) " +
            "AND lower(b.location) <> 'wires' " + // <-- Додати цю умову
            "ORDER BY b.creationDate ASC")
    Page<Barcode> findByBayEndsWithOrderByCreationDateAsc(@Param("bay") String bay, Pageable pageable);

    @Query("SELECT b FROM Barcode b WHERE lower(b.status) <> 'out' " +
            "AND lower(b.location) <> 'wires' " +
            "ORDER BY b.creationDate ASC")
    Page<Barcode> findWarehouseViewAllOrderByCreationDateAsc(Pageable pageable);

    @Query("SELECT new ua.karpaty.barcodetracker.Dto.ApnSummaryDto(b.apn, SUM(b.quantity), COUNT(b.id)) " +
            "FROM Barcode b " +
            "WHERE b.status <> :status AND b.apn IS NOT NULL AND b.apn <> '' " +
            "GROUP BY b.apn " +
            "ORDER BY b.apn ASC")
    List<ApnSummaryDto> getApnSummaryByStatusNot(@Param("status") String status);

    @Query(value = """
        SELECT
            (SELECT COUNT(*) FROM barcode WHERE status != 'out') AS totalInDb,
            (SELECT COUNT(*) FROM barcode) AS totalAdded,
            (SELECT COUNT(*) FROM barcode WHERE status = 'out') AS totalDiscarded,
            (SELECT location FROM barcode
             WHERE status != 'out' AND location IS NOT NULL AND location != ''
             GROUP BY location
             ORDER BY COUNT(location) DESC
             LIMIT 1) AS mostPopularLocation
        """, nativeQuery = true)
    DashboardStatsInterface getCombinedDashboardStats();

    long countByImportBatchId(Long batchId);

    @QueryHints(value = @QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "500"))
    @Query("SELECT b FROM Barcode b WHERE b.status <> :status ORDER BY b.creationDate ASC")
    Stream<Barcode> streamByStatusNotOrderByCreationDateAsc(@Param("status") String status);

    @QueryHints(value = @QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "500"))
    @Query("SELECT b FROM Barcode b WHERE lower(b.apn) LIKE lower(concat('%', :apn, '%')) AND b.status <> :status ORDER BY b.creationDate ASC")
    Stream<Barcode> streamByApnContainingIgnoreCaseAndStatusNotOrderByCreationDateAsc(@Param("apn") String apn, @Param("status") String status);

    @Query("SELECT new ua.karpaty.barcodetracker.Dto.MismatchDto(b.id, b.code, b.apn, b.location, m.location) " +
            "FROM Barcode b JOIN MaterialMaster m ON b.apn = m.apn " +
            "WHERE b.status = 'stock' " +
            "AND m.location IS NOT NULL AND m.location != '' " +
            "AND b.location != m.location " +
            "AND b.location NOT IN ('prestock', 'wires') " +
            "ORDER BY b.lastUpdated DESC")
    List<MismatchDto> findLocationMismatches(Pageable pageable);

    @Query("SELECT new ua.karpaty.barcodetracker.Dto.LocationCountDto(b.location, COUNT(b)) " +
            "FROM Barcode b " +
            "WHERE b.status = 'stock' " +
            "GROUP BY b.location " +
            "ORDER BY COUNT(b) DESC")
    List<LocationCountDto> getLocationFillRate(Pageable pageable);

}
