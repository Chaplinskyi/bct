package ua.karpaty.barcodetracker.Service;

import com.github.pjfanning.xlsx.StreamingReader;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ua.karpaty.barcodetracker.Entity.Barcode;
import ua.karpaty.barcodetracker.Entity.LocationHistory;
import ua.karpaty.barcodetracker.Entity.StatusHistory;
import ua.karpaty.barcodetracker.Repository.BarcodeRepository;
import ua.karpaty.barcodetracker.Repository.LocationHistoryRepository;
import ua.karpaty.barcodetracker.Repository.StatusHistoryRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class BarcodeService {

    private final BarcodeRepository barcodeRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    @Autowired
    public BarcodeService(BarcodeRepository barcodeRepository,
                          LocationHistoryRepository locationHistoryRepository,
                          StatusHistoryRepository statusHistoryRepository) {
        this.barcodeRepository = barcodeRepository;
        this.locationHistoryRepository = locationHistoryRepository;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    public List<Barcode> findAll() {
        return barcodeRepository.findAll();
    }

    public Optional<Barcode> findByCode(String code) {
        return barcodeRepository.findByCode(code);
    }

    public Barcode findById(Long id) {
        return barcodeRepository.findById(String.valueOf(id))
                .orElseThrow(() -> new RuntimeException("Barcode not found with id: " + id));
    }

    @Transactional
    public void saveAll(List<Barcode> barcodes) {
        if (barcodes == null || barcodes.isEmpty()) return;

        for (Barcode b : barcodes) {
            b.setLocation("prestock"); // встановлення фіксованої локації при імпорті

            // Встановлення дати додавання
            if (b.getParsedDate() != null) {
                b.setLastUpdated(b.getParsedDate());
            } else {
                b.setLastUpdated(LocalDateTime.now());
            }
        }

        List<Barcode> savedBarcodes = barcodeRepository.saveAll(barcodes);

        List<LocationHistory> locHistories = new ArrayList<>();
        List<StatusHistory> statHistories = new ArrayList<>();

        for (Barcode b : savedBarcodes) {
            // ⚠️ Використовуємо ту ж дату, що і в lastUpdated
            LocalDateTime changeTime = b.getLastUpdated();

            locHistories.add(createLocationHistory(b, null, b.getLocation(), changeTime));
            statHistories.add(createStatusHistory(b, null, b.getStatus(), changeTime));
        }

        locationHistoryRepository.saveAll(locHistories);
        statusHistoryRepository.saveAll(statHistories);
    }

    @Transactional
    public void updateStatus(Long id, String newStatus) {
        Barcode barcode = barcodeRepository.findById(String.valueOf(id))
                .orElseThrow(() -> new RuntimeException("Barcode not found with ID: " + id));

        // Оновлюємо статус
        barcode.setStatus(newStatus);
        barcode.setLastUpdated(LocalDateTime.now());

        // Створюємо новий запис в історію
        StatusHistory history = new StatusHistory();
        history.setStatus(newStatus);
        history.setTimestamp(LocalDateTime.now());
        history.setBarcode(barcode); // зв'язок назад

        // Додаємо в список історії
        barcode.getStatusHistory().add(history);

        // Зберігаємо штрихкод разом з історією
        barcodeRepository.save(barcode);
    }

    public List<Barcode> findAllOutSortedByDate() {
        return barcodeRepository.findByStatusOrderByLastUpdatedDesc("out");
    }

    public List<Barcode> findOutByDateRange(LocalDateTime start, LocalDateTime end) {
        return barcodeRepository.findByStatusAndLastUpdatedBetweenOrderByLastUpdatedDesc("out", start, end);
    }

    public List<Barcode> findOutByDateAndApn(LocalDateTime from, LocalDateTime to, String apn) {
        return barcodeRepository.findByStatusAndDateRangeAndApn("out", from, to, apn);
    }

    public List<Barcode> findOutByApn(String apn) {
        return barcodeRepository.findByStatusAndApnOrderByLastUpdatedDesc("out", apn);
    }

    @Transactional
    public void updateBarcodesToOut(List<String> barcodes) {
        int batchSize = 500;
        for (int i = 0; i < barcodes.size(); i += batchSize) {
            int end = Math.min(i + batchSize, barcodes.size());
            List<String> batch = barcodes.subList(i, end);
            barcodeRepository.markAllOut(batch);
        }
    }

    public List<String> readFirstColumnBarcodes(MultipartFile file, int maxRows) throws IOException {
        // 1. Перевірка кількості рядків та колонок (попередній аналіз)
        try (InputStream previewInputStream = file.getInputStream();
             Workbook previewWorkbook = new XSSFWorkbook(previewInputStream)) {

            Sheet sheet = previewWorkbook.getSheetAt(0);

            int rows = sheet.getPhysicalNumberOfRows();
            if (rows > maxRows) {
                return null; // перевищення кількості рядків
            }

            for (Row row : sheet) {
                for (int i = 1; i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell != null && cell.getCellType() != CellType.BLANK) {
                        return Collections.emptyList(); // зайві стовпчики
                    }
                }
            }
        }

        // 2. Якщо все гаразд — читаємо ефективно штрихкоди
        List<String> barcodes = new ArrayList<>(Math.min(maxRows, 1000));
        try (InputStream is = file.getInputStream();
             Workbook workbook = StreamingReader.builder()
                     .rowCacheSize(100)
                     .bufferSize(4096)
                     .open(is)) {

            Sheet sheet = workbook.getSheetAt(0);

            for (Row row : sheet) {
                Cell cell = row.getCell(0, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell != null) {
                    String barcode = switch (cell.getCellType()) {
                        case STRING -> cell.getStringCellValue().trim();
                        case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
                        default -> null;
                    };
                    if (barcode != null && !barcode.isEmpty()) {
                        barcodes.add(barcode);
                    }
                }

                if (barcodes.size() >= maxRows) break;
            }
        }

        return barcodes;
    }

    // =====================
    // === PRIVATE HELPERS
    // =====================

    private LocationHistory createLocationHistory(Barcode barcode, String oldLoc, String newLoc, LocalDateTime time) {
        LocationHistory history = new LocationHistory();
        history.setBarcode(barcode);
        history.setNewLocation(newLoc); // ← Використовуємо параметр newLoc
        history.setChangeTime(time); // ← Використовуємо переданий час
        return locationHistoryRepository.save(history); // Повертаємо збережену історію
    }

    private StatusHistory createStatusHistory(Barcode barcode, String oldStatus, String newStatus, LocalDateTime time) {
        StatusHistory h = new StatusHistory();
        h.setBarcode(barcode);
        h.setOldStatus(oldStatus);
        h.setNewStatus(newStatus);
        h.setChangeTime(time);
        return h;
    }

    public void updateLocation(Long id, String fullLocation) {
        Barcode barcode = barcodeRepository.findById(String.valueOf(id))
                .orElseThrow(() -> new RuntimeException("Barcode not found"));

        String oldLocation = barcode.getLocation();

        barcode.setLocation(fullLocation);
        barcode.setLastUpdated(LocalDateTime.now());
        barcodeRepository.save(barcode);

        locationHistoryRepository.save(new LocationHistory(barcode, oldLocation, fullLocation, LocalDateTime.now()));
    }

    public Page<Barcode> findAllByStatusNot(String status, int page, int size) {
        return barcodeRepository.findAllByStatusNot(status, PageRequest.of(page, size));
    }

    public Page<Barcode> findByApnContainingAndStatusNot(String apn, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastUpdated").descending());
        return barcodeRepository.findByApnContainingAndStatusNot(apn, status, pageable);
    }

    public Page<Barcode> findAllByStatus(String status, Pageable pageable) {
        return barcodeRepository.findAllByStatus(status, pageable);
    }



}