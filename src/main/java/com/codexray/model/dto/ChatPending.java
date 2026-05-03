package com.codexray.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatPending(
        String pollId,
        String sessionId,
        String role,
        String content,
        boolean done,
        boolean streaming,
        String error,
        LocalDateTime timestamp,
        List<String> suggestions
) {
    // 兼容旧构造器
    public ChatPending(String pollId, String sessionId, String role, String content,
                       boolean done, boolean streaming, String error, LocalDateTime timestamp) {
        this(pollId, sessionId, role, content, done, streaming, error, timestamp, null);
    }
}
