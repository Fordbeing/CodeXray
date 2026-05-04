package com.codexray.model.dto;

import java.util.List;

public record CrossRepoChatRequest(
        String question,
        List<String> taskIds
) {}
