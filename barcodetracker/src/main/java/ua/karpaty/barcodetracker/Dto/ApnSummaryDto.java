package ua.karpaty.barcodetracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApnSummaryDto {
    private String apn;
    private long totalQuantity;
    private long boxCount;
}