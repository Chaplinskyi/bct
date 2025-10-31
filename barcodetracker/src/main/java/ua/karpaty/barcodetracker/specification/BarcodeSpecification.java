package ua.karpaty.barcodetracker.specification;

import ua.karpaty.barcodetracker.Entity.Barcode;
import org.springframework.data.jpa.domain.Specification;

public class BarcodeSpecification {

    /**
     * Специфікація для фільтрації за статусом (не 'out')
     */
    public static Specification<Barcode> isNotStatus(String status) {
        return (root, query, cb) -> cb.notEqual(root.get("status"), status);
    }

    /**
     * Специфікація для фільтрації за стелажем (rack)
     */
    public static Specification<Barcode> hasRack(String rack) {
        if (rack == null || rack.isBlank()) {
            return null; // Не додавати умову, якщо 'rack' порожній
        }
        return (root, query, cb) -> cb.equal(root.get("rack"), rack);
    }

    /**
     * Специфікація для фільтрації за прольотом (bay)
     */
    public static Specification<Barcode> hasBay(String bay) {
        if (bay == null || bay.isBlank()) {
            return null; // Не додавати умову, якщо 'bay' порожній
        }
        return (root, query, cb) -> cb.equal(root.get("bay"), bay);
    }

    /**
     * Специфікація для пошуку за APN
     */
    public static Specification<Barcode> hasApn(String apn) {
        if (apn == null || apn.isBlank()) {
            return null;
        }
        // Використовуємо 'like' для часткового пошуку, якщо потрібно,
        // або 'equal' для точного.
        return (root, query, cb) -> cb.equal(root.get("apn"), apn);
    }
}