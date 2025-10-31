package ua.karpaty.barcodetracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor // Lombok згенерує конструктор з усіма аргументами
public class ApnSummaryDto {
    private String apn;          // Колонка 1: APN
    private Long totalQuantity;  // Колонка 2: Загальна кількість (сума)
    private Long boxCount;       // Колонка 3: Кількість ящиків (кількість записів)
}