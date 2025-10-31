package ua.karpaty.barcodetracker;

import org.apache.poi.util.IOUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BarcodetrackerApplication {

	public static void main(String[] args) {
		IOUtils.setByteArrayMaxOverride(200 * 1024 * 1024);
		SpringApplication.run(BarcodetrackerApplication.class, args);
	}

}
