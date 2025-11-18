package ua.karpaty.barcodetracker.Entity.Frozen;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "frozen_barcodes")
public class FrozenBarcode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Встановлюємо індекс на 'code' для МИТТЄВОГО пошуку
    @Column(nullable = false, unique = true, length = 50, updatable = false)
    private String code;

    @Column(length = 50, updatable = false)
    private String apn;

    @Column(updatable = false)
    private Integer quantity;

    @Column(updatable = false)
    private LocalDate dateAdded;

    @Column(updatable = false)
    private LocalDate dateDiscarded;

    public FrozenBarcode(String code, String apn, Integer quantity, LocalDate dateAdded, LocalDate dateDiscarded) {
        this.code = code;
        this.apn = apn;
        this.quantity = quantity;
        this.dateAdded = dateAdded;
        this.dateDiscarded = dateDiscarded;
    }
}