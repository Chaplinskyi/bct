package ua.karpaty.barcodetracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@AllArgsConstructor
public class ChartDataDto {
    private final List<String> labels; // Місяці (напр. "Жов 2025")
    private final List<Long> data;     // Кількість

    public List<String> getLabels() {
        return labels;
    }

    public List<Long> getData() {
        return data;
    }
}