package com.codexray.model.dto;

import java.time.LocalDateTime;

public record AnalysisResultResponse(
        String taskId,
        String repoUrl,
        String status,
        String report,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
