package com.codexray.controller;

import com.codexray.common.Result;
import com.codexray.llm.LlmClient;
import com.codexray.service.SettingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingController {

    private final SettingService settingService;
    private final LlmClient llmClient;

    public SettingController(SettingService settingService, LlmClient llmClient) {
        this.settingService = settingService;
        this.llmClient = llmClient;
    }

    @GetMapping
    public Result<Map<String, String>> getSettings() {
        return Result.ok(settingService.getAll());
    }

    @PutMapping
    public Result<Void> updateSettings(@RequestBody Map<String, String> settings) {
        settingService.updateAll(settings);
        return Result.ok(null);
    }

    @PostMapping("/test-ai")
    public Result<String> testAiConnection() {
        try {
            String response = llmClient.testConnection();
            return Result.ok(response);
        } catch (Exception e) {
            return Result.error("AI 连接失败: " + e.getMessage());
        }
    }

    // ===== AI Presets =====

    @GetMapping("/presets/ai")
    public Result<List<Map<String, String>>> getAiPresets() {
        return Result.ok(settingService.getAiPresets());
    }

    @PostMapping("/presets/ai")
    public Result<Void> saveAiPreset(@RequestBody Map<String, String> preset) {
        settingService.saveAiPreset(preset);
        return Result.ok(null);
    }

    @DeleteMapping("/presets/ai/{name}")
    public Result<Void> deleteAiPreset(@PathVariable String name) {
        settingService.deleteAiPreset(name);
        return Result.ok(null);
    }

    // ===== Mail Presets =====

    @GetMapping("/presets/mail")
    public Result<List<Map<String, String>>> getMailPresets() {
        return Result.ok(settingService.getMailPresets());
    }

    @PostMapping("/presets/mail")
    public Result<Void> saveMailPreset(@RequestBody Map<String, String> preset) {
        settingService.saveMailPreset(preset);
        return Result.ok(null);
    }

    @DeleteMapping("/presets/mail/{name}")
    public Result<Void> deleteMailPreset(@PathVariable String name) {
        settingService.deleteMailPreset(name);
        return Result.ok(null);
    }
}
