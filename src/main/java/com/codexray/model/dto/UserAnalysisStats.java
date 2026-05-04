package com.codexray.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record UserAnalysisStats(
        int totalAnalyses,
        int completedAnalyses,
        int totalChats,
        int totalReviews,
        List<DailyCount> heatmap,
        List<RepoSummary> recentRepos
) {
    public record DailyCount(LocalDate date, int count) {}

    public record RepoSummary(
            String taskId,
            String repoUrl,
            String status,
            LocalDateTime createdAt
    ) {}
}
