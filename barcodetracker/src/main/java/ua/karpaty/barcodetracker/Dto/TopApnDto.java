package ua.karpaty.barcodetracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// Використовується для таблиці "Топ-5 Матеріалів"
@Data
@AllArgsConstructor
public class TopApnDto {
    private String apn;
    private Long totalQuantity;
}