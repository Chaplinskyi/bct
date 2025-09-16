package ua.karpaty.barcodetracker.Controller;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ua.karpaty.barcodetracker.Entity.Barcode;
import ua.karpaty.barcodetracker.Entity.LocationHistory;
import ua.karpaty.barcodetracker.Entity.StatusHistory;
import ua.karpaty.barcodetracker.Exception.BarcodeNotFoundException;
import ua.karpaty.barcodetracker.Repository.BarcodeRepository;
import ua.karpaty.barcodetracker.Repository.LocationHistoryRepository;
import ua.karpaty.barcodetracker.Repository.StatusHistoryRepository;
import ua.karpaty.barcodetracker.Service.BarcodeService;
import ua.karpaty.barcodetracker.Service.ExcelService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/")
public class AdminController {

    private final BarcodeService barcodeService;
    private final ExcelService excelService;
    private final BarcodeRepository barcodeRepository;
    private final LocationHistoryRepository locationHistoryRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    @Autowired
    public AdminController(BarcodeService barcodeService, ExcelService excelService,
                           BarcodeRepository barcodeRepository,
                           LocationHistoryRepository locationHistoryRepository,
                           StatusHistoryRepository statusHistoryRepository) {
        this.barcodeService = barcodeService;
        this.excelService = excelService;
        this.barcodeRepository = barcodeRepository;
        this.locationHistoryRepository = locationHistoryRepository;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    @GetMapping("/upload")
    public String showUploadForm() {
        return "admin/upload-new"; // Назва Thymeleaf-шаблону з формою завантаження
    }

    @PostMapping("/upload")
    public String uploadExcel(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            List<Barcode> allBarcodes = excelService.parseExcel(file);
            List<Barcode> newBarcodes = new ArrayList<>();
            int skipped = 0;

            for (Barcode barcode : allBarcodes) {
                if (!barcodeRepository.existsByCode(barcode.getCode())) {
                    newBarcodes.add(barcode);
                } else {
                    skipped++;
                }
            }

            barcodeService.saveAll(newBarcodes);
            redirectAttributes.addFlashAttribute("message",
                    "Файл успішно оброблено. Додано: " + newBarcodes.size() +
                            ", не додано (вже існували): " + skipped + ".");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Помилка при завантаженні файлу: " + e.getMessage());
        }

        return "redirect:/upload";
    }

    @GetMapping("/barcodes/download")
    public void downloadFilteredBarcodes(@RequestParam(value = "apn", required = false) String apn,
                                         HttpServletResponse response) throws IOException {
        List<Barcode> barcodes = (apn != null && !apn.isEmpty())
                ? barcodeRepository.findByApnContainingIgnoreCaseAndStatusNot(apn, "out")
                : barcodeRepository.findByStatusNot("out");

        if (barcodes.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Barcodes");

        // Header row
        String[] headers = {"Serial Number", "APN", "Кількість", "Локація", "Статус", "Дата"};
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        // Data rows
        int rowIndex = 1;
        for (Barcode b : barcodes) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(b.getCode());
            row.createCell(1).setCellValue(b.getApn());
            row.createCell(2).setCellValue(b.getQuantity());
            row.createCell(3).setCellValue(b.getLocation());
            row.createCell(4).setCellValue(b.getStatus());
            row.createCell(5).setCellValue(
                    b.getLastUpdated() != null ? b.getLastUpdated().format(formatter) : "");
        }

        // Auto-size columns
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // Set response
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=STAV.xlsx");

        workbook.write(response.getOutputStream());
        workbook.close();
    }


    @GetMapping("/dashboard")
    public String getDashboard(Model model) {
        model.addAttribute("barcodes", barcodeService.findAll());
        return "admin/dashboard";
    }

    @GetMapping("/barcodes/{id}")
    public String viewBarcodeDetails(@PathVariable Long id, Model model) {
        Barcode barcode = barcodeRepository.findById(String.valueOf(id))
                .orElseThrow(() -> new RuntimeException("Barcode not found"));


        List<LocationHistory> locationHistory = locationHistoryRepository.findByBarcodeIdOrderByChangeTimeDesc(id);
        List<StatusHistory> statusHistory = statusHistoryRepository.findByBarcodeIdOrderByChangeTimeDesc(barcode.getId());

        model.addAttribute("barcode", barcode);
        model.addAttribute("locationHistory", locationHistory);
        model.addAttribute("statusHistory", statusHistory);
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
    public String showTransferBarcodesPage() {
        return "admin/transfer-barcodes";
    }

    @PostMapping("/transfer")
    public String transferBarcodes(@RequestParam("file") MultipartFile file,
                                   @RequestParam("newLocation") String newLocation,
                                   @RequestParam("locationNumber") String locationNumber,
                                   RedirectAttributes redirectAttributes) {
        try {
            List<String> codes = excelService.extractCodes(file);
            List<Barcode> barcodes = barcodeRepository.findByCodeIn(codes);

            int transferred = 0;
            int skipped = 0;

            for (Barcode barcode : barcodes) {
                if (barcodes.isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "Штрихкоди з файлу не знайдено у базі.");
                    return "redirect:/transfer";
                }

                if (barcode == null || barcode.getStatus() == null) {
                    skipped++;
                    continue;
                }

                if ("out".equalsIgnoreCase(barcode.getStatus())) {
                    skipped++;
                    continue;
                }

                String oldLocation = barcode.getLocation();
                String fullNewLocation = newLocation + " " + locationNumber;

                if (!oldLocation.equals(fullNewLocation)) {
                    barcode.setLocation(fullNewLocation);
                    barcode.setLastUpdated(LocalDateTime.now());

                    locationHistoryRepository.save(
                            new LocationHistory(barcode, oldLocation, fullNewLocation, LocalDateTime.now())
                    );

                    transferred++;
                }
            }

            barcodeRepository.saveAll(barcodes);

            redirectAttributes.addFlashAttribute("message",
                    "Успішно перенесено: " + transferred +
                            ". Пропущено (статус out): " + skipped + ".");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Помилка під час обробки: " + e.getMessage());
        }

        return "redirect:/transfer";
    }

    @PostMapping("/barcodes/{id}/updateLocation")
    public String updateBarcodeLocation(@PathVariable Long id,
                                        @RequestParam String newLocation,
                                        @RequestParam(required = false) String locationNumber) {

        String fullLocation = "prestock".equals(newLocation)
                ? "prestock"
                : newLocation + " " + locationNumber;

        barcodeService.updateLocation(id, fullLocation); // або логіка напряму

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
                                       Model model) {
        List<Barcode> barcodes;
        if (date != null && apn != null && !apn.isBlank()) {
            barcodes = barcodeService.findOutByDateAndApn(date.atStartOfDay(), date.atTime(LocalTime.MAX), apn);
        } else if (date != null) {
            barcodes = barcodeService.findOutByDateRange(date.atStartOfDay(), date.atTime(LocalTime.MAX));
        } else if (apn != null && !apn.isBlank()) {
            barcodes = barcodeService.findOutByApn(apn);
        } else {
            barcodes = barcodeService.findAllOutSortedByDate();
        }

        model.addAttribute("discardedBarcodes", barcodes);
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
            // Якщо без фільтрів — виводимо все
            Pageable pageable = PageRequest.of(0, 50); // перша сторінка, 50 записів
            Page<Barcode> barcodesPage = barcodeService.findAllByStatus("out", pageable);
            bars = barcodesPage.getContent();
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=discarded-barcodes.xlsx");

        ExcelService.exportToExcel(bars, response.getOutputStream());
    }

    @GetMapping("/upload-discarded")
    public String showUploadDiscardedForm() {
        return "admin/upload-discarded";
    }

    @PostMapping("/upload-discarded")
    public String handleUploadDiscarded(@RequestParam("file") MultipartFile file,
                                        RedirectAttributes redirectAttributes) {
        try {
            // Перевірка розміру файлу
            if (file.getSize() > 10_000_000) {
                redirectAttributes.addFlashAttribute("error", "Файл завеликий (максимум 10 МБ).");
                return "redirect:/upload-discarded";
            }

            List<String> barcodesFromExcel = barcodeService.readFirstColumnBarcodes(file, 3000);

            // Перевищено кількість рядків
            if (barcodesFromExcel == null) {
                redirectAttributes.addFlashAttribute("error", "Файл містить більше 3000 рядків.");
                return "redirect:/upload-discarded";
            }

            // Дані в інших стовпцях (повертає порожній список)
            if (barcodesFromExcel.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Файл повинен містити штрихкоди лише в першому стовпчику.");
                return "redirect:/upload-discarded";
            }

            // Все ок — запускаємо обробку
            CompletableFuture.runAsync(() -> barcodeService.updateBarcodesToOut(barcodesFromExcel));
            redirectAttributes.addFlashAttribute("message",
                    String.format("Завантажено %d штрихкодів для обробки.", barcodesFromExcel.size()));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Помилка при обробці файлу: " + e.getMessage());
        }

        return "redirect:/upload-discarded";
    }

    @PostMapping("/discarded/upload")
    public String uploadDiscardedExcel(@RequestParam("file") MultipartFile file,
                                       RedirectAttributes redirectAttributes) {
        try (InputStream is = file.getInputStream()) {
            Workbook workbook = new XSSFWorkbook(is);
            Sheet sheet = workbook.getSheetAt(0);

            int updatedCount = 0;
            int notFoundCount = 0;
            int alreadyOutCount = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                Cell codeCell = row.getCell(0);
                if (codeCell == null) continue;

                String code;
                if (codeCell.getCellType() == CellType.STRING) {
                    code = codeCell.getStringCellValue().trim();
                } else if (codeCell.getCellType() == CellType.NUMERIC) {
                    code = String.valueOf((long) codeCell.getNumericCellValue()).trim();
                } else {
                    continue;
                }

                if (code.isEmpty()) continue;

                Optional<Barcode> optionalBarcode = barcodeRepository.findByCode(code);
                if (optionalBarcode.isPresent()) {
                    Barcode barcode = optionalBarcode.get();

                    // Ігнорувати, якщо статус вже out
                    if ("out".equalsIgnoreCase(barcode.getStatus())) {
                        alreadyOutCount++;
                        continue;
                    }

                    // Оновити статус
                    barcode.setStatus("out");
                    barcode.setLastUpdated(LocalDateTime.now());

                    // Додати запис у статус-історію
                    StatusHistory history = new StatusHistory();
                    history.setStatus("out");
                    history.setTimestamp(LocalDateTime.now());
                    history.setBarcode(barcode);

                    barcode.getStatusHistory().add(history);

                    barcodeRepository.save(barcode);
                    updatedCount++;
                } else {
                    notFoundCount++;
                    System.out.println("Штрихкод не знайдено: " + code);
                }
            }

            redirectAttributes.addFlashAttribute("message",
                    "Файл оброблено: списано — " + updatedCount +
                            ", вже списані — " + alreadyOutCount +
                            ", не знайдено — " + notFoundCount + ".");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Помилка при обробці файлу: " + e.getMessage());
        }

        return "redirect:/upload-discarded";
    }

    @ControllerAdvice
    public static class GlobalExceptionHandler {

        @ExceptionHandler(BarcodeNotFoundException.class)
        public String handleBarcodeNotFound(BarcodeNotFoundException ex, Model model) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "error/404";
        }
    }
}
