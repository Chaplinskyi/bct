package ua.karpaty.barcodetracker.Service;

// --- ОНОВЛЕНІ ТА ДОДАНІ ІМПОРТИ ---
import com.github.pjfanning.xlsx.StreamingReader; // <-- ПРАВИЛЬНИЙ імпорт потокового рідера
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ua.karpaty.barcodetracker.Dto.ApnSummaryDto;
import ua.karpaty.barcodetracker.Dto.BarcodeTransferDto;
import ua.karpaty.barcodetracker.Entity.Barcode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId; // <-- ДОДАНО: для конвертації дати
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
// ----------------------------

@Service
public class ExcelService {

    // Створимо список префіксів для "wires" (Без змін)
    private static final List<String> WIRES_APN_PREFIXES = Arrays.asList("M3130", "M3232", "M3362", "M68");
    // Винесемо форматер дати в константу для перевикористання
    private static final DateTimeFormatter DATE_FORMATTER_DDMMYYYY = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * ОПТИМІЗОВАНО: Використовує StreamingReader для низького споживання пам'яті.
     */
    public List<Barcode> parseExcel(MultipartFile file) throws IOException {
        List<Barcode> barcodes = new ArrayList<>();

        // ОНОВЛЕНО: Замість XSSFWorkbook використовуємо StreamingReader
        try (InputStream is = file.getInputStream();
             Workbook workbook = StreamingReader.builder()
                     .rowCacheSize(100)    // Кешувати 100 рядків в пам'яті
                     .bufferSize(4096)     // Розмір буфера читання
                     .open(is)) {          // Відкрити потік

            Sheet sheet = workbook.getSheetAt(0);

            // Цей цикл тепер потоковий (читає рядок за рядком)
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Пропустити заголовок
                try {
                    Barcode barcode = new Barcode();

                    // --- ВСЯ ВАША ЛОГІКА ЗАЛИШАЄТЬСЯ БЕЗ ЗМІН ---

                    // Код (Serial Number)
                    Cell codeCell = row.getCell(0);
                    // Використовуємо хелпер для безпечного читання рядка
                    String code = getStringCellValue(codeCell);

                    // Ваша логіка валідації (^\d{9}$)
                    if (code == null || code.isEmpty() || !code.matches("^\\d{9}$")) {
                        System.err.println("Помилка на рядку " + row.getRowNum() + ": Некоректний формат штрих-коду: " + (code != null ? code : "NULL"));
                        continue;
                    }

                    barcode.setCode(code);

                    // APN
                    Cell apnCell = row.getCell(1);
                    String apn = getStringCellValue(apnCell);
                    barcode.setApn(apn != null ? apn : "");

                    // Кількість
                    Cell qtyCell = row.getCell(2);
                    // Використовуємо новий безпечний хелпер для int
                    int quantity = getIntCellValue(qtyCell, 1); // 1 - значення за замовчуванням
                    barcode.setQuantity(quantity);

                    // Дата з 4-ї колонки
                    Cell dateCell = row.getCell(3);
                    // Використовуємо оптимізований хелпер для дати
                    LocalDateTime parsedDateTime = parseCellAsLocalDateTime(dateCell, DATE_FORMATTER_DDMMYYYY);
                    if (parsedDateTime != null) {
                        barcode.setParsedDate(parsedDateTime.toLocalDate().atStartOfDay());
                    }

                    // Встановлення локації на основі APN (Без змін)
                    if (isWiresApn(apn)) {
                        barcode.setLocation("wires");
                    } else {
                        barcode.setLocation("prestock");
                    }
                    barcode.setStatus("stock");

                    barcodes.add(barcode);
                } catch (Exception e) {
                    System.err.println("Помилка обробки рядка " + (row.getRowNum() + 1) + ": " + e.getMessage());
                }
            }
        }
        return barcodes;
    }

    // Допоміжний метод для перевірки APN (Без змін)
    private boolean isWiresApn(String apn) {
        if (apn == null || apn.isEmpty()) {
            return false;
        }
        return WIRES_APN_PREFIXES.stream().anyMatch(apn::startsWith);
    }

    //
    // --- МЕТОДИ ЕКСПОРТУ (exportToExcel, exportDiscardedToExcel, exportApnSummaryToExcel) ---
    // --- ЗАЛИШЕНО БЕЗ ЗМІН, XSSFWorkbook тут працює коректно для запису ---
    //

    public static void exportToExcel(List<Barcode> barcodes, OutputStream out) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Barcodes");
            String[] headers = {"Serial Number", "APN", "Кількість", "Локація", "Дата Додавання"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            int rowIndex = 1;
            for (Barcode b : barcodes) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(b.getCode());
                row.createCell(1).setCellValue(b.getApn());
                row.createCell(2).setCellValue(b.getQuantity());
                row.createCell(3).setCellValue(b.getLocation());
                row.createCell(4).setCellValue(
                        b.getCreationDate() != null ? b.getCreationDate().format(formatter) : "");
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
        }
    }

    /**
     * ОПТИМІЗОВАНО: Використовує StreamingReader.
     */
    public List<String> extractCodes(MultipartFile file) throws IOException {
        List<String> codes = new ArrayList<>();

        // ОНОВЛЕНО: Використання StreamingReader
        try (InputStream is = file.getInputStream();
             Workbook workbook = StreamingReader.builder()
                     .rowCacheSize(100)
                     .bufferSize(4096)
                     .open(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String code = getStringCellValue(row.getCell(0)); // Використовуємо наш хелпер

                if (code != null && !code.isEmpty()) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }

    /**
     * ОПТИМІЗОВАНО: Використовує StreamingReader.
     */
    public List<BarcodeTransferDto> parseTransfers(MultipartFile file) throws IOException {
        List<BarcodeTransferDto> transfers = new ArrayList<>();

        // ОНОВЛЕНО: Використання StreamingReader
        try (InputStream is = file.getInputStream();
             Workbook workbook = StreamingReader.builder()
                     .rowCacheSize(100)
                     .bufferSize(4096)
                     .open(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String code = getStringCellValue(row.getCell(0)); // Колонка A
                if (code == null || code.isEmpty()) continue;

                String locationName = getStringCellValue(row.getCell(3)); // Колонка D
                String locationNumber = getStringCellValue(row.getCell(4)); // Колонка E

                transfers.add(new BarcodeTransferDto(
                        code,
                        (locationName != null) ? locationName.trim() : "",
                        (locationNumber != null) ? locationNumber.trim() : ""
                ));
            }
        }
        return transfers;
    }

    // Метод експорту (Без змін)
    public static void exportDiscardedToExcel(List<Barcode> barcodes, OutputStream out) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Discarded Barcodes");
            String[] headers = {"Serial Number", "APN", "Кількість", "Локація", "Дата списання"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            int rowIndex = 1;
            for (Barcode b : barcodes) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(b.getCode());
                row.createCell(1).setCellValue(b.getApn());
                row.createCell(2).setCellValue(b.getQuantity());
                row.createCell(3).setCellValue(b.getLocation());
                row.createCell(4).setCellValue(
                        b.getLastUpdated() != null ? b.getLastUpdated().format(formatter) : "");
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
        }
    }

    // Метод експорту (Без змін)
    public void exportApnSummaryToExcel(List<ApnSummaryDto> summaryList, OutputStream out) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("APN Summary");
            String[] headers = {"APN", "Кількість", "Ящики"};
            Row headerRow = sheet.createRow(0);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (ApnSummaryDto summary : summaryList) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(summary.getApn());
                row.createCell(1).setCellValue(summary.getTotalQuantity());
                row.createCell(2).setCellValue(summary.getBoxCount());
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
        }
    }

    /**
     * ОПТИМІЗОВАНО: Використовує StreamingReader.
     */
    public List<Barcode> parseHistoryExcel(MultipartFile file) throws IOException {
        List<Barcode> barcodes = new ArrayList<>();
        // Використовуємо константу
        DateTimeFormatter formatter = DATE_FORMATTER_DDMMYYYY;

        // ОНОВЛЕНО: Використання StreamingReader
        try (InputStream is = file.getInputStream();
             Workbook workbook = StreamingReader.builder()
                     .rowCacheSize(100)
                     .bufferSize(4096)
                     .open(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                try {
                    String code = getStringCellValue(row.getCell(0)); // A
                    // Ваша логіка валідації коду (від 7 до 10 цифр)
                    if (code == null || code.isEmpty() || !code.matches("^\\d{7,10}$")) {
                        System.err.println("Помилка (або некоректний формат коду: '" + code + "') на рядку " + (row.getRowNum() + 1));
                        continue;
                    }

                    String apn = getStringCellValue(row.getCell(1)); // B
                    int quantity = getIntCellValue(row.getCell(2), 1); // C

                    LocalDateTime creationDateTime = parseCellAsLocalDateTime(row.getCell(3), formatter); // D
                    if (creationDateTime == null) {
                        System.err.println("Помилка (або відсутня дата додавання) на рядку " + (row.getRowNum() + 1) + " для коду " + code);
                        continue;
                    }

                    LocalDateTime discardDateTime = parseCellAsLocalDateTime(row.getCell(4), formatter); // E

                    // Ваша логіка (Без змін)
                    String status = (discardDateTime != null) ? "out" : null;

                    Barcode barcode = Barcode.builder()
                            .code(code)
                            .apn(apn != null ? apn : "")
                            .quantity(quantity)
                            .creationDate(creationDateTime)
                            .lastUpdated(discardDateTime != null ? discardDateTime : creationDateTime)
                            .discardDate(discardDateTime)
                            .status(status)
                            .build();

                    barcodes.add(barcode);
                } catch (Exception e) {
                    System.err.println("Помилка обробки рядка " + (row.getRowNum() + 1) + ": " + e.getMessage());
                }
            }
        }
        return barcodes;
    }


    // --- НОВІ/ОНОВЛЕНІ ДОПОМІЖНІ МЕТОДИ ДЛЯ СТРІМІНГУ ---

    /**
     * ОПТИМІЗОВАНО: Хелпер для дат, що працює зі StreamingReader.
     * StreamingReader краще працює з cell.getDateCellValue() (який повертає java.util.Date)
     * ніж з cell.getLocalDateTimeCellValue().
     */
    private LocalDateTime parseCellAsLocalDateTime(Cell dateCell, DateTimeFormatter formatter) {
        if (dateCell == null) {
            return null;
        }
        LocalDate parsedDate = null;
        try {
            // Пріоритет 1: Спробувати отримати як java.util.Date (найшвидший спосіб для дат в Excel)
            if (dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
                java.util.Date date = dateCell.getDateCellValue(); // Це працює швидко зі стрімінгом
                if (date != null) {
                    parsedDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                }
            }
            // Пріоритет 2: Спробувати розпарсити як рядок
            else if (dateCell.getCellType() == CellType.STRING) {
                String dateStr = dateCell.getStringCellValue();
                if (dateStr != null && !dateStr.trim().isEmpty()) {
                    parsedDate = LocalDate.parse(dateStr.trim(), formatter);
                }
            }
        } catch (DateTimeParseException e) {
            System.err.println("Некоректний формат дати в комірці: " + dateCell.getAddress() + ". Значення: " + getStringCellValue(dateCell));
        } catch (IllegalStateException ise) {
            System.err.println("Помилка типу комірки при читанні дати: " + dateCell.getAddress() + ". Тип: " + dateCell.getCellType());
        } catch (Exception e) {
            System.err.println("Загальна помилка читання дати " + dateCell.getAddress() + ": " + e.getMessage());
        }

        return (parsedDate != null) ? parsedDate.atStartOfDay() : null;
    }

    /**
     * ОПТИМІЗОВАНО: Хелпер для рядків, що працює зі StreamingReader.
     * Забезпечує коректне читання числових значень (як-от штрих-коди) без наукової нотації.
     */
    private String getStringCellValue(Cell cell) {
        if (cell == null) return null;

        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue() != null ? cell.getStringCellValue().trim() : null;
                case NUMERIC:
                    // Перевіряємо, чи це дата
                    if (DateUtil.isCellDateFormatted(cell)) {
                        try {
                            java.util.Date date = cell.getDateCellValue();
                            return date != null ? DATE_FORMATTER_DDMMYYYY.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()) : null;
                        } catch (Exception e) {
                            // Якщо не вдалося як дату, спробуємо як число
                            return String.valueOf((long) cell.getNumericCellValue()).trim();
                        }
                    } else {
                        // Обробляємо як ціле число (важливо для штрих-кодів)
                        return String.valueOf((long) cell.getNumericCellValue()).trim();
                    }
                case FORMULA:
                    // Streaming reader зазвичай повертає кешоване значення через getStringCellValue
                    try {
                        return cell.getStringCellValue().trim();
                    } catch (IllegalStateException e) {
                        try {
                            // Якщо формула повернула число
                            return String.valueOf((long) cell.getNumericCellValue()).trim();
                        } catch (IllegalStateException e2) {
                            return null;
                        }
                    }
                case BLANK:
                    return null;
                default:
                    return null;
            }
        } catch (Exception e) {
            // Перехоплення неочікуваних помилок при доступі до комірки
            System.err.println("Помилка доступу до комірки " + cell.getAddress() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * ОПТИМІЗОВАНО: Хелпер для чисел, що працює зі StreamingReader.
     */
    private int getIntCellValue(Cell cell, int defaultVal) {
        if (cell == null) return defaultVal;
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (int) cell.getNumericCellValue();
            }
            if (cell.getCellType() == CellType.STRING) {
                String s = cell.getStringCellValue();
                if (s != null && !s.trim().isEmpty()) {
                    return Integer.parseInt(s.trim());
                }
            }
        } catch (Exception e) {
            // NumberFormatException, IllegalStateException, etc.
            System.err.println("Не вдалося розпізнати число, використано " + defaultVal + " на " + cell.getAddress());
        }
        return defaultVal;
    }
}