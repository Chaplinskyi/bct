package ua.karpaty.barcodetracker.Dto;

import lombok.Getter;
import ua.karpaty.barcodetracker.Entity.Barcode; // Потрібно імпортувати Barcode
import java.time.LocalDateTime;

@Getter
public class RecentActivityDto {
    private final Long barcodeId;
    private final String barcodeCode;
    private final String description;
    private final LocalDateTime timestamp;
    private final String type; // "location" або "status"

    // Конструктор для LocationHistory
    public RecentActivityDto(Long barcodeId, String barcodeCode, String oldLocation, String newLocation, LocalDateTime timestamp) {
        this.barcodeId = barcodeId;
        this.barcodeCode = barcodeCode;
        this.description = formatLocationChange(oldLocation, newLocation);
        this.timestamp = timestamp;
        this.type = "location";
    }

    // Конструктор для StatusHistory
    public RecentActivityDto(Long barcodeId, String barcodeCode, String oldStatus, String newStatus, LocalDateTime timestamp, boolean isStatus) {
        this.barcodeId = barcodeId;
        this.barcodeCode = barcodeCode;
        this.description = formatStatusChange(oldStatus, newStatus);
        this.timestamp = timestamp;
        this.type = "status";
    }

    private String formatLocationChange(String oldLoc, String newLoc) {
        if (oldLoc == null || oldLoc.isEmpty()) {
            return "Призначено локацію: " + newLoc;
        }
        return "Переміщено з '" + oldLoc + "' на '" + newLoc + "'";
    }

    private String formatStatusChange(String oldStatus, String newStatus) {
        if (oldStatus == null || oldStatus.isEmpty()) {
            return "Встановлено статус: " + newStatus;
        }
        return "Змінено статус з '" + oldStatus + "' на '" + newStatus + "'";
    }

    // Додаємо метод для отримання Barcode (опціонально, якщо потрібно в DTO)
    // Якщо не використовуєте Barcode напряму в DTO, цей метод можна видалити
    // public Barcode getBarcode() { return barcode; }
}