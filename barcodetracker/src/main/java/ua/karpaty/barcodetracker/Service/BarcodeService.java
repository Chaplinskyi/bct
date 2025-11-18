package ua.karpaty.barcodetracker.Service;

import com.github.pjfanning.xlsx.StreamingReader;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ua.karpaty.barcodetracker.Dto.*;
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
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Service
public class BarcodeService {

    private final BarcodeRepository barcodeRepository;
    private final ImportBatchRepository importBatchRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final Map<String, Integer> locationMap;

    private final FrozenDataService frozenDataService;

    @Autowired
    public BarcodeService(BarcodeRepository barcodeRepository, ImportBatchRepository importBatchRepository,
                          LocationHistoryRepository locationHistoryRepository,
                          StatusHistoryRepository statusHistoryRepository, Map<String, Integer> locationMap, FrozenDataService frozenDataService) {
        this.barcodeRepository = barcodeRepository;
        this.importBatchRepository = importBatchRepository;
        this.locationHistoryRepository = locationHistoryRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.locationMap = locationMap;
        this.frozenDataService = frozenDataService;
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

        long nextId = importBatchRepository.findTopByOrderByIdDesc()
                .map(lastBatch -> lastBatch.getId() + 1)
                .orElse(1L);
        ImportBatch batch = new ImportBatch("Import - " + nextId, LocalDateTime.now());
        importBatchRepository.save(batch);

        for (Barcode b : barcodes) {
            b.setImportBatch(batch);
            LocalDateTime initialDate = b.getParsedDate() != null ? b.getParsedDate() : LocalDateTime.now();
            b.setCreationDate(initialDate);
            b.setLastUpdated(initialDate);
        }

        List<Barcode> savedBarcodes = barcodeRepository.saveAll(barcodes);

        List<LocationHistory> locHistories = new ArrayList<>();
        List<StatusHistory> statHistories = new ArrayList<>();

        for (Barcode b : savedBarcodes) {
            LocalDateTime changeTime = b.getLastUpdated();

            locHistories.add(createLocationHistory(b, null, b.getLocation(), changeTime));
            statHistories.add(createStatusHistory(b, null, b.getStatus(), changeTime));
        }

        locationHistoryRepository.saveAll(locHistories);
        statusHistoryRepository.saveAll(statHistories);
    }

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

        String oldStatus = barcode.getStatus();

        barcode.setStatus(newStatus);
        barcode.setLastUpdated(LocalDateTime.now());

        StatusHistory history = new StatusHistory(
                barcode,
                oldStatus,
                newStatus,
                LocalDateTime.now()
        );

        barcode.getStatusHistory().add(history);

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
    public List<MismatchDto> findLocationMismatches(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return barcodeRepository.findLocationMismatches(pageable);
    }

    @Transactional
    public ChartDataDto getWarehouseFillRate(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<LocationCountDto> stats = barcodeRepository.getLocationFillRate(pageable);

        List<String> labels = stats.stream()
                .map(LocationCountDto::getLocation)
                .collect(Collectors.toList());

        List<Long> data = stats.stream()
                .map(LocationCountDto::getCount)
                .collect(Collectors.toList());

        return new ChartDataDto(labels, data);
    }

    public Map<String, List<String>> readFirstColumnBarcodes(MultipartFile file, int maxRows) throws IOException {
        List<String> codesFromFile = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             Workbook workbook = StreamingReader.builder()
                     .rowCacheSize(100)
                     .bufferSize(4096)
                     .open(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = 0;
            for (Row row : sheet) {
                if (rowCount++ > maxRows) {
                    return null;
                }

                Cell codeCell = row.getCell(0);
                if (codeCell == null) continue;

                String code = null;
                if (codeCell.getCellType() == CellType.STRING) {
                    code = codeCell.getStringCellValue().trim();
                } else if (codeCell.getCellType() == CellType.NUMERIC) {
                    code = String.valueOf((long) codeCell.getNumericCellValue()).trim();
                }

                if (code != null && !code.isEmpty() && code.matches("\\d+")) {
                    codesFromFile.add(code);
                }
            }
        } catch (Exception e) {
            throw new IOException("Помилка читання Excel файлу: " + e.getMessage(), e);
        }

        Set<String> uniqueCodes = new HashSet<>();
        Set<String> duplicateCodes = new HashSet<>();

        for (String code : codesFromFile) {
            if (!uniqueCodes.add(code)) {
                duplicateCodes.add(code);
            }
        }

        Map<String, List<String>> result = new HashMap<>();
        result.put("codesToProcess", new ArrayList<>(uniqueCodes));
        result.put("duplicateCodes", new ArrayList<>(duplicateCodes));

        return result;
    }

    public List<Barcode> findTopOutdatedBarcodes(int limit) {
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        Pageable pageable = PageRequest.of(0, limit, Sort.by("creationDate").ascending());
        return barcodeRepository.findByCreationDateBeforeAndStatusNot(oneYearAgo, "out", pageable).getContent();
    }

    public List<Barcode> findAllOutdatedBarcodes() {
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        return barcodeRepository.findByCreationDateBeforeAndStatusNot(oneYearAgo, "out");
    }

    private LocationHistory createLocationHistory(Barcode barcode, String oldLoc, String newLoc, LocalDateTime time) {
        LocationHistory history = new LocationHistory();
        history.setBarcode(barcode);
        history.setOldLocation(oldLoc);
        history.setNewLocation(newLoc);
        history.setChangeTime(time);
        return history; //
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
        Pageable pageable = PageRequest.of(page, size, Sort.by("creationDate").ascending());
        return barcodeRepository.findAllByStatusNot(status, pageable);
    }

    public Page<Barcode> findByApnContainingAndStatusNot(String apn, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("creationDate").ascending());
        return barcodeRepository.findByApnContainingAndStatusNot(apn, status, pageable);
    }

    @Transactional
    public DashboardStatsDto getDashboardStats() {
        // 1. Отримуємо перші 4 показники
        DashboardStatsInterface stats = barcodeRepository.getCombinedDashboardStats();

        // 2. Отримуємо загальну кількість з "замороженої" бази
        long frozenCount = frozenDataService.countTotalFrozenBarcodes();

        // 3. Отримуємо 5-й показник (lastImportCount)
        Optional<ImportBatch> lastImportOpt = importBatchRepository.findTopByOrderByIdDesc();
        long lastImportCount = 0;
        if (lastImportOpt.isPresent()) {
            // === ОСЬ ВИПРАВЛЕННЯ ДЛЯ ЧИТАННЯ ===
            // Ми читаємо збережене поле, а не робимо .getBarcodes().size()
            Integer count = lastImportOpt.get().getBarcodeCount();
            if (count != null) {
                lastImportCount = count;
            }
        }

        // 4. Створюємо DTO через конструктор
        return new DashboardStatsDto(
                stats.getTotalInDb(),
                stats.getTotalAdded() + frozenCount,
                stats.getTotalDiscarded() + frozenCount,
                stats.getMostPopularLocation() != null ? stats.getMostPopularLocation() : "-",
                lastImportCount
        );
    }

    public ChartDataDto getMonthlyAddedStats() {
        LocalDateTime startDate = LocalDateTime.now().minusYears(1).withDayOfMonth(1);
        List<MonthlyStatDto> stats = barcodeRepository.getMonthlyAddedStats(startDate);
        return formatChartData(stats, startDate);
    }

    public ChartDataDto getMonthlyDiscardStats() {
        LocalDateTime startDate = LocalDateTime.now().minusYears(1).withDayOfMonth(1);
        List<MonthlyStatDto> stats = statusHistoryRepository.getMonthlyDiscardStats(startDate);
        return formatChartData(stats, startDate);
    }

    private ChartDataDto formatChartData(List<MonthlyStatDto> stats, LocalDateTime startDate) {
        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();

        Map<String, Long> statsMap = stats.stream()
                .collect(Collectors.toMap(
                        s -> s.getYear() + "-" + s.getMonth(),
                        MonthlyStatDto::getCount
                ));

        LocalDateTime end = LocalDateTime.now().withDayOfMonth(1);
        LocalDateTime current = startDate;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("uk"));

        while (!current.isAfter(end)) {
            labels.add(current.format(formatter));
            String key = current.getYear() + "-" + current.getMonthValue();
            data.add(statsMap.getOrDefault(key, 0L));
            current = current.plusMonths(1);
        }

        return new ChartDataDto(labels, data);
    }

    public Page<Barcode> findWarehouseView(String rack, String bay, Pageable pageable) {
        String trimRack = (rack != null) ? rack.trim() : null;
        String trimBay = (bay != null) ? bay.trim() : null;
        boolean rackPresent = trimRack != null && !trimRack.isBlank();
        boolean bayPresent = trimBay != null && !trimBay.isBlank();

        Pageable dateSortPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("creationDate").ascending());
        Pageable locationSortPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("creationDate").ascending());


        if (rackPresent && bayPresent) {
            log.info("Filtering warehouse view by EXACT rack '{}' and bay '{}', sorting by date.", trimRack, trimBay);
            return barcodeRepository.findByRackAndBayExactOrderByCreationDateAsc(trimRack, trimBay, dateSortPageable);

        } else if (rackPresent) {
            log.info("Filtering warehouse view by rack '{}' (starts with), sorting by date.", trimRack);
            String canonicalRackKey = findCanonicalKey(trimRack, locationMap);
            if (canonicalRackKey == null) {
                log.warn("Canonical key not found for rack '{}'", trimRack);
                return Page.empty(pageable);
            }

            Integer bayCount = locationMap.getOrDefault(canonicalRackKey, -1);

            if (bayCount == 0) {
                log.info("Rack '{}' has no bays, performing exact search, sorting by date.", canonicalRackKey);
                return barcodeRepository.findByRackExactOrderByCreationDateAsc(canonicalRackKey, dateSortPageable);
            } else {
                log.info("Rack '{}' has bays, performing starts-with search, sorting by date.", canonicalRackKey);
                return barcodeRepository.findByRackStartsWithOrderByCreationDateAsc(canonicalRackKey, dateSortPageable);
            }

        } else if (bayPresent) {
            log.info("Filtering warehouse view by bay '{}' (ends with), sorting by date.", trimBay);
            return barcodeRepository.findByBayEndsWithOrderByCreationDateAsc(trimBay, dateSortPageable);

        } else {
            log.info("No filters applied to warehouse view, sorting by creation date.");
            return barcodeRepository.findWarehouseViewAllOrderByCreationDateAsc(locationSortPageable); // Сортування за датою
        }
    }

    public Map<LocationDTO, List<Barcode>> findMaterialByApnGroupedByLocation(String apn) {

        List<Barcode> barcodes = barcodeRepository.findByApnAndStatusNotOrderByCreationDateAsc(apn.trim(), "out");

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
        location = location.trim();

        Matcher matcher = Pattern.compile("\\d").matcher(location);
        if (matcher.find()) {
            int index = matcher.start();
            if (index > 0) {
                String rackPart = location.substring(0, index);
                String bayPart = location.substring(index);
                return new LocationDTO(rackPart, bayPart);
            } else {
                return new LocationDTO("", location);
            }
        } else {
            return new LocationDTO(location, "");
        }
    }

    public List<String> getAllRacks() {
        return new ArrayList<>(locationMap.keySet());
    }

    public List<String> getAllBays() {
        int maxBay = locationMap.values().stream()
                .max(Integer::compare)
                .orElse(1);

        return IntStream.rangeClosed(1, maxBay)
                .mapToObj(String::valueOf)
                .collect(Collectors.toList());
    }

    private String findCanonicalKey(String key, Map<String, Integer> map) {
        if (key == null) return null;
        for (String mapKey : map.keySet()) {
            if (mapKey.equalsIgnoreCase(key)) {
                return mapKey;
            }
        }
        return null;
    }

    public List<ActivityLogDto> getRecentActivities(int limit) {
        List<ActivityLogDto> combinedList = new ArrayList<>();
        Pageable pageable = PageRequest.of(0, limit);

        List<ImportBatch> imports = importBatchRepository.findAllByOrderByIdDesc();
        for (ImportBatch batch : imports.stream().limit(limit).toList()) {
            combinedList.add(new ActivityLogDto(
                    batch.getImportDate(),
                    "IMPORT",
                    "Створено " + batch.getName(),
                    "bi bi-file-earmark-plus text-success",
                    "/import/" + batch.getId()
            ));
        }

        List<LocationHistory> moves = locationHistoryRepository.findRecentWithBarcode(pageable);
        for (LocationHistory move : moves) {
            if (move.getOldLocation() != null) {
                combinedList.add(new ActivityLogDto(
                        move.getChangeTime(),
                        "MOVE",
                        "Код " + move.getBarcode().getCode() + " переміщено на " + move.getNewLocation(),
                        "bi bi-geo-alt text-primary",
                        "/barcodes/" + move.getBarcode().getId()
                ));
            }
        }

        List<StatusHistory> statuses = statusHistoryRepository.findRecentWithBarcode(pageable);
        for (StatusHistory status : statuses) {
            if (status.getOldStatus() != null) {
                String desc = "Статус коду " + status.getBarcode().getCode() + " змінено на " + status.getNewStatus();
                String icon = "bi bi-toggles text-warning";

                if ("out".equals(status.getNewStatus())) {
                    desc = "Код " + status.getBarcode().getCode() + " списано";
                    icon = "bi bi-file-earmark-minus text-danger";
                }

                combinedList.add(new ActivityLogDto(
                        status.getChangeTime(),
                        "STATUS",
                        desc,
                        icon,
                        "/barcodes/" + status.getBarcode().getId()
                ));
            }
        }

        combinedList.sort(Comparator.comparing(ActivityLogDto::getTimestamp).reversed());
        return combinedList.stream().limit(limit).collect(Collectors.toList());
    }

    public List<ApnSummaryDto> getApnSummary() {
        return barcodeRepository.getApnSummaryByStatusNot("out");
    }

    @Transactional
    public void streamAllBarcodesToExcel(OutputStream out) throws IOException {
        try (Stream<Barcode> barcodeStream = barcodeRepository.streamByStatusNotOrderByCreationDateAsc("out")) {
            ExcelService.exportToExcelStreaming(barcodeStream, out);
        }
    }

    @Transactional
    public void streamBarcodesByApnToExcel(String apn, OutputStream out) throws IOException {
        try (Stream<Barcode> barcodeStream = barcodeRepository.streamByApnContainingIgnoreCaseAndStatusNotOrderByCreationDateAsc(apn, "out")) {
            ExcelService.exportToExcelStreaming(barcodeStream, out);
        }
    }

}