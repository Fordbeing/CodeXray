package com.codexray.model.dto;

public record ChatResponse(
        String answer,
        String repoUrl,
        String sessionId
) {}
