package ua.karpaty.barcodetracker.Dto;

// Цей інтерфейс використовується Spring Data JPA для прийому результатів nativeQuery
public interface MonthlyStatDto {
    Integer getYear();
    Integer getMonth();
    Long getCount();
}