package ua.karpaty.barcodetracker.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.karpaty.barcodetracker.Entity.MaterialMaster;
import ua.karpaty.barcodetracker.Repository.MaterialMasterRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MaterialMasterService {

    private final MaterialMasterRepository materialMasterRepository;

    @Autowired
    public MaterialMasterService(MaterialMasterRepository materialMasterRepository) {
        this.materialMasterRepository = materialMasterRepository;
    }

    @Transactional
    public String importDataFromCsv(InputStream inputStream) throws IOException {

        Map<String, MaterialMaster> materialsFromCsv = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                String[] columns = line.split(",", -1);

                if (columns.length > 2) {
                    String apn = columns[0].trim();
                    String location = columns[1].trim();
                    String supermarket = columns[2].trim();

                    if (!apn.isEmpty()) {
                        materialsFromCsv.put(apn, new MaterialMaster(apn, location, supermarket));
                    }
                }
            }
        }

        if (materialsFromCsv.isEmpty()) {
            return "Файл порожній або має неправильний формат. Нічого не імпортовано.";
        }

        List<MaterialMaster> materialsToSave = new ArrayList<>(materialsFromCsv.values());

        List<String> apns = materialsToSave.stream().map(MaterialMaster::getApn).collect(Collectors.toList());
        Map<String, MaterialMaster> existingMaterialsMap = materialMasterRepository.findByApnIn(apns).stream()
                .collect(Collectors.toMap(MaterialMaster::getApn, Function.identity()));

        List<MaterialMaster> finalSaveList = new ArrayList<>();
        int updated = 0;
        int created = 0;

        for (MaterialMaster newMaterial : materialsToSave) {
            MaterialMaster existing = existingMaterialsMap.get(newMaterial.getApn());
            if (existing != null) {
                existing.setLocation(newMaterial.getLocation());
                existing.setSupermarket(newMaterial.getSupermarket());
                finalSaveList.add(existing);
                updated++;
            } else {
                finalSaveList.add(newMaterial);
                created++;
            }
        }

        materialMasterRepository.saveAll(finalSaveList);

        return "Імпорт завершено. Оброблено унікальних APN з файлу: " + materialsFromCsv.size() +
                ". Створено нових записів: " + created + ". Оновлено існуючих: " + updated + ".";
    }
}