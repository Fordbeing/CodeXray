package com.codexray.model.dto;

import java.time.LocalDateTime;

public record ChatPending(
        String pollId,
        String sessionId,
        String role,
        String content,
        boolean done,
        boolean streaming,
        String error,
        LocalDateTime timestamp
) {}
