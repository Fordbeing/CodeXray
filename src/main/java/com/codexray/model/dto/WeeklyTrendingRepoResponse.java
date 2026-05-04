package com.codexray.model.dto;

import java.time.LocalDate;

public record WeeklyTrendingRepoResponse(
        String repoName,
        String repoUrl,
        String description,
        String language,
        String stars,
        String forks,
        String analysis,
        int daysCount,
        String totalTodayStars,
        LocalDate lastSeen
) {}
