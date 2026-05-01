package com.codexray.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 在 Spring 环境初始化前加载 .env 文件中的环境变量。
 * 已有的系统环境变量不会被覆盖。
 */
public class DotEnvConfig implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DotEnvConfig.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Path envFile = Path.of(".env");
        if (!Files.isRegularFile(envFile)) {
            return;
        }
        try {
            int count = 0;
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int idx = trimmed.indexOf('=');
                if (idx <= 0) continue;
                String key = trimmed.substring(0, idx).trim();
                String value = trimmed.substring(idx + 1).trim();
                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, value);
                    count++;
                }
            }
            if (count > 0) {
                log.info("Loaded {} variables from .env", count);
            }
        } catch (IOException e) {
            log.warn("Failed to load .env: {}", e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
