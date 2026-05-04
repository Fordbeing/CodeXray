package com.codexray.service;

import com.codexray.model.dto.ArchitectureGraph;
import com.codexray.rag.VectorStoreService;
import com.codexray.rag.VectorStoreService.CodeChunkDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ArchitectureService {

    private static final Logger log = LoggerFactory.getLogger(ArchitectureService.class);

    private final VectorStoreService vectorStoreService;

    public ArchitectureService(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    public ArchitectureGraph buildGraph(String taskId) {
        List<CodeChunkDoc> chunks = vectorStoreService.searchByCategory(taskId, null, 5000);
        if (chunks.isEmpty()) {
            return new ArchitectureGraph(List.of(), List.of(), Map.of());
        }

        // 按 category 分组
        Map<String, List<CodeChunkDoc>> byCategory = chunks.stream()
                .filter(c -> c.category() != null && !c.category().isBlank())
                .collect(Collectors.groupingBy(CodeChunkDoc::category));

        // 构建节点：包含文件列表、符号列表、chunk 数量
        List<ArchitectureGraph.GraphNode> nodes = new ArrayList<>();
        Map<String, Set<String>> categoryFiles = new HashMap<>();
        for (var entry : byCategory.entrySet()) {
            String category = entry.getKey();
            List<CodeChunkDoc> catChunks = entry.getValue();

            Set<String> filePaths = catChunks.stream()
                    .map(CodeChunkDoc::file_path)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(TreeSet::new));
            categoryFiles.put(category, filePaths);

            // 收集该 category 的符号名（去重，最多 20 个）
            List<String> symbols = catChunks.stream()
                    .map(CodeChunkDoc::symbol_name)
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> s.replaceAll("[<>]", "").trim())
                    .filter(s -> !s.isEmpty() && s.length() > 2)
                    .distinct()
                    .limit(20)
                    .toList();

            nodes.add(new ArchitectureGraph.GraphNode(
                    category,
                    category,
                    category,
                    filePaths.size(),
                    new ArrayList<>(filePaths),
                    symbols,
                    catChunks.size()
            ));
        }

        // 构建边：分析跨 category 的符号引用，记录匹配的符号名
        Map<String, Set<String>> categorySymbols = new HashMap<>();
        for (var entry : byCategory.entrySet()) {
            Set<String> symbols = entry.getValue().stream()
                    .map(CodeChunkDoc::symbol_name)
                    .filter(s -> s != null && !s.isBlank())
                    .map(s -> s.replaceAll("[<>]", "").trim())
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            categorySymbols.put(entry.getKey(), symbols);
        }

        Map<String, Integer> edgeWeights = new HashMap<>();
        Map<String, Set<String>> edgeSymbols = new HashMap<>();
        for (var srcEntry : byCategory.entrySet()) {
            String srcCat = srcEntry.getKey();
            for (CodeChunkDoc chunk : srcEntry.getValue()) {
                String content = chunk.content();
                if (content == null) continue;
                for (var tgtEntry : categorySymbols.entrySet()) {
                    String tgtCat = tgtEntry.getKey();
                    if (srcCat.equals(tgtCat)) continue;
                    for (String symbol : tgtEntry.getValue()) {
                        if (symbol.length() > 3 && content.contains(symbol)) {
                            String edgeKey = srcCat + "->" + tgtCat;
                            edgeWeights.merge(edgeKey, 1, Integer::sum);
                            edgeSymbols.computeIfAbsent(edgeKey, k -> new HashSet<>()).add(symbol);
                        }
                    }
                }
            }
        }

        // 构建边列表（限制权重阈值）
        List<ArchitectureGraph.GraphEdge> edges = new ArrayList<>();
        int maxWeight = edgeWeights.values().stream().max(Integer::compareTo).orElse(1);
        int threshold = Math.max(1, maxWeight / 10);
        for (var entry : edgeWeights.entrySet()) {
            if (entry.getValue() < threshold) continue;
            String[] parts = entry.getKey().split("->");
            Set<String> syms = edgeSymbols.getOrDefault(entry.getKey(), Set.of());
            // 每条边最多展示 10 个代表性符号
            List<String> topSymbols = syms.stream().limit(10).toList();
            edges.add(new ArchitectureGraph.GraphEdge(parts[0], parts[1], entry.getValue(), topSymbols));
        }

        // 统计
        Map<String, Integer> stats = new LinkedHashMap<>();
        byCategory.forEach((cat, chks) -> stats.put(cat, chks.size()));

        log.info("Built architecture graph for task {}: {} nodes, {} edges", taskId, nodes.size(), edges.size());
        return new ArchitectureGraph(nodes, edges, stats);
    }
}
