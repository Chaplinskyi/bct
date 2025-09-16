package ua.karpaty.barcodetracker.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Barcode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code; // Штрихкод
    private String apn;
    private int quantity;

    private String location; // SK, ST, SV, D, SR, BULKY, NARIZKA (розшифрувати в UI)
    private String status;   // stock, out

    private LocalDateTime lastUpdated;

    @Transient
    private LocalDateTime parsedDate;

    public LocalDateTime getParsedDate() {
        return parsedDate;
    }

    public void setParsedDate(LocalDateTime parsedDate) {
        this.parsedDate = parsedDate;
    }

    @OneToMany(mappedBy = "barcode", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LocationHistory> locationHistories;

    @OneToMany(mappedBy = "barcode", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StatusHistory> statusHistories;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getApn() {
        return apn;
    }

    public void setApn(String apn) {
        this.apn = apn;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public List<LocationHistory> getLocationHistories() {
        return locationHistories;
    }

    public void setLocationHistories(List<LocationHistory> locationHistories) {
        this.locationHistories = locationHistories;
    }

    public List<StatusHistory> getStatusHistories() {
        return statusHistories;
    }

    public void setStatusHistories(List<StatusHistory> statusHistories) {
        this.statusHistories = statusHistories;
    }

    public List<StatusHistory> getStatusHistory() {
        return statusHistories;
    }
}
