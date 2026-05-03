package com.codexray.service;

import com.codexray.mapper.SysSettingMapper;
import com.codexray.model.entity.SysSetting;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SettingService {

    private static final Logger log = LoggerFactory.getLogger(SettingService.class);

    private final SysSettingMapper settingMapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SettingService(SysSettingMapper settingMapper) {
        this.settingMapper = settingMapper;
        loadAll();
    }

    public Map<String, String> getAll() {
        return new HashMap<>(cache);
    }

    public String get(String key) {
        return cache.get(key);
    }

    public String get(String key, String defaultValue) {
        return cache.getOrDefault(key, defaultValue);
    }

    public void updateAll(Map<String, String> settings) {
        settings.forEach((key, value) -> {
            SysSetting existing = settingMapper.selectById(key);
            if (existing != null) {
                existing.setValue(value);
                existing.setUpdatedAt(LocalDateTime.now());
                settingMapper.updateById(existing);
            } else {
                SysSetting entity = new SysSetting();
                entity.setKey(key);
                entity.setValue(value);
                entity.setUpdatedAt(LocalDateTime.now());
                settingMapper.insert(entity);
            }
            cache.put(key, value);
        });
    }

    // ===== Preset management (stored as JSON in sys_setting) =====

    private List<Map<String, String>> getPresets(String settingKey) {
        String json = cache.get(settingKey);
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse presets for key {}: {}", settingKey, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void savePresets(String settingKey, List<Map<String, String>> presets) {
        try {
            String json = objectMapper.writeValueAsString(presets);
            updateAll(Map.of(settingKey, json));
        } catch (Exception e) {
            throw new RuntimeException("Failed to save presets: " + e.getMessage(), e);
        }
    }

    public List<Map<String, String>> getAiPresets() {
        return getPresets("ai_presets");
    }

    public void saveAiPreset(Map<String, String> preset) {
        List<Map<String, String>> presets = getAiPresets();
        String name = preset.get("name");
        // Remove existing with same name
        presets.removeIf(p -> name.equals(p.get("name")));
        // Keep only last 5
        while (presets.size() >= 5) {
            presets.remove(presets.size() - 1);
        }
        presets.add(0, preset);
        savePresets("ai_presets", presets);
    }

    public void deleteAiPreset(String name) {
        List<Map<String, String>> presets = getAiPresets();
        presets.removeIf(p -> name.equals(p.get("name")));
        savePresets("ai_presets", presets);
    }

    public List<Map<String, String>> getMailPresets() {
        return getPresets("mail_presets");
    }

    public void saveMailPreset(Map<String, String> preset) {
        List<Map<String, String>> presets = getMailPresets();
        String name = preset.get("name");
        presets.removeIf(p -> name.equals(p.get("name")));
        while (presets.size() >= 5) {
            presets.remove(presets.size() - 1);
        }
        presets.add(0, preset);
        savePresets("mail_presets", presets);
    }

    public void deleteMailPreset(String name) {
        List<Map<String, String>> presets = getMailPresets();
        presets.removeIf(p -> name.equals(p.get("name")));
        savePresets("mail_presets", presets);
    }

    private void loadAll() {
        List<SysSetting> all = settingMapper.selectList(null);
        for (SysSetting s : all) {
            cache.put(s.getKey(), s.getValue());
        }
    }
}
