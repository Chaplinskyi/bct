package ua.karpaty.barcodetracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardStatsDto {
    private final long totalInDb;
    private final long totalAdded;
    private final long totalDiscarded;
    private final String mostPopularLocation;
    private final long lastImportCount;
}