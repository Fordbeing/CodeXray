package com.codexray.model.dto;

import java.util.List;

public record ChatResponse(
        String sessionId,
        String answer,
        String timestamp,
        List<String> suggestions
) {}
