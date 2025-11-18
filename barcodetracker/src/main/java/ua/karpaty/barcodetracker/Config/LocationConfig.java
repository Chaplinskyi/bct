package ua.karpaty.barcodetracker.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;
import java.util.LinkedHashMap;

@Configuration
public class LocationConfig {

    @Bean
    public Map<String, Integer> locationMap() {
        Map<String, Integer> locations = new LinkedHashMap<>();

        locations.put("prestock", 0);

        locations.put("SK", 12);
        locations.put("ST", 14);
        locations.put("D", 14);
        locations.put("SV", 11);
        locations.put("BULKY", 3);
        locations.put("Y", 6);
        locations.put("SR", 15);
        locations.put("Ter.1", 5);
        locations.put("Ter.2", 5);
        locations.put("Ter.3", 3);
        locations.put("Ter.6", 3);
        locations.put("Ter.7", 1);
        locations.put("H", 2);
        locations.put("I", 2);
        locations.put("J", 2);
        locations.put("P", 2);
        locations.put("Q", 2);
        locations.put("T", 2);
        locations.put("U", 2);
        locations.put("G", 3);
        locations.put("TerminalExcess", 4);
        locations.put("Tape", 12);

        return locations;
    }
}