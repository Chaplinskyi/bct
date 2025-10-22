package ua.karpaty.barcodetracker.Service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ua.karpaty.barcodetracker.Dto.BarcodeTransferDto;
import ua.karpaty.barcodetracker.Entity.Barcode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ExcelService {

    // Створимо список префіксів для "wires"
    private static final List<String> WIRES_APN_PREFIXES = Arrays.asList("M3130", "M3232", "M3362", "M68");

    public List<Barcode> parseExcel(MultipartFile file) throws IOException {
        List<Barcode> barcodes = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Пропустити заголовок
                try {
                    Barcode barcode = new Barcode();
                    // Код (Serial Number)
                    Cell codeCell = row.getCell(0);
                    if (codeCell == null || codeCell.getCellType() == CellType.BLANK) continue;

                    String code;
                    if (codeCell.getCellType() == CellType.STRING) {
                        code = codeCell.getStringCellValue().trim();
                    } else if (codeCell.getCellType() == CellType.NUMERIC) {
                        code = String.valueOf((long) codeCell.getNumericCellValue());
                    } else {
                        continue;
                    }

                    if (code.isEmpty() || !code.matches("^\\d{9}$")) {
                        System.err.println("Помилка на рядку " + row.getRowNum() + ": Некоректний формат штрих-коду: " + code);
                        continue; // Пропускаємо цей рядок
                    }

                    if (code.isEmpty()) continue;
                    barcode.setCode(code);
                    // APN
                    Cell apnCell = row.getCell(1);
                    String apn = "";
                    if (apnCell != null) {
                        if (apnCell.getCellType() == CellType.STRING) {
                            apn = apnCell.getStringCellValue().trim();
                        } else if (apnCell.getCellType() == CellType.NUMERIC) {
                            apn = String.valueOf((long) apnCell.getNumericCellValue());
                        }
                    }
                    barcode.setApn(apn);
                    // Кількість
                    Cell qtyCell = row.getCell(2);
                    if (qtyCell != null && qtyCell.getCellType() == CellType.NUMERIC) {
                        barcode.setQuantity((int) qtyCell.getNumericCellValue());
                    } else {
                        barcode.setQuantity(1); // дефолт
                    }
                    // Дата з 4-ї колонки
                    Cell dateCell = row.getCell(3);
                    if (dateCell != null) {
                        LocalDate parsedDate = null;
                        if (dateCell.getCellType() == CellType.STRING) {
                            String dateStr = dateCell.getStringCellValue().trim();
                            try {
                                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                                parsedDate = LocalDate.parse(dateStr, formatter);
                            } catch (DateTimeParseException e) {
                                // Некоректний формат
                                parsedDate = null;
                            }
                        } else if (dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
                            parsedDate = dateCell.getLocalDateTimeCellValue().toLocalDate();
                        }
                        if (parsedDate != null) {
                            barcode.setParsedDate(parsedDate.atStartOfDay());
                        }
                    }
                    // Встановлення локації на основі APN
                    if (isWiresApn(apn)) {
                        barcode.setLocation("wires");
                    } else {
                        barcode.setLocation("prestock");
                    }
                    barcode.setStatus("stock");

                    barcodes.add(barcode);
                } catch (Exception e) {
                    System.err.println("Помилка на рядку " + row.getRowNum() + ": " + e.getMessage());
                    continue;
                }
            }
        }
        return barcodes;
    }

    // Допоміжний метод для перевірки APN
    private boolean isWiresApn(String apn) {
        if (apn == null || apn.isEmpty()) {
            return false;
        }
        return WIRES_APN_PREFIXES.stream().anyMatch(apn::startsWith);
    }

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

    public List<String> extractCodes(MultipartFile file) throws IOException {
        List<String> codes = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                Cell codeCell = row.getCell(0);
                if (codeCell == null) continue;

                String code;
                if (codeCell.getCellType() == CellType.STRING) {
                    code = codeCell.getStringCellValue().trim();
                } else if (codeCell.getCellType() == CellType.NUMERIC) {
                    code = String.valueOf((long) codeCell.getNumericCellValue());
                } else {
                    continue;
                }

                if (!code.isEmpty()) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }

    /**
     * Новий метод для парсингу файлу з переміщеннями.
     * Очікує штрих-код у колонці 1, локацію у колонці 4, номер локації у колонці 5.
     */
    public List<BarcodeTransferDto> parseTransfers(MultipartFile file) throws IOException {
        List<BarcodeTransferDto> transfers = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Пропустити заголовок

                Cell codeCell = row.getCell(0); // Колонка A
                Cell locationCell = row.getCell(3); // Колонка D
                Cell locationNumberCell = row.getCell(4); // Колонка E

                if (codeCell == null) continue;
                String code = getStringCellValue(codeCell);
                if (code == null || code.isEmpty()) continue;

                // Отримуємо назву стелажу (напр. "excess sk" або "SK")
                String locationName = getStringCellValue(locationCell);

                // Отримуємо номер прольоту (напр. "4")
                String locationNumber = getStringCellValue(locationNumberCell);

                // Додаємо у список, навіть якщо порожні, валідація буде в контролері
                transfers.add(new BarcodeTransferDto(
                        code,
                        (locationName != null) ? locationName.trim() : "",
                        (locationNumber != null) ? locationNumber.trim() : ""
                ));
            }
        }
        return transfers;
    }

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

    // Допоміжний метод для отримання строкового значення з комірки
    private String getStringCellValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue()).trim();
            default -> null;
        };
    }
}

