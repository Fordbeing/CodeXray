package com.codexray.model.dto;

public record UpdateProfileRequest(
        String nickname,
        String githubUsername
) {}
