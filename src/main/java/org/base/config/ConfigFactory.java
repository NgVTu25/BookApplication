package org.base.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigFactory {

    private static final ConfigFactory INSTANCE = new ConfigFactory();
    private final Properties properties = new Properties();

    private ConfigFactory() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (in == null) {
                throw new RuntimeException("Không tìm thấy file trong src/main/resources");
            }
            properties.load(in);

        } catch (IOException e) {
            throw new RuntimeException("Lỗi khi load application.properties", e);
        }
    }

    public static ConfigFactory getInstance() {
        return INSTANCE;
    }

    public String getConfig(String key) {
        String value = properties.getProperty(key);
        return value == null ? null : value.trim();
    }
}