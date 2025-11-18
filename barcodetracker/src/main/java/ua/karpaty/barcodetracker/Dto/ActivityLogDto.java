package ua.karpaty.barcodetracker.Dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ActivityLogDto {
    private final LocalDateTime timestamp;
    private final String activityType;
    private final String description;
    private final String iconClass;
    private final String linkUrl;
}