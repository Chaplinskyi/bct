package ua.karpaty.barcodetracker.Controller;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.web.servlet.view.RedirectView;
import ua.karpaty.barcodetracker.Dto.ApnSummaryDto;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ua.karpaty.barcodetracker.Dto.BarcodeTransferDto;
import ua.karpaty.barcodetracker.Dto.LocationDTO;
import ua.karpaty.barcodetracker.Dto.RecentActivityDto;
import ua.karpaty.barcodetracker.Entity.*;
import ua.karpaty.barcodetracker.Repository.BarcodeRepository;
import ua.karpaty.barcodetracker.Repository.LocationHistoryRepository;
import ua.karpaty.barcodetracker.Repository.StatusHistoryRepository;
import ua.karpaty.barcodetracker.Service.BarcodeService;
import ua.karpaty.barcodetracker.Service.ExcelService;

import java.util.*;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.function.Function;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final BarcodeService barcodeService;
    private final BarcodeRepository barcodeRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final Map<String, Integer> locationMap;
    private final ExcelService excelService;

    @Autowired
    public AdminController(BarcodeService barcodeService, BarcodeRepository barcodeRepository, LocationHistoryRepository locationHistoryRepository, StatusHistoryRepository statusHistoryRepository, Map<String, Integer> locationMap, ExcelService excelService) {
        this.barcodeService = barcodeService;
        this.barcodeRepository = barcodeRepository;
        this.locationHistoryRepository = locationHistoryRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.locationMap = locationMap;
        this.excelService = excelService;
    }

    @GetMapping("/")
    public RedirectView redirectToDashboard() {
        return new RedirectView("/dashboard");
    }

    @GetMapping("/upload-new")
    public String showUploadNewPage(Model model) {
        model.addAttribute("nextImportName", barcodeService.getNextImportName());
        model.addAttribute("importHistory", barcodeService.findAllImportBatches());
        return "admin/upload-new";
    }

    @GetMapping("/warehouse")
    public String showWarehousePage(
            @RequestParam(required = false) String apn,
            @RequestParam(required = false) String rack, // Початкові значення з запиту
            @RequestParam(required = false) String bay,  // Початкові значення з запиту
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Model model) {

        String currentApn = (apn != null && !apn.isBlank()) ? apn.trim() : null;
        String currentRack = (rack != null && !rack.isBlank()) ? rack.trim() : null;
        String currentBay = (bay != null && !bay.isBlank()) ? bay.trim() : null;

        // --- НОВА ЛОГІКА ---
        // Якщо виконується пошук за APN, скидаємо фільтри rack та bay
        if (currentApn != null) {
            currentRack = null; // Скидаємо стелаж
            currentBay = null;  // Скидаємо проліт
        }
        // --- КІНЕЦЬ НОВОЇ ЛОГІКИ ---

        // Додаємо ПОТОЧНІ значення фільтрів у модель
        model.addAttribute("apnQuery", currentApn);
        model.addAttribute("rackQuery", currentRack); // Використовуємо можливо скинуті значення
        model.addAttribute("bayQuery", currentBay);   // Використовуємо можливо скинуті значення

        // Додаємо списки для випадаючих меню
        model.addAttribute("allRacks", barcodeService.getAllRacks());
        model.addAttribute("allBays", barcodeService.getAllBays());
        model.addAttribute("locationMap", this.locationMap);

        if (currentApn != null) {
            // Режим 1: Пошук за APN
            Map<LocationDTO, List<Barcode>> materialLocations =
                    barcodeService.findMaterialByApnGroupedByLocation(currentApn); // Використовуємо currentApn
            model.addAttribute("materialLocations", materialLocations);
            model.addAttribute("warehousePage", Page.empty());
            model.addAttribute("totalPages", 0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("pageSize", size);

        } else {
            // Режим 2: Огляд складу з фільтрами rack/bay (або без них)
            Pageable pageable = PageRequest.of(page, size); // Сортування визначається в сервісі
            // Передаємо можливо скинуті currentRack / currentBay
            Page<Barcode> warehousePage = barcodeService.findWarehouseView(currentRack, currentBay, pageable);

            model.addAttribute("warehousePage", warehousePage);
            model.addAttribute("totalPages", warehousePage.getTotalPages());
            model.addAttribute("currentPage", warehousePage.getNumber());
            model.addAttribute("pageSize", size);
            model.addAttribute("materialLocations", Map.of());
        }
        return "admin/warehouse-view";
    }

    @PostMapping("/upload-new")
    @Transactional // Додаємо @Transactional для надійності
    public String uploadNewBarcodes(@RequestParam("file") MultipartFile file,
                                    RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Будь ласка, оберіть файл для завантаження.");
            return "redirect:/upload-new";
        }

        try {
            long startTime = System.currentTimeMillis(); // Для вимірювання часу

            // 1. Швидко парсимо файл (це вже оптимізовано завдяки StreamingReader)
            List<Barcode> allBarcodes = excelService.parseExcel(file);
            if (allBarcodes.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Файл порожній або не містить коректних даних.");
                return "redirect:/upload-new";
            }
            log.info("Parsed {} barcodes from file in {} ms.", allBarcodes.size(), System.currentTimeMillis() - startTime);

            // 2. Фільтруємо дублікати ВСЕРЕДИНІ файлу (швидко, в пам'яті)
            Set<String> processedCodesInFile = new HashSet<>();
            List<Barcode> uniqueBarcodesFromFile = new ArrayList<>();
            int skippedFileDuplicates = 0;

            for (Barcode barcode : allBarcodes) {
                if (processedCodesInFile.add(barcode.getCode())) {
                    uniqueBarcodesFromFile.add(barcode);
                } else {
                    skippedFileDuplicates++;
                }
            }

            if (uniqueBarcodesFromFile.isEmpty()) {
                redirectAttributes.addFlashAttribute("message",
                        String.format("Нових унікальних штрих-кодів для додавання не знайдено (пропущено дублікатів з файлу: %d).",
                                skippedFileDuplicates));
                return "redirect:/upload-new";
            }
            log.info("Filtered to {} unique barcodes ({} duplicates found in file).", uniqueBarcodesFromFile.size(), skippedFileDuplicates);

            // 3. Збираємо унікальні коди для ОДНОГО запиту в БД
            List<String> codesToVerify = uniqueBarcodesFromFile.stream()
                    .map(Barcode::getCode)
                    .collect(Collectors.toList());

            // 4. Виконуємо ОДИН запит, щоб знайти всі існуючі коди
            long dbCheckStartTime = System.currentTimeMillis();
            Set<String> existingCodesInDb = barcodeRepository.findExistingCodesByCodeIn(codesToVerify);
            log.info("Checked {} codes against DB in {} ms. Found {} existing.",
                    codesToVerify.size(), System.currentTimeMillis() - dbCheckStartTime, existingCodesInDb.size());

            // 5. Фільтруємо ті, що вже є в БД (швидко, в пам'яті)
            List<Barcode> newBarcodesToSave = new ArrayList<>();
            int skippedDbDuplicates = 0;
            for (Barcode barcode : uniqueBarcodesFromFile) {
                if (!existingCodesInDb.contains(barcode.getCode())) {
                    newBarcodesToSave.add(barcode);
                } else {
                    skippedDbDuplicates++;
                }
            }

            // 6. Зберігаємо тільки фінальний список (якщо він не порожній)
            if (!newBarcodesToSave.isEmpty()) {
                long saveStartTime = System.currentTimeMillis();
                // Викликаємо ваш сервіс, який коректно створює ImportBatch та історію
                barcodeService.saveAll(newBarcodesToSave);
                log.info("Saved {} new barcodes in {} ms.", newBarcodesToSave.size(), System.currentTimeMillis() - saveStartTime);
            }

            // 7. Оновлюємо повідомлення
            redirectAttributes.addFlashAttribute("message",
                    String.format("Файл успішно оброблено. Додано нових: %d. Пропущено (дублікати у файлі): %d. Пропущено (вже існували в БД): %d.",
                            newBarcodesToSave.size(), skippedFileDuplicates, skippedDbDuplicates));

        } catch (Exception e) {
            log.error("Помилка під час обробки файлу /upload-new", e); // Додано логування
            redirectAttributes.addFlashAttribute("error", "Сталася помилка під час обробки файлу: " + e.getMessage());
        }
        return "redirect:/upload-new";
    }

    @GetMapping("/import/{id}")
    public String showImportDetails(@PathVariable Long id,
                                    @RequestParam(defaultValue = "0") int page,
                                    Model model) {
        int size = 50;
        Pageable pageable = PageRequest.of(page, size, Sort.by("creationDate").descending());
        Page<Barcode> barcodePage = barcodeService.findBarcodesByImportId(id, pageable);

        model.addAttribute("barcodes", barcodePage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", barcodePage.getTotalPages());
        model.addAttribute("importId", id);
        return "admin/import-details";
    }

    // ========================================================================
    // РЕШТА ВАШОГО КОДУ ЗАЛИШАЄТЬСЯ БЕЗ ЗМІН
    // ========================================================================

    @GetMapping("/barcodes/download")
    public void downloadFilteredBarcodes(@RequestParam(value = "apn", required = false) String apn,
                                         HttpServletResponse response) throws IOException {

        // 1. Отримуємо список штрих-кодів (логіка залишилася та ж сама)
        List<Barcode> barcodes = (apn != null && !apn.isEmpty())
                ? barcodeRepository.findByApnContainingIgnoreCaseAndStatusNot(apn, "out")
                : barcodeRepository.findByStatusNot("out");

        // 2. Перевіряємо, чи є що вивантажувати
        if (barcodes.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        // 3. Встановлюємо правильні заголовки для Excel-файлу
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=STAV.xlsx");

        // 4. ВИПРАВЛЕНО: Делегуємо створення файлу надійному сервісу ExcelService
        ExcelService.exportToExcel(barcodes, response.getOutputStream());
    }

    @GetMapping("/dashboard")
    public String getDashboard(Model model) {
        List<Barcode> outdatedBarcodes = barcodeService.findTopOutdatedBarcodes(5);
        model.addAttribute("stats", barcodeService.getDashboardStats());
        model.addAttribute("outdatedBarcodes", outdatedBarcodes);
        model.addAttribute("addedChartData", barcodeService.getMonthlyAddedStats());
        model.addAttribute("discardedChartData", barcodeService.getMonthlyDiscardStats());

        List<RecentActivityDto> recentActivities = barcodeService.getRecentActivity(5); // Отримуємо 5 останніх
        model.addAttribute("recentActivities", recentActivities);
        return "admin/dashboard";
    }

    @GetMapping("/barcodes/download/outdated")
    public void downloadOutdatedBarcodes(HttpServletResponse response) throws IOException {
        List<Barcode> outdatedBarcodes = barcodeService.findAllOutdatedBarcodes();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=outdated-barcodes.xlsx");
        ExcelService.exportToExcel(outdatedBarcodes, response.getOutputStream());
    }

    @GetMapping("/barcodes/{id}")
    public String showBarcodeDetails(@PathVariable Long id, Model model) {
        // Отримуємо Barcode (якщо findById не кидає виняток, інакше .orElseThrow)
        Barcode barcode = barcodeService.findById(id);

        // ВИПРАВЛЕНО: Передаємо об'єкт barcode
        List<LocationHistory> locationHistory = locationHistoryRepository.findByBarcodeIdOrderByChangeTimeDesc(barcode.getId());
        List<StatusHistory> statusHistory = statusHistoryRepository.findByBarcodeIdOrderByChangeTimeDesc(barcode.getId());

        model.addAttribute("barcode", barcode);
        model.addAttribute("locationHistory", locationHistory);
        model.addAttribute("statusHistory", statusHistory);
        model.addAttribute("locationMap", locationMap); // Передача локацій для форми

        return "admin/barcode-details";
    }

    @GetMapping("/barcodes/search")
    public String searchBarcode(@RequestParam("code") String code, RedirectAttributes redirectAttributes) {
        if (!code.matches("\\d+")) {
            redirectAttributes.addFlashAttribute("error", "Невірний формат штрих коду: " + code);
            return "redirect:/dashboard";
        }
        Optional<Barcode> barcodeOpt = barcodeService.findByCode(code);
        return barcodeOpt.map(barcode -> "redirect:/barcodes/" + barcode.getId())
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Штрих код не знайдено: " + code);
                    return "redirect:/dashboard";
                });
    }

    @GetMapping("/barcodes")
    public String viewAllBarcodes(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "500") int size,
                                  @RequestParam(required = false) String apn,
                                  Model model) {
        Page<Barcode> barcodePage = (apn != null && !apn.isEmpty()) ?
                barcodeService.findByApnContainingAndStatusNot(apn, "out", page, size) :
                barcodeService.findAllByStatusNot("out", page, size);
        model.addAttribute("barcodes", barcodePage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", barcodePage.getTotalPages());
        model.addAttribute("apn", apn);
        return "admin/all-barcodes";
    }

    @GetMapping("/transfer")
    public String showTransferForm(Model model) {
        model.addAttribute("locationMap", locationMap);
        return "admin/transfer-barcodes";
    }

    @PostMapping("/transfer")
    public String transferBarcodes(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) boolean isExcess,
                                   @RequestParam("newLocation") String newLocation,
                                   @RequestParam(required = false) String locationNumber,
                                   RedirectAttributes redirectAttributes) {

        // 1. Побудова нової повної локації
        String fullNewLocation;
        if ("prestock".equals(newLocation)) {
            fullNewLocation = "prestock";
        } else {
            StringBuilder locationBuilder = new StringBuilder();
            if (isExcess) {
                locationBuilder.append("excess ");
            }
            locationBuilder.append(newLocation); // Напр. "SK"
            if (locationNumber != null && !locationNumber.isEmpty()) {
                locationBuilder.append(" ").append(locationNumber); // Напр. " 4"
            }
            fullNewLocation = locationBuilder.toString();
        }

        // 2. Решта логіки (залишилася без змін)
        try {
            List<String> codes = excelService.extractCodes(file);
            List<Barcode> barcodes = barcodeRepository.findByCodeIn(codes);

            int transferred = 0;
            int skippedOut = 0;
            int skippedWires = 0;

            List<Barcode> barcodesToUpdate = new ArrayList<>();
            List<LocationHistory> historyToSave = new ArrayList<>();

            for (Barcode barcode : barcodes) {
                if ("wires".equalsIgnoreCase(barcode.getLocation())) {
                    skippedWires++;
                    continue;
                }
                if ("out".equalsIgnoreCase(barcode.getStatus())) {
                    skippedOut++;
                    continue;
                }

                String oldLocation = barcode.getLocation();
                if (!oldLocation.equals(fullNewLocation)) {
                    barcode.setLocation(fullNewLocation);
                    barcode.setLastUpdated(LocalDateTime.now());
                    barcodesToUpdate.add(barcode);
                    historyToSave.add(new LocationHistory(barcode, oldLocation, fullNewLocation, LocalDateTime.now()));
                    transferred++;
                }
            }

            barcodeRepository.saveAll(barcodesToUpdate);
            locationHistoryRepository.saveAll(historyToSave);

            redirectAttributes.addFlashAttribute("message",
                    String.format("Перенесено: %d. Пропущено (статус 'out'): %d. Пропущено (локація 'wires'): %d.",
                            transferred, skippedOut, skippedWires));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Помилка під час обробки: " + e.getMessage());
        }

        return "redirect:/transfer";
    }

    @PostMapping("/transfer-detailed")
    public String transferBarcodesDetailed(@RequestParam("file") MultipartFile file,
                                           RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Будь ласка, виберіть файл для завантаження.");
            return "redirect:/transfer";
        }

        try {
            List<BarcodeTransferDto> transfers = excelService.parseTransfers(file);
            if (transfers.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Файл порожній або не містить даних для обробки.");
                return "redirect:/transfer";
            }

            List<String> codesToUpdate = transfers.stream().map(BarcodeTransferDto::getCode).collect(Collectors.toList());
            List<Barcode> barcodesFromDb = barcodeRepository.findByCodeIn(codesToUpdate);
            Map<String, Barcode> barcodeMap = barcodesFromDb.stream()
                    .collect(Collectors.toMap(Barcode::getCode, Function.identity()));

            // Оновлені лічильники для детального звіту
            int transferred = 0, skippedOut = 0, notFound = 0, skippedWires = 0, invalidLocation = 0, invalidNumber = 0;

            List<Barcode> barcodesToSave = new ArrayList<>();
            List<LocationHistory> historyToSave = new ArrayList<>();

            for (BarcodeTransferDto transfer : transfers) {
                Barcode barcode = barcodeMap.get(transfer.getCode());

                // 1. Базові перевірки штрих-коду
                if (barcode == null) {
                    notFound++;
                    continue;
                }
                if ("wires".equalsIgnoreCase(barcode.getLocation())) {
                    skippedWires++;
                    continue;
                }
                if ("out".equalsIgnoreCase(barcode.getStatus())) {
                    skippedOut++;
                    continue;
                }

                // 2. Валідація нової локації
                String rawLocation = transfer.getLocationName(); // Напр. "excess sk" або "SK"
                String rawNumber = transfer.getLocationNumber(); // Напр. "4"

                boolean isExcess = rawLocation.toLowerCase().contains("excess");
                String baseLocationName = rawLocation.toLowerCase().replace("excess", "").trim(); // Напр. "sk"

                // Знаходимо канонічну назву, ігноруючи регістр (напр. "sk" -> "SK")
                String normalizedLocationKey = findLocationKey(baseLocationName);

                // --- Перевірка 1: Стелаж існує ---
                if (normalizedLocationKey == null) {
                    invalidLocation++;
                    continue;
                }

                // --- Перевірка 2: Номер прольоту коректний ---
                int maxNumber = locationMap.get(normalizedLocationKey);
                int providedNumber = 0;

                if (rawNumber.isEmpty()) {
                    if (maxNumber > 0) {
                        invalidNumber++; // Номер потрібен, але не наданий (напр. для "SK")
                        continue;
                    }
                    // maxNumber == 0 (prestock) і номер порожній - це валідно
                } else {
                    try {
                        providedNumber = Integer.parseInt(rawNumber);
                    } catch (NumberFormatException e) {
                        invalidNumber++; // Не число
                        continue;
                    }
                }

                if (maxNumber == 0 && providedNumber > 0) { // Напр. "prestock 1"
                    invalidNumber++;
                    continue;
                }
                if (maxNumber > 0 && (providedNumber < 1 || providedNumber > maxNumber)) { // Напр. "SK 13"
                    invalidNumber++;
                    continue;
                }

                // 3. Створення фінальної назви локації
                StringBuilder finalLocation = new StringBuilder();
                if (isExcess && !normalizedLocationKey.equals("prestock")) {
                    finalLocation.append("excess ");
                }
                finalLocation.append(normalizedLocationKey);
                if (providedNumber > 0) {
                    finalLocation.append(" ").append(providedNumber);
                }
                String newLocation = finalLocation.toString();

                // 4. Перенесення
                String oldLocation = barcode.getLocation();
                if (!oldLocation.equals(newLocation)) {
                    barcode.setLocation(newLocation);
                    barcode.setLastUpdated(LocalDateTime.now());
                    barcodesToSave.add(barcode);
                    historyToSave.add(new LocationHistory(barcode, oldLocation, newLocation, LocalDateTime.now()));
                    transferred++;
                }
            }

            barcodeRepository.saveAll(barcodesToSave);
            locationHistoryRepository.saveAll(historyToSave);

            // Оновлений детальний звіт
            redirectAttributes.addFlashAttribute("message",
                    String.format("Обробку завершено. Перенесено: %d, Не знайдено: %d, Пропущено ('out'): %d, " +
                                    "Пропущено ('wires'): %d, Невірний стелаж: %d, Невірний прольот: %d.",
                            transferred, notFound, skippedOut, skippedWires, invalidLocation, invalidNumber));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Помилка під час обробки файлу: " + e.getMessage());
        }

        return "redirect:/transfer";
    }

    /**
     * Допоміжний метод для пошуку канонічної назви стелажу.
     * (напр. "sk" -> "SK", "ter.1" -> "Ter.1")
     */
    private String findLocationKey(String rawBaseLocationName) {
        if (rawBaseLocationName == null || rawBaseLocationName.isEmpty()) {
            return null;
        }
        for (String key : this.locationMap.keySet()) {
            if (key.equalsIgnoreCase(rawBaseLocationName)) {
                return key; // Повертає правильний регістр, напр. "SK"
            }
        }
        return null; // Не знайдено
    }

    @PostMapping("/barcodes/{id}/updateLocation")
    public String updateBarcodeLocation(@PathVariable Long id,
                                        @RequestParam String newLocation,
                                        @RequestParam(required = false) String locationNumber,
                                        @RequestParam(required = false) boolean isExcess) {
        Barcode barcode = barcodeService.findById(id);
        if ("wires".equalsIgnoreCase(barcode.getLocation())) {
            return "redirect:/barcodes/" + id;
        }
        String fullLocation;
        if ("prestock".equals(newLocation)) {
            fullLocation = "prestock";
        } else {
            StringBuilder locationBuilder = new StringBuilder();
            if (isExcess) {
                locationBuilder.append("excess ");
            }
            locationBuilder.append(newLocation);
            if (locationNumber != null && !locationNumber.isEmpty()) {
                locationBuilder.append(" ").append(locationNumber);
            }
            fullLocation = locationBuilder.toString();
        }
        barcodeService.updateLocation(id, fullLocation);
        return "redirect:/barcodes/" + id;
    }

    @PostMapping("/barcodes/{id}/updateStatus")
    public String updateStatus(@PathVariable Long id, @RequestParam String newStatus) {
        barcodeService.updateStatus(id, newStatus);
        return "redirect:/barcodes/" + id;
    }

    @GetMapping("/discarded")
    public String getDiscardedBarcodes(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @RequestParam(required = false) String apn,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "500") int size,
                                       Model model) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastUpdated").descending());
        Page<Barcode> barcodePage;
        if (date != null && apn != null && !apn.isBlank()) {
            barcodePage = barcodeService.findOutByDateAndApn(date.atStartOfDay(), date.atTime(LocalTime.MAX), apn, pageable);
        } else if (date != null) {
            barcodePage = barcodeService.findOutByDateRange(date.atStartOfDay(), date.atTime(LocalTime.MAX), pageable);
        } else if (apn != null && !apn.isBlank()) {
            barcodePage = barcodeService.findOutByApn(apn, pageable);
        } else {
            barcodePage = barcodeService.findAllOutSortedByDate(pageable);
        }
        model.addAttribute("discardedBarcodes", barcodePage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", barcodePage.getTotalPages());
        model.addAttribute("apn", apn);
        model.addAttribute("date", date);
        return "admin/discarded";
    }

    @GetMapping("/discarded/export")
    public void exportDiscardedToExcel(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @RequestParam(required = false) String apn,
                                       HttpServletResponse response) throws IOException {

        List<Barcode> bars;

        if (date != null && apn != null && !apn.isBlank()) {
            bars = barcodeService.findOutByDateAndApn(date.atStartOfDay(), date.atTime(LocalTime.MAX), apn);
        } else if (date != null) {
            bars = barcodeService.findOutByDateRange(date.atStartOfDay(), date.atTime(LocalTime.MAX));
        } else if (apn != null && !apn.isBlank()) {
            bars = barcodeService.findOutByApn(apn);
        } else {
            // Якщо без фільтрів — виводимо ВСЕ
            bars = barcodeService.findAllOutSortedByDate();
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=discarded-barcodes.xlsx");

        // ВИПРАВЛЕНО: Викликаємо новий, спеціалізований метод
        ExcelService.exportDiscardedToExcel(bars, response.getOutputStream());
    }

    @GetMapping("/upload-discarded")
    public String showUploadDiscardedForm() {
        return "admin/upload-discarded";
    }

    @PostMapping("/discarded/upload")
    public String uploadDiscardedExcel(@RequestParam("file") MultipartFile file,
                                       RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Файл не вибрано.");
            return "redirect:/upload-discarded";
        }
        try {
            List<String> codesFromExcel = barcodeService.readFirstColumnBarcodes(file, 3000);
            if (codesFromExcel == null) {
                redirectAttributes.addFlashAttribute("error", "Файл містить більше 3000 рядків.");
                return "redirect:/upload-discarded";
            }
            if (codesFromExcel.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "У файлі не знайдено цифрових штрих-кодів у першій колонці.");
                return "redirect:/upload-discarded";
            }

            List<Barcode> barcodesFromDb = barcodeRepository.findByCodeIn(codesFromExcel);
            Map<String, Barcode> barcodeMap = barcodesFromDb.stream()
                    .collect(Collectors.toMap(Barcode::getCode, Function.identity()));

            int updatedCount = 0;
            int alreadyOutCount = 0;
            List<String> notFoundCodes = new ArrayList<>();

            List<Barcode> barcodesToUpdate = new ArrayList<>();
            List<StatusHistory> historyToSave = new ArrayList<>();

            for (String code : codesFromExcel) {
                Barcode barcode = barcodeMap.get(code);
                if (barcode == null) {
                    notFoundCodes.add(code);
                } else if ("out".equalsIgnoreCase(barcode.getStatus())) {
                    alreadyOutCount++;
                } else {
                    String oldStatus = barcode.getStatus();
                    barcode.setStatus("out");
                    barcode.setLastUpdated(LocalDateTime.now());
                    barcodesToUpdate.add(barcode);
                    historyToSave.add(new StatusHistory(barcode, oldStatus, "out", LocalDateTime.now()));
                    updatedCount++;
                }
            }

            if (!barcodesToUpdate.isEmpty()) {
                barcodeRepository.saveAll(barcodesToUpdate);
                statusHistoryRepository.saveAll(historyToSave);
            }

            String message = String.format(
                    "Обробку завершено: Списано: %d. Вже були списані: %d.",
                    updatedCount, alreadyOutCount
            );
            redirectAttributes.addFlashAttribute("message", message);

            // ОНОВЛЕНО: Формуємо повідомлення з HTML-тегами <br>
            if (!notFoundCodes.isEmpty()) {
                String notFoundMessage = "<b>Не знайдено в базі (" + notFoundCodes.size() + " шт):</b><br>"
                        + String.join("<br>", notFoundCodes);
                redirectAttributes.addFlashAttribute("error", notFoundMessage);
            }

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Помилка при обробці файлу: " + e.getMessage());
        }

        return "redirect:/upload-discarded";
    }

    @PostMapping("/barcodes/clear") // Новий ендпоінт для POST-запиту
    public String clearDatabase(RedirectAttributes redirectAttributes) {
        try {
            barcodeService.deleteAllBarcodesAndHistory();
            redirectAttributes.addFlashAttribute("message", "Базу даних штрих-кодів успішно очищено!");
        } catch (Exception e) {
            // Логування помилки можна додати тут
            redirectAttributes.addFlashAttribute("error", "Помилка під час очищення бази даних: " + e.getMessage());
        }
        return "redirect:/barcodes"; // Повертаємо користувача на сторінку зі списком
    }

    @GetMapping("/login")
    public String loginPage() {
        // Просто повертаємо ім'я шаблону login.html
        return "admin/login"; // <-- ПОМИЛКА ТУТ
    }

    // Новий ендпоінт для скачування зведеної таблиці
    @GetMapping("/barcodes/summary/download")
    public void downloadApnSummary(HttpServletResponse response) throws IOException {
        // Встановлюємо тип контенту та заголовок для файлу
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=apn_summary.xlsx");

        // Отримуємо зведені дані з сервісу
        List<ApnSummaryDto> summaryData = barcodeService.getApnSummaryData();

        // Генеруємо та відправляємо Excel-файл
        excelService.exportApnSummaryToExcel(summaryData, response.getOutputStream());
    }

    // Новий ендпоінт для скачування штрих-кодів "До перевірки"
    @GetMapping("/barcodes/check/download")
    public void downloadBarcodesForCheck(HttpServletResponse response) throws IOException {
        // Встановлюємо тип контенту та заголовок для файлу
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=barcodes_for_check.xlsx");

        // Отримуємо дані з сервісу (лише ті, що мають статус 'check')
        List<Barcode> barcodesForCheck = barcodeService.getBarcodesForCheck();

        // Генеруємо та відправляємо Excel-файл, використовуючи існуючий метод
        excelService.exportToExcel(barcodesForCheck, response.getOutputStream());
    }

    @GetMapping("/barcodes/history")
    public String showAllBarcodesHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, // Можна налаштувати розмір сторінки
            Model model) {

        // Створюємо Pageable з сортуванням за датою створення (від найстаріших)
        Pageable pageable = PageRequest.of(page, size, Sort.by("creationDate").ascending());

        // Отримуємо сторінку з усіма штрих-кодами
        // Потрібно додати метод findAllOrderByCreationDateAsc до BarcodeService
        Page<Barcode> barcodeHistoryPage = barcodeService.findAllByOrderByCreationDateAsc(pageable);

        model.addAttribute("barcodePage", barcodeHistoryPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", barcodeHistoryPage.getTotalPages());
        model.addAttribute("pageSize", size); // Додаємо pageSize для нумерації

        return "admin/barcode-history"; // Ім'я нового шаблону
    }

    @GetMapping("/barcodes/history/upload")
    public String showUploadHistoryPage(Model model) {
        // Можна передати якісь атрибути в модель, якщо потрібно
        return "admin/upload-history"; // Повертає ім'я нового шаблону
    }

    @PostMapping("/barcodes/history/upload")
    @Transactional
    public String uploadHistoryBarcodes(@RequestParam("file") MultipartFile file,
                                        RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Будь ласка, оберіть файл для завантаження.");
            return "redirect:/barcodes/history";
        }

        int savedCount = 0;
        int skippedFileDuplicates = 0;
        int skippedDbDuplicates = 0;
        int historyRecordsCreated = 0;

        try {
            long startTime = System.currentTimeMillis(); // Для вимірювання часу

            // 1. Швидко парсимо файл (це вже оптимізовано)
            List<Barcode> barcodesFromFile = excelService.parseHistoryExcel(file);
            if (barcodesFromFile == null || barcodesFromFile.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Файл порожній або не містить коректних даних для імпорту в історію.");
                return "redirect:/barcodes/history";
            }
            log.info("Parsed {} barcodes from history file in {} ms.", barcodesFromFile.size(), System.currentTimeMillis() - startTime);

            // 2. Фільтруємо дублікати ВСЕРЕДИНІ файлу
            Set<String> processedCodesInFile = new HashSet<>();
            List<Barcode> uniqueBarcodesFromFile = new ArrayList<>();

            for (Barcode barcode : barcodesFromFile) {
                if (processedCodesInFile.add(barcode.getCode())) {
                    uniqueBarcodesFromFile.add(barcode);
                } else {
                    skippedFileDuplicates++;
                }
            }

            if (uniqueBarcodesFromFile.isEmpty()) {
                redirectAttributes.addFlashAttribute("message",
                        String.format("Нових унікальних штрих-кодів для додавання не знайдено (пропущено дублікатів з файлу: %d).",
                                skippedFileDuplicates));
                return "redirect:/barcodes/history";
            }
            log.info("Filtered to {} unique barcodes ({} duplicates found in file).", uniqueBarcodesFromFile.size(), skippedFileDuplicates);

            // 3. Збираємо коди для ОДНОГО запиту в БД
            List<String> codesToVerify = uniqueBarcodesFromFile.stream()
                    .map(Barcode::getCode)
                    .collect(Collectors.toList());

            // 4. Виконуємо ОДИН запит, щоб знайти всі існуючі коди
            long dbCheckStartTime = System.currentTimeMillis();
            Set<String> existingCodesInDb = barcodeRepository.findExistingCodesByCodeIn(codesToVerify);
            log.info("Checked {} codes against DB in {} ms. Found {} existing.",
                    codesToVerify.size(), System.currentTimeMillis() - dbCheckStartTime, existingCodesInDb.size());


            // 5. Фільтруємо ті, що вже є в БД
            List<Barcode> barcodesToSave = new ArrayList<>();
            for (Barcode barcode : uniqueBarcodesFromFile) {
                if (!existingCodesInDb.contains(barcode.getCode())) {
                    barcodesToSave.add(barcode);
                } else {
                    skippedDbDuplicates++;
                }
            }

            // 6. Зберігаємо тільки фінальний список (якщо він не порожній)
            if (!barcodesToSave.isEmpty()) {
                long saveStartTime = System.currentTimeMillis();
                List<Barcode> savedBarcodes = barcodeRepository.saveAll(barcodesToSave);
                savedCount = savedBarcodes.size();
                log.info("Saved {} new history barcodes in {} ms.", savedCount, System.currentTimeMillis() - saveStartTime);

                // 7. Створення записів StatusHistory (Ваша логіка для графіка)
                List<StatusHistory> historyToSave = new ArrayList<>();
                for (Barcode savedBarcode : savedBarcodes) {
                    if ("out".equals(savedBarcode.getStatus()) && savedBarcode.getDiscardDate() != null) {
                        StatusHistory historyEntry = new StatusHistory(
                                savedBarcode, null, "out", savedBarcode.getDiscardDate()
                        );
                        historyToSave.add(historyEntry);
                    }
                }
                if (!historyToSave.isEmpty()) {
                    statusHistoryRepository.saveAll(historyToSave);
                    historyRecordsCreated = historyToSave.size();
                    log.info("Created {} status history records.", historyRecordsCreated);
                }

                redirectAttributes.addFlashAttribute("message",
                        String.format("Успішно додано %d штрих-кодів до історії. Створено записів списання: %d. Пропущено дублікатів з файлу: %d. Пропущено існуючих в БД: %d.",
                                savedCount, historyRecordsCreated, skippedFileDuplicates, skippedDbDuplicates));
            } else {
                redirectAttributes.addFlashAttribute("message",
                        String.format("Нових унікальних штрих-кодів для додавання не знайдено. Пропущено дублікатів з файлу: %d. Пропущено існуючих в БД: %d.",
                                skippedFileDuplicates, skippedDbDuplicates));
            }

        } catch (Exception e) {
            log.error("Помилка під час завантаження історії штрих-кодів", e);
            redirectAttributes.addFlashAttribute("error", "Сталася помилка під час обробки файлу: " + e.getMessage());
        }

        return "redirect:/barcodes/history";
    }

}