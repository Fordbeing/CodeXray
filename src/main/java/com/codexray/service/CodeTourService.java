package com.codexray.service;

import com.codexray.llm.LlmClient;
import com.codexray.model.dto.CodeTour;
import com.codexray.rag.VectorStoreService;
import com.codexray.rag.VectorStoreService.CodeChunkDoc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CodeTourService {

    private static final Logger log = LoggerFactory.getLogger(CodeTourService.class);
    private static final String CACHE_PREFIX = "codexray:tour:";
    private static final Duration CACHE_TTL = Duration.ofDays(7);

    private final VectorStoreService vectorStoreService;
    private final AnalysisService analysisService;
    private final LlmClient llmClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public CodeTourService(VectorStoreService vectorStoreService, AnalysisService analysisService,
                           LlmClient llmClient, RedisTemplate<String, String> redisTemplate,
                           ObjectMapper objectMapper) {
        this.vectorStoreService = vectorStoreService;
        this.analysisService = analysisService;
        this.llmClient = llmClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public CodeTour generateTour(String taskId) {
        // 检查缓存
        String cacheKey = CACHE_PREFIX + taskId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                CodeTour cachedTour = objectMapper.readValue(cached, CodeTour.class);
                if (cachedTour.stops() != null && !cachedTour.stops().isEmpty()) {
                    return cachedTour;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read tour cache: {}", e.getMessage());
        }

        // 获取分析报告
        var result = analysisService.getResult(taskId);
        String reportSummary = "";
        if (result.report() != null && !result.report().isBlank()) {
            reportSummary = result.report().length() > 1000
                    ? result.report().substring(0, 1000) + "..."
                    : result.report();
        }

        // 获取代码切片（限制数量避免上下文过大）
        List<CodeChunkDoc> allChunks = vectorStoreService.searchByCategory(taskId, null, 2000);
        if (allChunks.isEmpty()) {
            throw new RuntimeException("没有找到代码数据");
        }

        // 按 category 分组
        Map<String, List<CodeChunkDoc>> byCategory = allChunks.stream()
                .filter(c -> c.category() != null)
                .collect(Collectors.groupingBy(CodeChunkDoc::category));

        // 构建精简上下文：每个 category 取最多 3 个文件，每个文件取最大 chunk 前 400 字符
        StringBuilder ctx = new StringBuilder();
        if (!reportSummary.isEmpty()) {
            ctx.append("## 项目概要\n").append(reportSummary).append("\n\n");
        }

        ctx.append("## 代码模块\n");
        // 按文件数排序，优先展示重要模块
        List<Map.Entry<String, List<CodeChunkDoc>>> sorted = byCategory.entrySet().stream()
                .sorted((a, b) -> {
                    long filesA = a.getValue().stream().map(CodeChunkDoc::file_path).distinct().count();
                    long filesB = b.getValue().stream().map(CodeChunkDoc::file_path).distinct().count();
                    return Long.compare(filesB, filesA);
                })
                .toList();

        for (var entry : sorted) {
            String cat = entry.getKey();
            List<CodeChunkDoc> chunks = entry.getValue();

            // 收集该 category 的文件
            Set<String> files = new LinkedHashSet<>();
            for (CodeChunkDoc c : chunks) {
                if (c.file_path() != null) files.add(c.file_path());
            }

            ctx.append("\n### ").append(cat).append(" (").append(files.size()).append(" 文件)\n");
            // 列出最多 8 个文件
            int fileIdx = 0;
            for (String f : files) {
                if (fileIdx++ >= 8) { ctx.append("- ...等更多文件\n"); break; }
                ctx.append("- ").append(f).append("\n");
            }

            // 每个文件取最大的 chunk 的前 400 字符作为代码示例（最多 3 个文件）
            Map<String, List<CodeChunkDoc>> byFile = chunks.stream()
                    .filter(c -> c.file_path() != null)
                    .collect(Collectors.groupingBy(CodeChunkDoc::file_path));
            int fileCount = 0;
            for (var fileEntry : byFile.entrySet()) {
                if (fileCount++ >= 3) break;
                String content = fileEntry.getValue().stream()
                        .max(Comparator.comparingInt(c -> c.content() != null ? c.content().length() : 0))
                        .map(c -> c.content() != null ? c.content() : "")
                        .orElse("");
                if (content.length() > 400) content = content.substring(0, 400) + "...";
                if (!content.isEmpty()) {
                    ctx.append("\n```\n// ").append(fileEntry.getKey()).append("\n");
                    ctx.append(content).append("\n```\n");
                }
            }
        }

        // 收集所有 symbol_name 供 LLM 参考
        Set<String> allSymbols = allChunks.stream()
                .map(CodeChunkDoc::symbol_name)
                .filter(s -> s != null && !s.isBlank() && s.length() > 2)
                .limit(50)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!allSymbols.isEmpty()) {
            ctx.append("\n## 核心符号\n");
            ctx.append(String.join(", ", allSymbols));
        }

        String prompt = """
                你是一个代码导览专家。根据以下项目信息，生成一份 5-8 步的代码走读指南。

                %s

                请以 JSON 格式输出（不要输出其他内容）：
                {
                    "title": "项目名 + 代码导览",
                    "summary": "2-3句话概述这个项目的核心架构和代码组织方式",
                    "stops": [
                        {
                            "step": 1,
                            "title": "步骤标题",
                            "explanation": "详细解释这一步要读什么代码、为什么重要、关键设计决策（Markdown格式，200-400字）",
                            "filePaths": ["涉及的文件路径"],
                            "category": "模块分类",
                            "nextHint": "下一步引导"
                        }
                    ]
                }

                要求：
                1. 从项目入口开始，按请求/数据处理流程排列
                2. 每步聚焦一个模块，解释职责和设计意图
                3. 步骤间有逻辑衔接
                4. 最后一步的 nextHint 设为 null
                """.formatted(ctx.toString());

        try {
            String response = llmClient.chatWithContext(
                    "你是代码导览专家。直接输出 JSON，不要任何解释或 markdown 代码块。",
                    List.of(),
                    prompt
            );

            CodeTour tour = parseJsonResponse(response);
            if (tour == null || tour.stops() == null || tour.stops().isEmpty()) {
                throw new RuntimeException("LLM 返回了空的导览数据");
            }

            // 缓存结果
            try {
                redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(tour), CACHE_TTL);
            } catch (Exception e) {
                log.warn("Failed to cache tour: {}", e.getMessage());
            }

            return tour;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate code tour for task: {}", taskId, e);
            throw new RuntimeException("生成代码导览失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 LLM 响应中提取 JSON，支持多种格式：
     * - 纯 JSON
     * - ```json ... ``` 包裹
     * - 前后有解释文字
     */
    private CodeTour parseJsonResponse(String response) {
        if (response == null || response.isBlank()) return null;
        String text = response.trim();

        // 尝试 1: 直接解析
        try {
            return objectMapper.readValue(text, CodeTour.class);
        } catch (Exception ignored) {}

        // 尝试 2: 提取 ```json ... ``` 块
        int codeBlockStart = text.indexOf("```");
        if (codeBlockStart >= 0) {
            int firstNewline = text.indexOf('\n', codeBlockStart);
            int codeBlockEnd = text.lastIndexOf("```");
            if (firstNewline > 0 && codeBlockEnd > firstNewline) {
                String inner = text.substring(firstNewline + 1, codeBlockEnd).trim();
                try {
                    return objectMapper.readValue(inner, CodeTour.class);
                } catch (Exception ignored) {}
            }
        }

        // 尝试 3: 找 { 开头 } 结尾的 JSON 对象
        int braceStart = text.indexOf('{');
        int braceEnd = text.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            String jsonPart = text.substring(braceStart, braceEnd + 1);
            try {
                return objectMapper.readValue(jsonPart, CodeTour.class);
            } catch (Exception ignored) {}
        }

        log.error("Failed to parse CodeTour JSON from response (first 500 chars): {}",
                text.substring(0, Math.min(500, text.length())));
        return null;
    }
}
