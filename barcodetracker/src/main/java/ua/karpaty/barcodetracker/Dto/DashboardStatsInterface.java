package ua.karpaty.barcodetracker.Dto;

public interface DashboardStatsInterface {
    Long getTotalInDb();
    Long getTotalAdded();
    Long getTotalDiscarded();
    String getMostPopularLocation();
}