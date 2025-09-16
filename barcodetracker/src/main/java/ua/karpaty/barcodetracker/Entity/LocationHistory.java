package ua.karpaty.barcodetracker.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@Builder
public class LocationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String oldLocation;
    private String newLocation;
    private LocalDateTime changeTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barcode_id", nullable = false)
    private Barcode barcode;

    public LocationHistory() {
    }

    public LocationHistory(Barcode barcode, String oldLocation, String newLocation, LocalDateTime changedAt) {
        this.barcode = barcode;
        this.oldLocation = oldLocation;
        this.newLocation = newLocation;
        this.changeTime = changedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOldLocation() {
        return oldLocation;
    }

    public void setOldLocation(String oldLocation) {
        this.oldLocation = oldLocation;
    }

    public String getNewLocation() {
        return newLocation;
    }

    public void setNewLocation(String newLocation) {
        this.newLocation = newLocation;
    }

    public LocalDateTime getChangeTime() {
        return changeTime;
    }

    public void setChangeTime(LocalDateTime changeTime) {
        this.changeTime = changeTime;
    }

    public Barcode getBarcode() {
        return barcode;
    }

    public void setBarcode(Barcode barcode) {
        this.barcode = barcode;
    }
}
