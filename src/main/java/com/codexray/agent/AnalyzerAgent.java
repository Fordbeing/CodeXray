package com.codexray.agent;

import com.codexray.llm.LlmClient;
import com.codexray.rag.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 分析 Agent：Map-Reduce 模式分模块深度分析。
 * 先按 category 分组，每组内按文件分析（Map），再合并为模块级摘要（Reduce）。
 */
@Service
public class AnalyzerAgent {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerAgent.class);

    private final LlmClient llmClient;
    private final VectorStoreService vectorStoreService;

    public AnalyzerAgent(LlmClient llmClient, VectorStoreService vectorStoreService) {
        this.llmClient = llmClient;
        this.vectorStoreService = vectorStoreService;
    }

    public List<ModuleAnalysis> analyze(String taskId, String repoPath) {
        // 从 ES 按 category 获取切片
        List<String> categories = List.of("controller", "service", "model", "config", "data", "util", "source");
        List<ModuleAnalysis> results = new ArrayList<>();

        for (String category : categories) {
            List<VectorStoreService.CodeChunkDoc> chunks = vectorStoreService.search(
                    taskId, new float[768], 50, category
            );
            if (chunks.isEmpty()) continue;

            log.info("AnalyzerAgent: analyzing {} chunks in category {}", chunks.size(), category);

            // Map 阶段：按文件分组，每组 2-3 个 chunk 分析
            Map<String, List<VectorStoreService.CodeChunkDoc>> byFile = chunks.stream()
                    .collect(Collectors.groupingBy(VectorStoreService.CodeChunkDoc::file_path));

            List<String> fileAnalyses = new ArrayList<>();
            for (Map.Entry<String, List<VectorStoreService.CodeChunkDoc>> entry : byFile.entrySet()) {
                String filePath = entry.getKey();
                List<VectorStoreService.CodeChunkDoc> fileChunks = entry.getValue();

                StringBuilder codeContext = new StringBuilder();
                int chunkCount = 0;
                for (VectorStoreService.CodeChunkDoc chunk : fileChunks) {
                    codeContext.append("📄 ").append(chunk.file_path())
                            .append(":").append(chunk.start_line()).append("-").append(chunk.end_line());
                    if (chunk.symbol_name() != null) {
                        codeContext.append(" [").append(chunk.symbol_name()).append("]");
                    }
                    codeContext.append("\n```\n").append(chunk.content()).append("\n```\n\n");
                    chunkCount++;
                    if (chunkCount >= 3) break; // 每文件最多 3 个 chunk
                }

                try {
                    String analysis = llmClient.chat("",
                            "用 1-2 句话总结以下代码文件的职责和关键方法：\n\n" + codeContext);
                    fileAnalyses.add(filePath + ": " + analysis);
                } catch (Exception e) {
                    log.warn("File analysis failed for {}: {}", filePath, e.getMessage());
                    fileAnalyses.add(filePath + ": (analysis failed)");
                }
            }

            // Reduce 阶段：合并文件分析为模块摘要
            String moduleSummary;
            if (fileAnalyses.size() == 1) {
                moduleSummary = fileAnalyses.get(0);
            } else {
                try {
                    String reducePrompt = "以下是 " + category + " 模块中各文件的分析结果，"
                            + "请合并为一段简洁的模块级摘要（3-5 句话）：\n\n"
                            + String.join("\n", fileAnalyses);
                    moduleSummary = llmClient.chat("", reducePrompt);
                } catch (Exception e) {
                    moduleSummary = String.join("; ", fileAnalyses.stream()
                            .limit(5).toList());
                }
            }

            results.add(new ModuleAnalysis(category, chunks.size(), byFile.size(), moduleSummary));
        }

        return results;
    }

    public record ModuleAnalysis(
            String category,
            int chunkCount,
            int fileCount,
            String summary
    ) {}
}
