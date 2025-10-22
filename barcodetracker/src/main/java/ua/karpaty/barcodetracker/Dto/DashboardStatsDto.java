package ua.karpaty.barcodetracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardStatsDto {
    private final long totalInDb;       // Всього в базі (активні)
    private final long totalAdded;      // Всього додано (за весь час)
    private final long totalDiscarded;  // Всього списано
    private final String mostPopularLocation; // Найпопулярніша локація
    private final long lastImportCount; // Кількість в останньому імпорті
}