package com.codexray.model.dto;

import java.util.List;

public record CodeTour(
        String title,
        String summary,
        List<TourStop> stops
) {
    public record TourStop(
            int step,
            String title,
            String explanation,
            List<String> filePaths,
            String category,
            String nextHint
    ) {}
}
