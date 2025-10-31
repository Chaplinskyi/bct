package ua.karpaty.barcodetracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class BarcodeTransferDto {
    private final String code;
    private final String locationName; // Колонка D
    private final String locationNumber; // Колонка E

    public String getCode() {
        return code;
    }

    public String getLocationName() {
        return locationName;
    }

    public String getLocationNumber() {
        return locationNumber;
    }
}