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

    private String rack;  // Наприклад, "A", "B"
    private String bay;   // Наприклад, "1", "2"

    private LocalDateTime creationDate;
    private LocalDateTime lastUpdated;

    private LocalDateTime discardDate;

    @Transient
    private LocalDateTime parsedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id")
    private ImportBatch importBatch;

    @OneToMany(mappedBy = "barcode", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LocationHistory> locationHistories;

    @OneToMany(mappedBy = "barcode", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StatusHistory> statusHistories;

    public List<StatusHistory> getStatusHistory() {
        return statusHistories;
    }
}
