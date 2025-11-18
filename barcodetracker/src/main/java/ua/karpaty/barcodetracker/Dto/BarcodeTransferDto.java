package ua.karpaty.barcodetracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BarcodeTransferDto {
    private final String code;
    private final String locationName;
    private final String locationNumber;
}