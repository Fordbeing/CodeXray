package com.codexray.model.dto;

public record CodeReviewRequest(
        String diff,
        String prUrl,
        String taskId,
        String filePath
) {}
