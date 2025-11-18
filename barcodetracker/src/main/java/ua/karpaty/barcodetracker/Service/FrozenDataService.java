package ua.karpaty.barcodetracker.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus; // <--- ДОДАНО
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback; // <--- ДОДАНО
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import ua.karpaty.barcodetracker.Entity.Frozen.FrozenBarcode;
import ua.karpaty.barcodetracker.Repository.Frozen.FrozenBarcodeRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FrozenDataService {

    private final FrozenBarcodeRepository frozenBarcodeRepository;
    private final TransactionTemplate transactionTemplate;
    private static final int BATCH_SIZE = 2000;

    @Autowired
    public FrozenDataService(FrozenBarcodeRepository frozenBarcodeRepository,
                             @Qualifier("frozenTransactionManager") PlatformTransactionManager transactionManager) {
        this.frozenBarcodeRepository = frozenBarcodeRepository;

        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Головний метод імпорту.
     */
    public String importFromCsv(MultipartFile file) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        List<FrozenBarcode> batch = new ArrayList<>(BATCH_SIZE);
        long totalSaved = 0;
        long skippedRows = 0;
        long duplicateRows = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                String[] columns = line.split(",", -1);
                if (columns.length < 5) {
                    skippedRows++;
                    continue;
                }

                try {
                    String code = columns[0].trim();
                    String apn = columns[1].trim();
                    String cleanQuantity = columns[2].replaceAll("\\s+", "");

                    Integer quantity = cleanQuantity.isEmpty() ? 0 : Integer.parseInt(cleanQuantity);
                    LocalDate dateAdded = LocalDate.parse(columns[3].trim(), formatter);
                    LocalDate dateDiscarded = LocalDate.parse(columns[4].trim(), formatter);

                    if (code.isEmpty()) {
                        skippedRows++;
                        continue;
                    }

                    batch.add(new FrozenBarcode(code, apn, quantity, dateAdded, dateDiscarded));

                    if (batch.size() >= BATCH_SIZE) {
                        long saved = processBatchInTransaction(batch);
                        totalSaved += saved;
                        duplicateRows += (batch.size() - saved);
                        batch.clear();
                    }
                } catch (DateTimeParseException | NumberFormatException e) {
                    skippedRows++;
                }
            }

            if (!batch.isEmpty()) {
                long saved = processBatchInTransaction(batch);
                totalSaved += saved;
                duplicateRows += (batch.size() - saved);
                batch.clear();
            }

        } catch (Exception e) {
            return "Помилка під час читання файлу " + file.getOriginalFilename() + ": " + e.getMessage();
        }

        return "Файл " + file.getOriginalFilename() + " оброблено. Збережено: " + totalSaved +
                ", дублікатів: " + duplicateRows + ", помилок формату: " + skippedRows + ".";
    }

    /**
     * Виконує збереження пакета в окремій транзакції.
     * Використовуємо явний тип TransactionCallback<Long> для уникнення помилок компіляції.
     */
    private long processBatchInTransaction(List<FrozenBarcode> batch) {
        Long result = transactionTemplate.execute(new TransactionCallback<Long>() {
            @Override
            public Long doInTransaction(TransactionStatus status) {
                try {
                    List<String> codesInBatch = batch.stream()
                            .map(FrozenBarcode::getCode)
                            .collect(Collectors.toList());

                    // 1. Знаходимо існуючі
                    Set<String> existingCodes = frozenBarcodeRepository.findExistingCodes(codesInBatch);

                    // 2. Фільтруємо
                    List<FrozenBarcode> newBarcodes = new ArrayList<>();
                    Set<String> uniqueInBatch = new HashSet<>();

                    for (FrozenBarcode fb : batch) {
                        if (!existingCodes.contains(fb.getCode()) && uniqueInBatch.add(fb.getCode())) {
                            newBarcodes.add(fb);
                        }
                    }

                    // 3. Зберігаємо
                    if (!newBarcodes.isEmpty()) {
                        frozenBarcodeRepository.saveAllAndFlush(newBarcodes);
                    }

                    return (long) newBarcodes.size();

                } catch (Exception e) {
                    status.setRollbackOnly();
                    System.err.println("Помилка під час збереження пакету: " + e.getMessage());
                    return 0L;
                }
            }
        });

        return result != null ? result : 0L;
    }

    @Transactional(readOnly = true, transactionManager = "frozenTransactionManager")
    public Optional<FrozenBarcode> findByCode(String code) {
        return frozenBarcodeRepository.findByCode(code);
    }

    @Transactional(readOnly = true, transactionManager = "frozenTransactionManager")
    public long countTotalFrozenBarcodes() {
        return frozenBarcodeRepository.count();
    }
}