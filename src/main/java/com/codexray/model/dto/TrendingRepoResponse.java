package com.codexray.model.dto;

import java.time.LocalDate;

public record TrendingRepoResponse(
        String repoName,
        String repoUrl,
        String description,
        String language,
        String stars,
        String todayStars,
        String forks,
        String analysis,
        LocalDate trendDate
) {}
