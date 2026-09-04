package com.peoplefirst;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

@SpringBootApplication
public class PeopleFirstApplication {

    public static void main(String[] args) {
        loadDotEnvIfPresent();
        SpringApplication.run(PeopleFirstApplication.class, args);
    }

    /**
     * Automatically loads .env file properties into system properties if present in project root or parent dir.
     */
    private static void loadDotEnvIfPresent() {
        File[] candidates = new File[] {
                new File(".env"),
                new File("../.env"),
                new File("backend/.env")
        };

        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int eqIdx = line.indexOf('=');
                        if (eqIdx > 0) {
                            String key = line.substring(0, eqIdx).trim();
                            String val = line.substring(eqIdx + 1).trim();
                            if ((val.startsWith("\"") && val.endsWith("\"")) ||
                                (val.startsWith("'") && val.endsWith("'"))) {
                                val = val.substring(1, val.length() - 1);
                            }
                            if (System.getenv(key) == null && System.getProperty(key) == null && !val.isEmpty()) {
                                System.setProperty(key, val);
                            }
                        }
                    }
                    System.out.println("🌱 Loaded environment configuration from: " + f.getAbsolutePath());
                    break;
                } catch (Exception e) {
                    System.err.println("Notice: Could not parse .env file: " + e.getMessage());
                }
            }
        }
    }
}

