package ua.karpaty.barcodetracker.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ua.karpaty.barcodetracker.Entity.Barcode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface BarcodeRepository extends JpaRepository<Barcode, String> {

    Optional<Barcode> findByCode(String code);

    List<Barcode> findByStatusOrderByLastUpdatedDesc(String status);

    List<Barcode> findByStatusAndLastUpdatedBetweenOrderByLastUpdatedDesc(
            String status, LocalDateTime start, LocalDateTime end);

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

    List<Barcode> findByStatusAndApnOrderByLastUpdatedDesc(String status, String apn);

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
}
