package ua.karpaty.barcodetracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// Використовується для графіка "Заповненість складу"
@Data
@AllArgsConstructor
public class LocationCountDto {
    private String location;
    private Long count;
}