package ua.karpaty.barcodetracker.Dto;

import lombok.Data;
import ua.karpaty.barcodetracker.Entity.Barcode;
import ua.karpaty.barcodetracker.Entity.Frozen.FrozenBarcode;

import java.time.LocalDateTime;

// Уніфікований DTO для відображення на сторінці 'discarded'
@Data
public class DiscardedBarcodeDto {
    private String code;
    private String apn;
    private Integer quantity;
    private String location; // 'location' з Barcode (в архіві цього поля немає)
    private LocalDateTime discardDate;
    private Long barcodeId; // ID з "гарячої" бази для посилання
    private boolean isFrozen; // Прапорець, що це архівний запис

    // Конструктор для "гарячого" Barcode
    public DiscardedBarcodeDto(Barcode barcode) {
        this.code = barcode.getCode();
        this.apn = barcode.getApn();
        this.quantity = barcode.getQuantity();
        this.location = barcode.getLocation();
        this.discardDate = barcode.getLastUpdated(); // Використовуємо lastUpdated
        this.barcodeId = barcode.getId();
        this.isFrozen = false;
    }

    // Конструктор для "замороженого" FrozenBarcode
    public DiscardedBarcodeDto(FrozenBarcode frozenBarcode) {
        this.code = frozenBarcode.getCode();
        this.apn = frozenBarcode.getApn();
        this.quantity = frozenBarcode.getQuantity();
        this.location = "N/A (Архів)"; // В архіві немає локації
        this.discardDate = frozenBarcode.getDateDiscarded().atStartOfDay(); // Конвертуємо LocalDate
        this.barcodeId = null; // Немає ID в "гарячій" базі
        this.isFrozen = true;
    }
}