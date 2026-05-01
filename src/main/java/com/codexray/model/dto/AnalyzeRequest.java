package com.codexray.model.dto;

import jakarta.validation.constraints.NotBlank;

public record AnalyzeRequest(
        @NotBlank(message = "repoUrl cannot be blank")
        String repoUrl
) {
}
