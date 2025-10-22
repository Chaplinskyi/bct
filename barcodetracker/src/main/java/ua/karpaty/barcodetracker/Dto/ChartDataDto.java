package ua.karpaty.barcodetracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class ChartDataDto {
    private final List<String> labels; // Місяці (напр. "Жов 2025")
    private final List<Long> data;     // Кількість
}