package com.codexray.model.dto;

public record ComparisonResult(
        AnalysisResultResponse taskA,
        AnalysisResultResponse taskB,
        String comparison,
        int scoreDiff
) {}
