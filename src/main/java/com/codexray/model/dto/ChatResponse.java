package com.codexray.model.dto;

public record ChatResponse(
        String sessionId,
        String answer,
        String timestamp
) {}
