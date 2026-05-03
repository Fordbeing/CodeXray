package com.codexray.model.dto;

import java.util.List;

public record CodeReviewResult(
        List<ReviewComment> comments,
        String summary,
        int score,
        List<HunkResult> hunkResults
) {
    public record ReviewComment(
            String file,
            int line,
            String severity, // "error", "warning", "info"
            String message
    ) {}

    public record HunkResult(
            String file,
            String header,
            String summary,
            int score,
            List<ReviewComment> comments
    ) {}
}
