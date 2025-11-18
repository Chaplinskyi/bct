package ua.karpaty.barcodetracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// Використовується для відображення невідповідностей на Дашборді
@Data
@AllArgsConstructor
public class MismatchDto {
    private Long barcodeId;
    private String barcode;
    private String apn;
    private String actualLocation;   // Де лежить зараз
    private String expectedLocation; // Де має лежати (з Master DB)
}