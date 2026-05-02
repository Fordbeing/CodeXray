package com.codexray.agent;

import com.codexray.llm.LlmClient;
import com.codexray.rag.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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
        List<String> categories = List.of("controller", "service", "model", "config", "data", "util", "source");
        List<ModuleAnalysis> results = new ArrayList<>();

        for (String category : categories) {
            ModuleAnalysis result = analyzeCategory(taskId, category);
            if (result != null) results.add(result);
        }

        return results;
    }

    /**
     * 并行分析所有 category（虚拟线程）。
     */
    public List<ModuleAnalysis> analyzeParallel(String taskId, ExecutorService executor) {
        List<String> categories = List.of("controller", "service", "model", "config", "data", "util", "source");

        List<Future<ModuleAnalysis>> futures = new ArrayList<>();
        for (String category : categories) {
            futures.add(executor.submit(() -> analyzeCategory(taskId, category)));
        }

        List<ModuleAnalysis> results = new ArrayList<>();
        for (Future<ModuleAnalysis> future : futures) {
            try {
                ModuleAnalysis result = future.get();
                if (result != null) results.add(result);
            } catch (Exception e) {
                log.warn("Category analysis failed: {}", e.getMessage());
            }
        }
        return results;
    }

    private ModuleAnalysis analyzeCategory(String taskId, String category) {
        List<VectorStoreService.CodeChunkDoc> chunks = vectorStoreService.search(
                taskId, new float[768], 50, category
        );
        if (chunks.isEmpty()) return null;

        log.info("AnalyzerAgent: analyzing {} chunks in category {}", chunks.size(), category);

        // Map 阶段：按文件分组分析
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
                if (chunkCount >= 3) break;
            }

            try {
                String systemPrompt = "你是一个专业的代码分析专家。请基于提供的代码内容进行分析，" +
                        "指出该文件的具体职责、关键类/方法、与其他模块的关系。" +
                        "要求：基于实际代码内容分析，不要泛泛而谈，引用具体的类名和方法名。";
                String analysis = llmClient.chat(systemPrompt,
                        "分析以下代码文件的职责和关键方法，用 1-2 句话总结：\n\n" + codeContext);
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
                String reduceSystemPrompt = "你是一个专业的代码分析专家。请基于各文件的分析结果，" +
                        "合并为一个模块级摘要。要求：体现该模块的具体业务逻辑和技术特点，不要使用模板化语言。";
                String reducePrompt = "以下是 " + category + " 模块中各文件的分析结果，"
                        + "请合并为一段简洁的模块级摘要（3-5 句话），体现该模块的独特功能和设计特点：\n\n"
                        + String.join("\n", fileAnalyses);
                moduleSummary = llmClient.chat(reduceSystemPrompt, reducePrompt);
            } catch (Exception e) {
                moduleSummary = String.join("; ", fileAnalyses.stream()
                        .limit(5).toList());
            }
        }

        return new ModuleAnalysis(category, chunks.size(), byFile.size(), moduleSummary);
    }

    public record ModuleAnalysis(
            String category,
            int chunkCount,
            int fileCount,
            String summary
    ) {}
}
