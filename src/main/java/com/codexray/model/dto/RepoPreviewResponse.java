package com.codexray.model.dto;

import java.util.List;
import java.util.Map;

public record RepoPreviewResponse(
        String repoUrl,
        int totalFiles,
        int totalSourceFiles,
        String primaryLanguage,
        Map<String, Integer> languageStats,
        List<String> topLevelFiles,
        List<String> keyConfigFiles
) {}
