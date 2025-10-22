package ua.karpaty.barcodetracker.Controller;

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
import ua.karpaty.barcodetracker.Entity.*;
import ua.karpaty.barcodetracker.Exception.BarcodeNotFoundException;
import ua.karpaty.barcodetracker.Repository.BarcodeRepository;
import ua.karpaty.barcodetracker.Repository.LocationHistoryRepository;
import ua.karpaty.barcodetracker.Repository.StatusHistoryRepository;
import ua.karpaty.barcodetracker.Service.BarcodeService;
import ua.karpaty.barcodetracker.Service.ExcelService;
import java.util.Optional;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/")
public class AdminController {

    private final BarcodeService barcodeService;
    private final ExcelService excelService;
    private final BarcodeRepository barcodeRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final Map<String, Integer> locationMap;

    @Autowired
    public AdminController(BarcodeService barcodeService, ExcelService excelService,
                           BarcodeRepository barcodeRepository,
                           LocationHistoryRepository locationHistoryRepository,
                           StatusHistoryRepository statusHistoryRepository, Map<String, Integer> locationMap) {
        this.barcodeService = barcodeService;
        this.excelService = excelService;
        this.barcodeRepository = barcodeRepository;
        this.locationHistoryRepository = locationHistoryRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.locationMap = locationMap;
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
            @RequestParam(required = false) String rack,
            @RequestParam(required = false) String bay,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        // Додаємо фільтри у модель
        model.addAttribute("apnQuery", apn);
        model.addAttribute("rackQuery", rack);
        model.addAttribute("bayQuery", bay);

        // --- НОВИЙ КОД ---
        // Додаємо списки для випадаючих меню (dropdowns)
        model.addAttribute("allRacks", barcodeService.getAllRacks());
        model.addAttribute("allBays", barcodeService.getAllBays());
        // --- КІНЕЦЬ НОВОГО КОДУ ---

        int pageSize = 50;

        if (apn != null && !apn.isBlank()) {
            // Режим 1: Пошук за APN
            Map<LocationDTO, List<Barcode>> materialLocations =
                    barcodeService.findMaterialByApnGroupedByLocation(apn);
            model.addAttribute("materialLocations", materialLocations);
            model.addAttribute("warehousePage", Page.empty());
        } else {
            // Режим 2: Огляд складу
            Pageable pageable = PageRequest.of(page, pageSize, Sort.by("location").ascending());
            // Викликає оновлений метод сервісу
            Page<Barcode> warehousePage = barcodeService.findWarehouseView(rack, bay, pageable);
            model.addAttribute("warehousePage", warehousePage);
            model.addAttribute("totalPages", warehousePage.getTotalPages());
            model.addAttribute("currentPage", warehousePage.getNumber());
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("materialLocations", Map.of());
        }
        return "admin/warehouse-view";
    }

    @PostMapping("/upload-new")
    public String uploadNewBarcodes(@RequestParam("file") MultipartFile file,
                                    RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Будь ласка, оберіть файл для завантаження.");
            return "redirect:/upload-new";
        }
        try {
            List<Barcode> allBarcodes = excelService.parseExcel(file);
            if (allBarcodes.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Файл порожній або не містить коректних даних.");
                return "redirect:/upload-new";
            }

            // Перевіряємо, які штрих-коди вже існують
            List<Barcode> newBarcodes = new ArrayList<>();
            int skipped = 0;
            for (Barcode barcode : allBarcodes) {
                if (!barcodeRepository.existsByCode(barcode.getCode())) {
                    newBarcodes.add(barcode);
                } else {
                    skipped++;
                }
            }

            // Зберігаємо тільки нові, якщо вони є
            if (!newBarcodes.isEmpty()) {
                barcodeService.saveAll(newBarcodes);
            }

            redirectAttributes.addFlashAttribute("message",
                    "Файл успішно оброблено. Додано нових: " + newBarcodes.size() +
                            ", пропущено (вже існували): " + skipped + ".");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Сталася помилка під час обробки файлу: " + e.getMessage());
            e.printStackTrace();
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

}