package ua.karpaty.barcodetracker.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ImportBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private LocalDateTime importDate;

    @Column(name = "barcode_count")
    private Integer barcodeCount; // Будемо зберігати тут розмір 'barcodes.size()'

    @OneToMany(mappedBy = "importBatch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Barcode> barcodes;

    public ImportBatch(String name, LocalDateTime importDate) {
        this.name = name;
        this.importDate = importDate;
    }
}