package ua.karpaty.barcodetracker.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ua.karpaty.barcodetracker.Entity.MaterialMaster;

import java.util.List;
import java.util.Optional;

@Repository
public interface MaterialMasterRepository extends JpaRepository<MaterialMaster, Long> {

    Optional<MaterialMaster> findByApn(String apn);

    Page<MaterialMaster> findByApnContainingIgnoreCase(String apn, Pageable pageable);

    List<MaterialMaster> findByApnIn(List<String> apns);
}