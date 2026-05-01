package com.codexray.model.dto;

import java.time.LocalDateTime;

public record ChatMessage(
        String sessionId,
        String role,
        String content,
        LocalDateTime timestamp
) {}
