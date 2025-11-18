package ua.karpaty.barcodetracker.Exception;

public class BarcodeNotFoundException extends RuntimeException {
    public BarcodeNotFoundException(String string) {
        super("Barcode not found");
    }
}