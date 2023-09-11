package org.example;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static Properties properties = new Properties();

    static {
        try (InputStream input = new FileInputStream(new File("Config/DockerInstaller.properties"))) {
            properties.load(input);
        } catch (Exception e) {
            throw new RuntimeException("Error loading properties", e);
        }

    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }


}
