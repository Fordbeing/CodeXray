package com.codexray.model.dto;

import java.util.List;
import java.util.Map;

public record ArchitectureGraph(
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        Map<String, Integer> categoryStats
) {
    public record GraphNode(
            String id,
            String label,
            String category,
            int fileCount,
            List<String> filePaths,
            List<String> symbols,
            int chunkCount
    ) {}

    public record GraphEdge(
            String source,
            String target,
            int weight,
            List<String> matchedSymbols
    ) {}
}
