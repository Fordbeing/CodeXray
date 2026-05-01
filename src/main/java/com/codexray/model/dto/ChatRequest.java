package com.codexray.model.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String repoUrl,
        @NotBlank String question,
        String sessionId
) {}
