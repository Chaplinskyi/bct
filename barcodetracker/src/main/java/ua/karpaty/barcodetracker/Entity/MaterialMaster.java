package ua.karpaty.barcodetracker.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "material_master")
public class MaterialMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String apn;

    @Column(length = 100)
    private String location;

    @Column(length = 100)
    private String supermarket;

    public MaterialMaster(String apn, String location, String supermarket) {
        this.apn = apn;
        this.location = location;
        this.supermarket = supermarket;
    }
}