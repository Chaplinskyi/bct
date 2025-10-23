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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ua.karpaty.barcodetracker.Dto.ChartDataDto;
import ua.karpaty.barcodetracker.Dto.DashboardStatsDto;
import ua.karpaty.barcodetracker.Dto.LocationDTO;
import ua.karpaty.barcodetracker.Dto.MonthlyStatDto;
import ua.karpaty.barcodetracker.Entity.Barcode;
import ua.karpaty.barcodetracker.Entity.ImportBatch;
import ua.karpaty.barcodetracker.Entity.LocationHistory;
import ua.karpaty.barcodetracker.Entity.StatusHistory;
import ua.karpaty.barcodetracker.Repository.BarcodeRepository;
import ua.karpaty.barcodetracker.Repository.ImportBatchRepository;
import ua.karpaty.barcodetracker.Repository.LocationHistoryRepository;
import ua.karpaty.barcodetracker.Repository.StatusHistoryRepository;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
public class BarcodeService {

    private final BarcodeRepository barcodeRepository;
    private final ImportBatchRepository importBatchRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final Map<String, Integer> locationMap;


    @Autowired
    public BarcodeService(BarcodeRepository barcodeRepository, ImportBatchRepository importBatchRepository,
                          LocationHistoryRepository locationHistoryRepository,
                          StatusHistoryRepository statusHistoryRepository, Map<String, Integer> locationMap) {
        this.barcodeRepository = barcodeRepository;
        this.importBatchRepository = importBatchRepository;
        this.locationHistoryRepository = locationHistoryRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.locationMap = locationMap;
    }

    public List<Barcode> findAll() {
        return barcodeRepository.findAll();
    }

    public Optional<Barcode> findByCode(String code) {
        return barcodeRepository.findByCode(code);
    }

    public Barcode findById(Long id) {
        return barcodeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Barcode not found with id: " + id));
    }

    @Transactional
    public void saveAll(List<Barcode> barcodes) {
        if (barcodes == null || barcodes.isEmpty()) return;

        //Створюємо новий запис про імпорт
        long nextId = importBatchRepository.findTopByOrderByIdDesc()
                .map(lastBatch -> lastBatch.getId() + 1)
                .orElse(1L);
        ImportBatch batch = new ImportBatch("Import - " + nextId, LocalDateTime.now());
        importBatchRepository.save(batch);

        for (Barcode b : barcodes) {
            //b.setLocation("prestock");
            b.setImportBatch(batch);
            // Встановлення початкової дати
            LocalDateTime initialDate = b.getParsedDate() != null ? b.getParsedDate() : LocalDateTime.now();
            b.setCreationDate(initialDate); // Встановлюємо дату створення
            b.setLastUpdated(initialDate);  // І дата останнього оновлення така ж
        }

        List<Barcode> savedBarcodes = barcodeRepository.saveAll(barcodes);

        List<LocationHistory> locHistories = new ArrayList<>();
        List<StatusHistory> statHistories = new ArrayList<>();

        for (Barcode b : savedBarcodes) {
            // Використовуємо ту ж дату, що і в lastUpdated
            LocalDateTime changeTime = b.getLastUpdated();

            locHistories.add(createLocationHistory(b, null, b.getLocation(), changeTime));
            statHistories.add(createStatusHistory(b, null, b.getStatus(), changeTime));
        }

        locationHistoryRepository.saveAll(locHistories);
        statusHistoryRepository.saveAll(statHistories);
    }

    // Нові методи для контролера
    public String getNextImportName() {
        return importBatchRepository.findTopByOrderByIdDesc()
                .map(lastBatch -> "Import - " + (lastBatch.getId() + 1))
                .orElse("Import - 1");
    }

    public List<ImportBatch> findAllImportBatches() {
        return importBatchRepository.findAllByOrderByIdDesc();
    }

    public Page<Barcode> findBarcodesByImportId(Long batchId, Pageable pageable) {
        return barcodeRepository.findByImportBatchId(batchId, pageable);
    }

    @Transactional
    public void updateStatus(Long id, String newStatus) {
        Barcode barcode = barcodeRepository.findById(id)
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

    // --- МЕТОДИ ДЛЯ ПАГІНАЦІЇ (повертають Page) ---

    public Page<Barcode> findAllOutSortedByDate(Pageable pageable) {
        return barcodeRepository.findByStatusOrderByLastUpdatedDesc("out", pageable);
    }

    public Page<Barcode> findOutByDateRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return barcodeRepository.findByStatusAndLastUpdatedBetweenOrderByLastUpdatedDesc("out", start, end, pageable);
    }

    public Page<Barcode> findOutByDateAndApn(LocalDateTime from, LocalDateTime to, String apn, Pageable pageable) {
        return barcodeRepository.findByStatusAndDateRangeAndApn("out", from, to, apn, pageable);
    }

    public Page<Barcode> findOutByApn(String apn, Pageable pageable) {
        return barcodeRepository.findByStatusAndApnOrderByLastUpdatedDesc("out", apn, pageable);
    }

    @Transactional
    public void updateBarcodesToOut(List<String> codes) {
        log.info("Починаємо асинхронне списання для {} штрих-кодів.", codes.size());
        List<Barcode> barcodesToUpdate = barcodeRepository.findByCodeIn(codes);

        List<StatusHistory> historyToSave = new ArrayList<>();

        for (Barcode barcode : barcodesToUpdate) {
            if (!"out".equalsIgnoreCase(barcode.getStatus())) {
                String oldStatus = barcode.getStatus();
                barcode.setStatus("out");
                barcode.setLastUpdated(LocalDateTime.now());
                historyToSave.add(new StatusHistory(barcode, oldStatus, "out", LocalDateTime.now()));
            }
        }

        barcodeRepository.saveAll(barcodesToUpdate);
        statusHistoryRepository.saveAll(historyToSave);
        log.info("Завершено списання для {} штрих-кодів.", barcodesToUpdate.size());
    }

    public List<String> readFirstColumnBarcodes(MultipartFile file, int maxRows) throws IOException {
        List<String> barcodes = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);

            if (sheet.getPhysicalNumberOfRows() > maxRows) {
                return null;
            }

            for (Row row : sheet) {
                if (row == null) continue;
                Cell firstCell = row.getCell(0);

                if (firstCell != null && firstCell.getCellType() != CellType.BLANK) {
                    String code = "";
                    if (firstCell.getCellType() == CellType.STRING) {
                        code = firstCell.getStringCellValue().trim();
                    } else if (firstCell.getCellType() == CellType.NUMERIC) {
                        code = String.valueOf((long) firstCell.getNumericCellValue());
                    }

                    // НОВА ПЕРЕВІРКА: додаємо, тільки якщо складається з цифр
                    if (!code.isEmpty() && code.matches("\\d+")) {
                        barcodes.add(code);
                    }
                }
            }
        }
        return barcodes;
    }

    // метод для отримання перших N застарілих штрих-кодів
    public List<Barcode> findTopOutdatedBarcodes(int limit) {
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        // Сортуємо за датою створення
        Pageable pageable = PageRequest.of(0, limit, Sort.by("creationDate").ascending());
        // Використовуємо новий метод репозиторію
        return barcodeRepository.findByCreationDateBeforeAndStatusNot(oneYearAgo, "out", pageable).getContent();
    }

    // метод для отримання ВСІХ застарілих штрих-кодів для експорту
    public List<Barcode> findAllOutdatedBarcodes() {
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        // Використовуємо новий метод репозиторію
        return barcodeRepository.findByCreationDateBeforeAndStatusNot(oneYearAgo, "out");
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

    @Transactional
    public void updateLocation(Long id, String fullLocation) {
        Barcode barcode = barcodeRepository.findById(id)
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

    // Новий метод для збору статистики
    public DashboardStatsDto getDashboardStats() {
        long totalInDb = barcodeRepository.countByStatusNot("out");
        long totalAdded = barcodeRepository.count();
        long totalDiscarded = barcodeRepository.countByStatus("out");

        // Найпопулярніша локація
        Page<String> topLocations = barcodeRepository.findTopLocation(PageRequest.of(0, 1));
        String mostPopularLocation = topLocations.hasContent() ? topLocations.getContent().get(0) : "Немає";

        // Кількість в останньому імпорті
        long lastImportCount = importBatchRepository.findTopByOrderByIdDesc()
                .map(ImportBatch::getId)
                .map(barcodeRepository::countByImportBatchId)
                .orElse(0L);

        return new DashboardStatsDto(totalInDb, totalAdded, totalDiscarded, mostPopularLocation, lastImportCount);
    }

    // НОВІ МЕТОДИ ДЛЯ ГРАФІКІВ:

    public ChartDataDto getMonthlyAddedStats() {
        LocalDateTime startDate = LocalDateTime.now().minusYears(1).withDayOfMonth(1); // Дані за останній рік
        List<MonthlyStatDto> stats = barcodeRepository.getMonthlyAddedStats(startDate);
        return formatChartData(stats, startDate);
    }

    public ChartDataDto getMonthlyDiscardStats() {
        LocalDateTime startDate = LocalDateTime.now().minusYears(1).withDayOfMonth(1); // Дані за останній рік
        List<MonthlyStatDto> stats = statusHistoryRepository.getMonthlyDiscardStats(startDate);
        return formatChartData(stats, startDate);
    }

    // Допоміжний метод для форматування даних для Chart.js
    private ChartDataDto formatChartData(List<MonthlyStatDto> stats, LocalDateTime startDate) {
        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();

        // Створюємо мапу для швидкого доступу до статистики
        Map<String, Long> statsMap = stats.stream()
                .collect(Collectors.toMap(
                        s -> s.getYear() + "-" + s.getMonth(),
                        MonthlyStatDto::getCount
                ));

        LocalDateTime end = LocalDateTime.now().withDayOfMonth(1);
        LocalDateTime current = startDate;

        // Встановлюємо українську локаль для назв місяців
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("uk"));

        // Проходимо по місяцях від start до end
        while (!current.isAfter(end)) {
            labels.add(current.format(formatter));
            String key = current.getYear() + "-" + current.getMonthValue();
            data.add(statsMap.getOrDefault(key, 0L));
            current = current.plusMonths(1);
        }

        return new ChartDataDto(labels, data);
    }

    /**
     * ОНОВЛЕНО: Використовує існуючу специфікацію для фільтрації
     */
    public Page<Barcode> findWarehouseView(String rack, String bay, Pageable pageable) {
        String trimRack = (rack != null) ? rack.trim() : null;
        String trimBay = (bay != null) ? bay.trim() : null;
        boolean rackPresent = trimRack != null && !trimRack.isBlank();
        boolean bayPresent = trimBay != null && !trimBay.isBlank();

        // Створюємо Pageable для сортування за датою створення (спаданням)
        Pageable dateSortPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("creationDate").descending());
        // Створюємо Pageable для сортування за локацією (зростанням) - для випадку без фільтрів
        Pageable locationSortPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("location").ascending());


        if (rackPresent && bayPresent) {
            // ВИПАДОК 2: Фільтр за Стелажем та Прольотом (точний пошук)
            log.info("Filtering warehouse view by EXACT rack '{}' and bay '{}', sorting by date.", trimRack, trimBay);
            return barcodeRepository.findByRackAndBayExactOrderByCreationDateDesc(trimRack, trimBay, dateSortPageable);

        } else if (rackPresent) {
            // ВИПАДОК 1: Фільтр ТІЛЬКИ за Стелажем
            log.info("Filtering warehouse view by rack '{}' (starts with), sorting by date.", trimRack);
            String canonicalRackKey = findCanonicalKey(trimRack, locationMap);
            if (canonicalRackKey == null) {
                log.warn("Canonical key not found for rack '{}'", trimRack);
                return Page.empty(pageable); // Такого стелажа немає
            }

            Integer bayCount = locationMap.getOrDefault(canonicalRackKey, -1);

            if (bayCount == 0) {
                // Стелаж без прольотів -> точний пошук (prestock, Tape)
                log.info("Rack '{}' has no bays, performing exact search, sorting by date.", canonicalRackKey);
                return barcodeRepository.findByRackExactOrderByCreationDateDesc(canonicalRackKey, dateSortPageable);
            } else {
                // Стелаж з прольотами -> пошук LIKE (SK, ST)
                log.info("Rack '{}' has bays, performing starts-with search, sorting by date.", canonicalRackKey);
                return barcodeRepository.findByRackStartsWithOrderByCreationDateDesc(canonicalRackKey, dateSortPageable);
            }

        } else if (bayPresent) {
            // ВИПАДОК 3: Фільтр ТІЛЬКИ за Прольотом
            log.info("Filtering warehouse view by bay '{}' (ends with), sorting by date.", trimBay);
            return barcodeRepository.findByBayEndsWithOrderByCreationDateDesc(trimBay, dateSortPageable);

        } else {
            // ВИПАДОК 4: Немає фільтрів
            log.info("No filters applied to warehouse view, sorting by location.");
            return barcodeRepository.findWarehouseViewAllOrderByLocationAsc(locationSortPageable); // Сортування за локацією
        }
    }

    /**
     * ОНОВЛЕНО: Використовує DTO та нову parseLocationToDTO
     */
    public Map<LocationDTO, List<Barcode>> findMaterialByApnGroupedByLocation(String apn) {
        List<Barcode> barcodes = barcodeRepository.findByApnAndStatusNotOrderByLocationAsc(apn.trim()); // Додано trim()

        return barcodes.stream()
                .collect(Collectors.groupingBy(
                        barcode -> parseLocationToDTO(barcode.getLocation()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private LocationDTO parseLocationToDTO(String location) {
        if (location == null || location.isBlank()) {
            return new LocationDTO("N/A", "");
        }
        location = location.trim(); // Додано trim()

        // Спробуємо знайти першу цифру
        Matcher matcher = Pattern.compile("\\d").matcher(location);
        if (matcher.find()) {
            int index = matcher.start();
            if (index > 0) { // Якщо є і букви, і цифри (напр. "SK12")
                String rackPart = location.substring(0, index);
                String bayPart = location.substring(index);
                return new LocationDTO(rackPart, bayPart);
            } else { // Якщо починається з цифри (не очікується, але про всяк випадок)
                return new LocationDTO("", location); // Порожній rack
            }
        } else { // Якщо тільки букви (напр. "prestock")
            return new LocationDTO(location, ""); // Порожній bay
        }
    }

    /**
     * НОВИЙ МЕТОД: Отримує стелажі (Rack) з вашого @Bean locationMap
     */
    public List<String> getAllRacks() {
        return new ArrayList<>(locationMap.keySet());
    }

    /**
     * НОВИЙ МЕТОД: Отримує прольоти (Bay) з вашого @Bean locationMap
     */
    public List<String> getAllBays() {
        // 1. Знаходимо максимальну кількість прольотів (напр. 15)
        int maxBay = locationMap.values().stream()
                .max(Integer::compare)
                .orElse(1); // (Якщо мапа порожня, буде 1)

        // 2. Генеруємо список рядків від "1" до "15"
        return IntStream.rangeClosed(1, maxBay)
                .mapToObj(String::valueOf)
                .collect(Collectors.toList());
    }

    /**
     * НОВИЙ МЕТОД: Допоміжний метод для пошуку ключа в мапі без урахування регістру.
     */
    private String findCanonicalKey(String key, Map<String, Integer> map) {
        if (key == null) return null;
        for (String mapKey : map.keySet()) {
            if (mapKey.equalsIgnoreCase(key)) {
                return mapKey; // Повертає ключ з правильним регістром (напр. "SK")
            }
        }
        return null; // Не знайдено
    }

}