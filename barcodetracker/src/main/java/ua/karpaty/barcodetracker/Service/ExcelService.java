package ua.karpaty.barcodetracker.Service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ua.karpaty.barcodetracker.Entity.Barcode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelService {

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

                    if (code.isEmpty()) continue;
                    barcode.setCode(code);
                    // APN
                    Cell apnCell = row.getCell(1);
                    if (apnCell != null) {
                        String apn = "";
                        if (apnCell.getCellType() == CellType.STRING) {
                            apn = apnCell.getStringCellValue().trim();
                        } else if (apnCell.getCellType() == CellType.NUMERIC) {
                            apn = String.valueOf((long) apnCell.getNumericCellValue()); // якщо це довге число
                        }
                        barcode.setApn(apn);
                    } else {
                        barcode.setApn("");
                    }
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
                    // Статичні значення
                    barcode.setLocation("prestock");
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

    public static void exportToExcel(List<Barcode> barcodes, OutputStream outputStream) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Discarded Barcodes");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Bar Code");
        header.createCell(1).setCellValue("APN");
        header.createCell(2).setCellValue("Кількість");
        header.createCell(3).setCellValue("Локація");
        header.createCell(4).setCellValue("Статус");
        header.createCell(5).setCellValue("Дата");

        int rowNum = 1;
        for (Barcode barcode : barcodes) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(barcode.getCode());
            row.createCell(1).setCellValue(barcode.getApn());
            row.createCell(2).setCellValue(barcode.getQuantity());
            row.createCell(3).setCellValue(barcode.getLocation());
            row.createCell(4).setCellValue(barcode.getStatus());
            row.createCell(5).setCellValue(barcode.getLastUpdated().toString());
        }

        workbook.write(outputStream);
        workbook.close();
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
}

