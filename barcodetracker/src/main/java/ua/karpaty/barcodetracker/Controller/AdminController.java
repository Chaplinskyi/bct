package ua.karpaty.barcodetracker.Controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.servlet.view.RedirectView;
import ua.karpaty.barcodetracker.Dto.*;
import ua.karpaty.barcodetracker.Entity.*;
import ua.karpaty.barcodetracker.Entity.Frozen.FrozenBarcode;
import ua.karpaty.barcodetracker.Repository.BarcodeRepository;
import ua.karpaty.barcodetracker.Repository.Frozen.FrozenBarcodeRepository;
import ua.karpaty.barcodetracker.Repository.LocationHistoryRepository;
import ua.karpaty.barcodetracker.Repository.MaterialMasterRepository;
import ua.karpaty.barcodetracker.Repository.StatusHistoryRepository;
import ua.karpaty.barcodetracker.Service.BarcodeService;
import ua.karpaty.barcodetracker.Service.ExcelService;
import ua.karpaty.barcodetracker.Service.FrozenDataService;
import ua.karpaty.barcodetracker.Service.MaterialMasterService;

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

    private final BarcodeService barcodeService;
    private final ExcelService excelService;
    private final BarcodeRepository barcodeRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final Map<String, Integer> locationMap;

    private final MaterialMasterService materialMasterService;
    private final MaterialMasterRepository materialMasterRepository;

    private final ObjectMapper objectMapper;

    private final FrozenDataService frozenDataService;

    private final FrozenBarcodeRepository frozenBarcodeRepository;

    @Autowired
    public AdminController(BarcodeService barcodeService, ExcelService excelService,
                           BarcodeRepository barcodeRepository,
                           LocationHistoryRepository locationHistoryRepository,
                           StatusHistoryRepository statusHistoryRepository, Map<String, Integer> locationMap,
                           MaterialMasterService materialMasterService,
                           MaterialMasterRepository materialMasterRepository,
                           ObjectMapper objectMapper,
                           FrozenDataService frozenDataService, FrozenBarcodeRepository frozenBarcodeRepository) {
        this.barcodeService = barcodeService;
        this.excelService = excelService;
        this.barcodeRepository = barcodeRepository;
        this.locationHistoryRepository = locationHistoryRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.locationMap = locationMap;
        this.materialMasterService = materialMasterService;
        this.materialMasterRepository = materialMasterRepository;
        this.objectMapper = objectMapper;
        this.frozenDataService = frozenDataService;
        this.frozenBarcodeRepository = frozenBarcodeRepository;
    }

    @GetMapping("/")
    public RedirectView redirectToDashboard() {
        return new RedirectView("/dashboard");
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", "Невірний логін або пароль!");
        }
        return "admin/login";
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
            @RequestParam(defaultValue = "50") int size,
            Model model) {

        String currentApn = (apn != null && !apn.isBlank()) ? apn.trim() : null;
        String currentRack = (rack != null && !rack.isBlank()) ? rack.trim() : null;
        String currentBay = (bay != null && !bay.isBlank()) ? bay.trim() : null;

        if (currentApn != null) {
            currentRack = null;
            currentBay = null;
        }

        model.addAttribute("apnQuery", currentApn);
        model.addAttribute("rackQuery", currentRack);
        model.addAttribute("bayQuery", currentBay);

        model.addAttribute("allRacks", barcodeService.getAllRacks());
        model.addAttribute("allBays", barcodeService.getAllBays());
        model.addAttribute("locationMap", this.locationMap);

        if (currentApn != null) {
            Map<LocationDTO, List<Barcode>> materialLocations =
                    barcodeService.findMaterialByApnGroupedByLocation(currentApn);
            model.addAttribute("materialLocations", materialLocations);
            model.addAttribute("warehousePage", Page.empty());
            model.addAttribute("totalPages", 0);
            model.addAttribute("currentPage", 0);
            model.addAttribute("pageSize", size);

        } else {
            Pageable pageable = PageRequest.of(page, size);
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

            // --- ОНОВЛЕНА ЛОГІКА: Збираємо пропущені коди ---

            // 1. Збираємо коди з файлу (зберігаючи дублікати файлу)
            List<String> codesFromFile = allBarcodes.stream()
                    .map(Barcode::getCode)
                    .collect(Collectors.toList());

            // 2. Знаходимо всі коди, які ВЖЕ існують в "гарячій" (PostgreSQL) базі
            List<Barcode> existingHotBarcodes = barcodeRepository.findByCodeIn(codesFromFile);
            Set<String> hotCodesSet = existingHotBarcodes.stream()
                    .map(Barcode::getCode)
                    .collect(Collectors.toSet());

            // 3. Знаходимо всі коди, які ВЖЕ існують в "замороженій" (SQLite) базі
            List<FrozenBarcode> existingFrozenBarcodes = frozenBarcodeRepository.findByCodeIn(codesFromFile);
            Set<String> frozenCodesSet = existingFrozenBarcodes.stream()
                    .map(FrozenBarcode::getCode)
                    .collect(Collectors.toSet());

            // 4. Створюємо єдиний набір ВСІХ існуючих кодів
            Set<String> allExistingCodes = new HashSet<>(hotCodesSet);
            allExistingCodes.addAll(frozenCodesSet);

            // 5. Розділяємо штрих-коди з файлу на два списки
            List<Barcode> newBarcodes = new ArrayList<>();
            List<String> skippedCodes = new ArrayList<>(); // <--- Новий список для помилок

            Set<String> codesProcessed = new HashSet<>(); // Допоміжний сет, щоб уникнути дублікатів з самого файлу

            for (Barcode barcode : allBarcodes) {
                String code = barcode.getCode();
                if (codesProcessed.add(code)) { // .add() поверне true, якщо код зустрічається ВПЕРШЕ
                    if (allExistingCodes.contains(code)) {
                        skippedCodes.add(code); // Код існує в БД
                    } else {
                        newBarcodes.add(barcode); // Код унікальний
                    }
                } else {
                    // Цей код - дублікат ВЖЕ В САМОМУ ФАЙЛІ. Також додаємо до пропущених.
                    skippedCodes.add(code + " (дублікат у файлі)");
                }
            }
            // --- КІНЕЦЬ ОНОВЛЕНОЇ ЛОГІКИ ---

            // Зберігаємо тільки нові, якщо вони є
            if (!newBarcodes.isEmpty()) {
                barcodeService.saveAll(newBarcodes);
            }

            // Передаємо повідомлення на сторінку
            redirectAttributes.addFlashAttribute("message",
                    "Файл успішно оброблено. Додано нових: " + newBarcodes.size() + ".");

            if (!skippedCodes.isEmpty()) {
                // Передаємо список пропущених кодів
                redirectAttributes.addFlashAttribute("skippedCodes", skippedCodes);
            }

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

    @GetMapping("/barcodes/download")
    public void downloadFilteredBarcodes(@RequestParam(value = "apn", required = false) String apn,
                                         HttpServletResponse response) throws IOException {

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=STAVEXP.xlsx");

        try {
            if (apn != null && !apn.isEmpty()) {
                barcodeService.streamBarcodesByApnToExcel(apn, response.getOutputStream());
            } else {
                barcodeService.streamAllBarcodesToExcel(response.getOutputStream());
            }
        } catch (Exception e) {
            System.out.println("Помилка під час стрімінгу Excel-файлу");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/dashboard")
    public String getDashboard(Model model) {
        List<Barcode> outdatedBarcodes = barcodeService.findTopOutdatedBarcodes(5);
        model.addAttribute("stats", barcodeService.getDashboardStats());
        model.addAttribute("outdatedBarcodes", outdatedBarcodes);
        model.addAttribute("activityLog", barcodeService.getRecentActivities(5));

        model.addAttribute("addedChartData", barcodeService.getMonthlyAddedStats());
        model.addAttribute("discardedChartData", barcodeService.getMonthlyDiscardStats());

        model.addAttribute("mismatches", barcodeService.findLocationMismatches(5));

        ChartDataDto fillRateData = barcodeService.getWarehouseFillRate(10);
        model.addAttribute("fillRateChartData", fillRateData);

        try {
            String fillRateJson = objectMapper.writeValueAsString(fillRateData);
            model.addAttribute("fillRateChartDataJson", fillRateJson);
        } catch (Exception e) {
            model.addAttribute("fillRateChartDataJson", "{}");
        }

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
        Barcode barcode = barcodeService.findById(id);

        List<LocationHistory> locationHistory = locationHistoryRepository.findByBarcodeIdOrderByChangeTimeDesc(barcode.getId());
        List<StatusHistory> statusHistory = statusHistoryRepository.findByBarcodeIdOrderByChangeTimeDesc(barcode.getId());

        model.addAttribute("barcode", barcode);
        model.addAttribute("locationHistory", locationHistory);
        model.addAttribute("statusHistory", statusHistory);
        model.addAttribute("locationMap", locationMap);

        if (barcode.getApn() != null && !barcode.getApn().isEmpty()) {
            Optional<MaterialMaster> masterDataOpt = materialMasterRepository.findByApn(barcode.getApn());
            masterDataOpt.ifPresent(masterData -> model.addAttribute("masterData", masterData));
        }

        return "admin/barcode-details";
    }

    @GetMapping("/barcodes/search")
    public String searchBarcode(@RequestParam("code") String code, RedirectAttributes redirectAttributes, Model model) {
        if (code == null || code.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Пошуковий запит не може бути порожнім.");
            return "redirect:/dashboard";
        }

        String trimmedCode = code.trim();

        // 1. Пошук у "гарячій" (PostgreSQL) базі
        Optional<Barcode> barcodeOpt = barcodeService.findByCode(trimmedCode);
        if (barcodeOpt.isPresent()) {
            // Знайдено в активній БД, переходимо на звичайну сторінку деталей
            return "redirect:/barcodes/" + barcodeOpt.get().getId();
        }

        // 2. Якщо не знайдено, шукаємо у "замороженій" (SQLite) базі
        Optional<FrozenBarcode> frozenBarcodeOpt = frozenDataService.findByCode(trimmedCode);
        if (frozenBarcodeOpt.isPresent()) {
            // Знайдено в "замороженій" БД, показуємо нову сторінку архіву
            model.addAttribute("barcode", frozenBarcodeOpt.get());
            return "admin/frozen-barcode-details";
        }

        // 3. Не знайдено ніде
        redirectAttributes.addFlashAttribute("error", "Штрих код не знайдено: " + trimmedCode);
        return "redirect:/dashboard";
    }

    @GetMapping("/barcodes")
    public String viewAllBarcodes(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size,
                                  @RequestParam(required = false) String apn,
                                  Model model) {
        Page<Barcode> barcodePage = (apn != null && !apn.isEmpty()) ?
                barcodeService.findByApnContainingAndStatusNot(apn, "out", page, size) :
                barcodeService.findAllByStatusNot("out", page, size);

        model.addAttribute("barcodes", barcodePage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", barcodePage.getTotalPages());
        model.addAttribute("apn", apn);
        model.addAttribute("pageSize", size);

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

        String fullNewLocation;
        if ("prestock".equals(newLocation)) {
            fullNewLocation = "prestock";
        } else {
            StringBuilder locationBuilder = new StringBuilder();
            if (isExcess) {
                locationBuilder.append("excess ");
            }
            locationBuilder.append(newLocation);
            if (locationNumber != null && !locationNumber.isEmpty()) {
                locationBuilder.append(" ").append(locationNumber);
            }
            fullNewLocation = locationBuilder.toString();
        }

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

            int transferred = 0, skippedOut = 0, notFound = 0, skippedWires = 0, invalidLocation = 0, invalidNumber = 0;

            List<Barcode> barcodesToSave = new ArrayList<>();
            List<LocationHistory> historyToSave = new ArrayList<>();

            for (BarcodeTransferDto transfer : transfers) {
                Barcode barcode = barcodeMap.get(transfer.getCode());

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

                String rawLocation = transfer.getLocationName();
                String rawNumber = transfer.getLocationNumber();

                boolean isExcess = rawLocation.toLowerCase().contains("excess");
                String baseLocationName = rawLocation.toLowerCase().replace("excess", "").trim();

                String normalizedLocationKey = findLocationKey(baseLocationName);

                if (normalizedLocationKey == null) {
                    invalidLocation++;
                    continue;
                }

                int maxNumber = locationMap.get(normalizedLocationKey);
                int providedNumber = 0;

                if (rawNumber.isEmpty()) {
                    if (maxNumber > 0) {
                        invalidNumber++;
                        continue;
                    }
                } else {
                    try {
                        providedNumber = Integer.parseInt(rawNumber);
                    } catch (NumberFormatException e) {
                        invalidNumber++;
                        continue;
                    }
                }

                if (maxNumber == 0 && providedNumber > 0) {
                    invalidNumber++;
                    continue;
                }
                if (maxNumber > 0 && (providedNumber < 1 || providedNumber > maxNumber)) {
                    invalidNumber++;
                    continue;
                }

                StringBuilder finalLocation = new StringBuilder();
                if (isExcess && !normalizedLocationKey.equals("prestock")) {
                    finalLocation.append("excess ");
                }
                finalLocation.append(normalizedLocationKey);
                if (providedNumber > 0) {
                    finalLocation.append(" ").append(providedNumber);
                }
                String newLocation = finalLocation.toString();

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

            redirectAttributes.addFlashAttribute("message",
                    String.format("Обробку завершено. Перенесено: %d, Не знайдено: %d, Пропущено ('out'): %d, " +
                                    "Пропущено ('wires'): %d, Невірний стелаж: %d, Невірний прольот: %d.",
                            transferred, notFound, skippedOut, skippedWires, invalidLocation, invalidNumber));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Помилка під час обробки файлу: " + e.getMessage());
        }

        return "redirect:/transfer";
    }

    private String findLocationKey(String rawBaseLocationName) {
        if (rawBaseLocationName == null || rawBaseLocationName.isEmpty()) {
            return null;
        }
        for (String key : this.locationMap.keySet()) {
            if (key.equalsIgnoreCase(rawBaseLocationName)) {
                return key;
            }
        }
        return null;
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
    public String getDiscardedBarcodes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String apn,
            @RequestParam(required = false) Boolean searchArchive, // <--- НОВИЙ ПАРАМЕТР
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("lastUpdated").descending());

        // --- НОВА ЛОГІКА ---
        String filterApn = (apn != null && !apn.isBlank()) ? apn.trim() : null;
        LocalDate filterDate = date;
        boolean isArchiveSearch = searchArchive != null && searchArchive; // true, якщо галочка стоїть

        // Встановлюємо дату "сьогодні" за замовчуванням, ТІЛЬКИ якщо це НЕ пошук по архіву
        if (filterApn == null && date == null && apn == null && !isArchiveSearch) {
            filterDate = LocalDate.now();
        }

        Page<DiscardedBarcodeDto> dtoPage;

        if (isArchiveSearch) {
            // --- РЕЖИМ 1: Пошук в "Замороженій" БД (SQLite) ---
            // Сортуємо за датою списання
            Pageable frozenPageable = PageRequest.of(page, size, Sort.by("dateDiscarded").descending());
            Page<FrozenBarcode> frozenPage;

            if (filterDate != null && filterApn != null) {
                frozenPage = frozenBarcodeRepository.findByApnContainingIgnoreCaseAndDateDiscardedBetween(filterApn, filterDate, filterDate, frozenPageable);
            } else if (filterDate != null) {
                frozenPage = frozenBarcodeRepository.findByDateDiscardedBetween(filterDate, filterDate, frozenPageable);
            } else if (filterApn != null) {
                frozenPage = frozenBarcodeRepository.findByApnContainingIgnoreCase(filterApn, frozenPageable);
            } else {
                frozenPage = frozenBarcodeRepository.findAll(frozenPageable);
            }
            // Конвертуємо Page<FrozenBarcode> -> Page<DiscardedBarcodeDto>
            dtoPage = frozenPage.map(DiscardedBarcodeDto::new);

        } else {
            // --- РЕЖИМ 2: Пошук в "Гарячій" БД (PostgreSQL), як і раніше ---
            Page<Barcode> barcodePage;
            if (filterDate != null && filterApn != null) {
                barcodePage = barcodeService.findOutByDateAndApn(filterDate.atStartOfDay(), filterDate.atTime(LocalTime.MAX), filterApn, pageable);
            } else if (filterDate != null) {
                barcodePage = barcodeService.findOutByDateRange(filterDate.atStartOfDay(), filterDate.atTime(LocalTime.MAX), pageable);
            } else if (filterApn != null) {
                barcodePage = barcodeService.findOutByApn(filterApn, pageable);
            } else {
                barcodePage = barcodeService.findAllOutSortedByDate(pageable);
            }
            // Конвертуємо Page<Barcode> -> Page<DiscardedBarcodeDto>
            dtoPage = barcodePage.map(DiscardedBarcodeDto::new);
        }

        model.addAttribute("discardedBarcodes", dtoPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", dtoPage.getTotalPages());
        model.addAttribute("apn", filterApn);
        model.addAttribute("date", filterDate);
        model.addAttribute("searchArchive", isArchiveSearch);

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
            bars = barcodeService.findAllOutSortedByDate();
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=discarded-barcodes.xlsx");

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
            Map<String, List<String>> extractResult = barcodeService.readFirstColumnBarcodes(file, 3000);

            if (extractResult == null) {
                redirectAttributes.addFlashAttribute("error", "Файл містить більше 3000 рядків.");
                return "redirect:/upload-discarded";
            }

            List<String> codesFromExcel = extractResult.get("codesToProcess");
            List<String> duplicateCodes = extractResult.get("duplicateCodes");

            if (codesFromExcel.isEmpty() && duplicateCodes.isEmpty()) {
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
                    if (duplicateCodes.contains(code)) {
                        continue;
                    }

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

            Set<String> allErrors = new HashSet<>(notFoundCodes);
            allErrors.addAll(duplicateCodes);

            if (!allErrors.isEmpty()) {
                String notFoundMessage = "<b>Не оброблено (" + allErrors.size() + " шт) - не знайдено або дублікати:</b><br>"
                        + String.join("<br>", allErrors);
                redirectAttributes.addFlashAttribute("error", notFoundMessage);
            }

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Помилка при обробці файлу: " + e.getMessage());
        }

        return "redirect:/upload-discarded";
    }

    @GetMapping("/barcodes/download/summary")
    public void downloadApnSummary(HttpServletResponse response) throws IOException {

        List<ApnSummaryDto> summaryList = barcodeService.getApnSummary();

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=RAWEXP.xlsx");

        ExcelService.exportApnSummaryToExcel(summaryList, response.getOutputStream());
    }

    @GetMapping("/import-master")
    public String showImportMasterForm() {
        return "admin/import-master";
    }

    @PostMapping("/import-master")
    public String handleImportMaster(@RequestParam("file") MultipartFile file,
                                     RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Будь ласка, оберіть .csv файл.");
            return "redirect:admin/import-master";
        }
        try {
            String message = materialMasterService.importDataFromCsv(file.getInputStream());
            redirectAttributes.addFlashAttribute("message", message);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Помилка під час імпорту: " + e.getMessage());
        }
        return "redirect:admin/import-master";
    }

    @GetMapping("/materials")
    public String viewAllMaterials(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size,
                                   @RequestParam(required = false) String apn,
                                   Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("apn").ascending());
        Page<MaterialMaster> materialPage;

        if (apn != null && !apn.isEmpty()) {
            materialPage = materialMasterRepository.findByApnContainingIgnoreCase(apn.trim(), pageable);
        } else {
            materialPage = materialMasterRepository.findAll(pageable);
        }

        model.addAttribute("materialPage", materialPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", materialPage.getTotalPages());
        model.addAttribute("apnQuery", apn);

        return "admin/material-master-view";
    }

    @GetMapping("/import-frozen")
    public String showImportFrozenForm() {
        return "admin/import-frozen";
    }

    @PostMapping("/import-frozen")
    public String handleImportFrozen(@RequestParam("files") MultipartFile[] files,
                                     RedirectAttributes redirectAttributes) {

        if (files.length == 0 || files[0].isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Будь ласка, оберіть хоча б один .csv файл.");
            return "redirect:/import-frozen";
        }

        List<String> messages = new ArrayList<>();
        try {
            // === ЗМІНА ТУТ ===
            // Ми більше не передаємо весь масив у сервіс.
            // Ми викликаємо сервіс 4 рази. Кожен виклик = 1 нова транзакція.
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                String message = frozenDataService.importFromCsv(file); // Викликаємо оновлений публічний метод
                messages.add(message);
            }
            redirectAttributes.addFlashAttribute("messages", messages);

        } catch (Exception e) {
            // Якщо будь-який файл "впаде" з критичною помилкою (не дублікат),
            // ми перехопимо її тут.
            redirectAttributes.addFlashAttribute("error", "Сталася критична помилка під час імпорту: " + e.getMessage());
        }
        return "redirect:/import-frozen";
    }


}