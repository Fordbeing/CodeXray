package com.codexray.controller;

import com.codexray.agent.ReporterAgent;
import com.codexray.common.CurrentUser;
import com.codexray.common.Result;
import com.codexray.llm.LlmClient;
import com.codexray.mapper.ComparisonRecordMapper;
import com.codexray.model.dto.AnalyzeRequest;
import com.codexray.model.dto.AnalysisResultResponse;
import com.codexray.model.dto.ComparisonResult;
import com.codexray.model.dto.RepoPreviewResponse;
import com.codexray.model.entity.ComparisonRecord;
import com.codexray.rag.VectorStoreService;
import com.codexray.service.AnalysisService;
import com.codexray.service.ArchitectureService;
import com.codexray.service.CodeTourService;
import com.codexray.service.ShareService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final VectorStoreService vectorStoreService;
    private final ReporterAgent reporterAgent;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final ComparisonRecordMapper comparisonRecordMapper;
    private final ArchitectureService architectureService;
    private final CodeTourService codeTourService;
    private final ShareService shareService;

    public AnalysisController(AnalysisService analysisService, VectorStoreService vectorStoreService,
                              ReporterAgent reporterAgent, LlmClient llmClient, ObjectMapper objectMapper,
                              ComparisonRecordMapper comparisonRecordMapper,
                              ArchitectureService architectureService,
                              CodeTourService codeTourService, ShareService shareService) {
        this.analysisService = analysisService;
        this.vectorStoreService = vectorStoreService;
        this.reporterAgent = reporterAgent;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.comparisonRecordMapper = comparisonRecordMapper;
        this.architectureService = architectureService;
        this.codeTourService = codeTourService;
        this.shareService = shareService;
    }

    @PostMapping("/analyze")
    public Result<String> analyze(@Valid @RequestBody AnalyzeRequest request) {
        String taskId = analysisService.submitAnalysis(request.repoUrl());
        return Result.ok(taskId);
    }

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("文件为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || (!originalName.endsWith(".zip") && !originalName.endsWith(".tar.gz") && !originalName.endsWith(".tgz"))) {
            return Result.error("仅支持 .zip / .tar.gz 格式");
        }
        String taskId = analysisService.uploadAndAnalyze(file);
        return Result.ok(taskId);
    }

    @GetMapping("/{taskId}")
    public Result<AnalysisResultResponse> getResult(@PathVariable String taskId) {
        AnalysisResultResponse response = analysisService.getResult(taskId);
        return Result.ok(response);
    }

    @GetMapping("/list")
    public Result<List<AnalysisResultResponse>> listTasks(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(analysisService.listTasks(limit));
    }

    @DeleteMapping("/{taskId}")
    public Result<Void> deleteTask(@PathVariable String taskId) {
        if (analysisService.deleteTask(taskId)) {
            return Result.ok(null);
        }
        return Result.error("Task not found: " + taskId);
    }

    @GetMapping("/preview")
    public Result<RepoPreviewResponse> preview(@RequestParam String repoUrl) {
        return Result.ok(analysisService.previewRepo(repoUrl));
    }

    /** 获取文件树：从 ES code_chunks 聚合 file_path 构建树 */
    @GetMapping("/{taskId}/tree")
    public Result<List<Map<String, Object>>> fileTree(@PathVariable String taskId) {
        List<VectorStoreService.CodeChunkDoc> chunks = vectorStoreService.searchByCategory(taskId, null, 5000);
        // 按 file_path 去重并构建树
        Map<String, Set<String>> dirChildren = new TreeMap<>();
        Set<String> files = new TreeSet<>();
        for (var chunk : chunks) {
            String path = chunk.file_path();
            if (path == null || path.isBlank()) continue;
            files.add(path);
            String[] parts = path.split("/");
            StringBuilder dir = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (i > 0) dir.append('/');
                dir.append(parts[i]);
                String dirPath = dir.toString();
                dirChildren.computeIfAbsent(dirPath, k -> new TreeSet<>()).add(
                        i < parts.length - 1 ? parts[i + 1] : parts[i]);
            }
        }
        // 构建树形结构
        List<Map<String, Object>> tree = new ArrayList<>();
        Map<String, Map<String, Object>> nodeMap = new HashMap<>();
        for (String file : files) {
            String[] parts = file.split("/");
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) path.append('/');
                path.append(parts[i]);
                String p = path.toString();
                if (!nodeMap.containsKey(p)) {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("label", parts[i]);
                    node.put("path", p);
                    boolean isLeaf = (i == parts.length - 1);
                    node.put("isLeaf", isLeaf);
                    if (!isLeaf) node.put("children", new ArrayList<>());
                    nodeMap.put(p, node);
                    if (i == 0) {
                        tree.add(node);
                    } else {
                        String parentPath = path.substring(0, path.length() - parts[i].length() - 1);
                        Map<String, Object> parent = nodeMap.get(parentPath);
                        if (parent != null) {
                            ((List<Map<String, Object>>) parent.get("children")).add(node);
                        }
                    }
                }
            }
        }
        return Result.ok(tree);
    }

    /** 获取文件内容 */
    @GetMapping("/{taskId}/file")
    public Result<String> fileContent(@PathVariable String taskId, @RequestParam String path) {
        // 从 code_chunks 拼接文件内容
        List<VectorStoreService.CodeChunkDoc> chunks = vectorStoreService.searchByCategory(taskId, null, 5000);
        String content = chunks.stream()
                .filter(c -> path.equals(c.file_path()))
                .sorted(Comparator.comparingInt(VectorStoreService.CodeChunkDoc::start_line))
                .map(VectorStoreService.CodeChunkDoc::content)
                .collect(java.util.stream.Collectors.joining("\n"));
        if (content.isBlank()) {
            return Result.error("文件未找到: " + path);
        }
        return Result.ok(content);
    }

    /** 代码搜索 */
    @GetMapping("/{taskId}/search")
    public Result<List<VectorStoreService.CodeChunkDoc>> codeSearch(
            @PathVariable String taskId, @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        List<VectorStoreService.CodeChunkDoc> results = vectorStoreService.searchText(taskId, q, limit);
        return Result.ok(results);
    }

    /** 获取推荐问题 */
    @GetMapping("/{taskId}/questions")
    public Result<List<String>> getQuestions(@PathVariable String taskId) {
        AnalysisResultResponse result = analysisService.getResult(taskId);
        if (result.report() == null || result.report().isBlank()) {
            return Result.ok(List.of());
        }
        List<String> questions = reporterAgent.generateQuestions(result.report());
        return Result.ok(questions);
    }

    /** 获取最近通知（完成/失败的分析任务） */
    @GetMapping("/notifications")
    public Result<List<AnalysisResultResponse>> getNotifications(
            @RequestParam(defaultValue = "10") int limit) {
        List<AnalysisResultResponse> all = analysisService.listTasks(50);
        List<AnalysisResultResponse> notifications = all.stream()
                .filter(t -> "COMPLETED".equals(t.status()) || "FAILED".equals(t.status()))
                .limit(limit)
                .toList();
        return Result.ok(notifications);
    }

    /** 对比两次分析报告 */
    @GetMapping("/compare")
    public Result<ComparisonResult> compare(@RequestParam String taskA, @RequestParam String taskB) {
        AnalysisResultResponse a = analysisService.getResult(taskA);
        AnalysisResultResponse b = analysisService.getResult(taskB);
        if (a.report() == null || b.report() == null) {
            return Result.error("对比的报告尚未生成完成");
        }

        // 同仓库校验
        if (a.repoUrl() != null && b.repoUrl() != null && !a.repoUrl().equals(b.repoUrl())) {
            return Result.error("只能对比同一仓库的分析报告");
        }

        // 解析 score 计算真实 scoreDiff
        int scoreA = parseScore(a.report());
        int scoreB = parseScore(b.report());
        int scoreDiff = scoreA > 0 && scoreB > 0 ? scoreB - scoreA : 0;

        // 用 LLM 生成 markdown 格式的对比分析
        String prompt = """
                对比以下两次代码分析报告，生成 Markdown 格式的对比分析。

                报告 A (task: %s):
                %s

                报告 B (task: %s):
                %s

                请严格按照以下 Markdown 格式输出：
                ## 评分变化
                （用表格展示各维度评分的前后对比）

                ## 主要改进
                （列出 B 相比 A 的具体改进点）

                ## 新增风险
                （列出 B 中新出现的问题或风险）

                ## 总结
                （一句话总结整体变化趋势）
                """.formatted(taskA, truncate(a.report(), 2000), taskB, truncate(b.report(), 2000));

        String comparison;
        try {
            comparison = llmClient.chatWithContext("你是代码分析对比助手，输出 Markdown 格式。", java.util.List.of(), prompt);
        } catch (Exception e) {
            comparison = "对比分析生成失败: " + e.getMessage();
        }

        ComparisonResult comparisonResult = new ComparisonResult(a, b, comparison, scoreDiff);

        // 持久化保存
        try {
            ComparisonRecord record = new ComparisonRecord();
            record.setComparisonId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            record.setUserId(CurrentUser.get());
            record.setTaskA(taskA);
            record.setTaskB(taskB);
            record.setRepoUrl(a.repoUrl());
            record.setResultJson(objectMapper.writeValueAsString(comparisonResult));
            record.setScoreDiff(scoreDiff);
            record.setCreatedAt(LocalDateTime.now());
            comparisonRecordMapper.insert(record);
        } catch (JsonProcessingException e) {
            // 保存失败不影响返回
        }

        return Result.ok(comparisonResult);
    }

    /** 按 repoUrl 分组的已完成任务列表 */
    @GetMapping("/compare/tasks")
    public Result<List<Map<String, Object>>> compareTasks(@RequestParam(defaultValue = "50") int limit) {
        List<AnalysisResultResponse> all = analysisService.listTasks(limit);
        Map<String, List<AnalysisResultResponse>> grouped = new LinkedHashMap<>();
        for (AnalysisResultResponse t : all) {
            if (!"COMPLETED".equals(t.status()) || t.report() == null) continue;
            String repo = t.repoUrl() != null ? t.repoUrl() : "unknown";
            grouped.computeIfAbsent(repo, k -> new ArrayList<>()).add(t);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            if (entry.getValue().size() < 2) continue; // 只返回有 2+ 次分析的仓库
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("repoUrl", entry.getKey());
            // 按时间排序
            List<AnalysisResultResponse> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(AnalysisResultResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            group.put("tasks", sorted);
            result.add(group);
        }
        return Result.ok(result);
    }

    /** 获取对比历史列表 */
    @GetMapping("/compare/list")
    public Result<List<Map<String, Object>>> listComparisonRecords(@RequestParam(defaultValue = "30") int limit) {
        Long userId = CurrentUser.get();
        QueryWrapper<ComparisonRecord> wrapper = new QueryWrapper<>();
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        wrapper.orderByDesc("created_at")
                .last("LIMIT " + Math.min(limit, 100));

        List<ComparisonRecord> records = comparisonRecordMapper.selectList(wrapper);
        List<Map<String, Object>> list = new ArrayList<>();
        for (ComparisonRecord r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("comparisonId", r.getComparisonId());
            m.put("repoUrl", r.getRepoUrl());
            m.put("taskA", r.getTaskA());
            m.put("taskB", r.getTaskB());
            m.put("scoreDiff", r.getScoreDiff());
            m.put("createdAt", r.getCreatedAt());
            list.add(m);
        }
        return Result.ok(list);
    }

    /** 获取单条对比结果 */
    @GetMapping("/compare/{comparisonId}")
    public Result<ComparisonResult> getComparisonRecord(@PathVariable String comparisonId) {
        QueryWrapper<ComparisonRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("comparison_id", comparisonId);
        ComparisonRecord record = comparisonRecordMapper.selectOne(wrapper);
        if (record == null) {
            return Result.error("对比记录不存在");
        }
        try {
            ComparisonResult result = objectMapper.readValue(record.getResultJson(), ComparisonResult.class);
            return Result.ok(result);
        } catch (Exception e) {
            return Result.error("对比结果解析失败");
        }
    }

    /** 删除对比记录 */
    @DeleteMapping("/compare/{comparisonId}")
    public Result<Void> deleteComparisonRecord(@PathVariable String comparisonId) {
        QueryWrapper<ComparisonRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("comparison_id", comparisonId);
        comparisonRecordMapper.delete(wrapper);
        return Result.ok(null);
    }

    /** 获取架构图数据 */
    @GetMapping("/{taskId}/graph")
    public Result<com.codexray.model.dto.ArchitectureGraph> architectureGraph(@PathVariable String taskId) {
        return Result.ok(architectureService.buildGraph(taskId));
    }

    /** 获取用户分析统计 */
    @GetMapping("/stats")
    public Result<com.codexray.model.dto.UserAnalysisStats> userStats() {
        return Result.ok(analysisService.getUserStats(CurrentUser.get()));
    }

    /** 获取 AI 代码导览 */
    @GetMapping("/{taskId}/tour")
    public Result<com.codexray.model.dto.CodeTour> codeTour(@PathVariable String taskId) {
        return Result.ok(codeTourService.generateTour(taskId));
    }

    /** 创建分享链接 */
    @PostMapping("/{taskId}/share")
    public Result<Map<String, Object>> createShare(@PathVariable String taskId,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        Long userId = CurrentUser.get();
        String password = body != null ? (String) body.get("password") : null;
        int expiresInDays = body != null && body.get("expiresInDays") != null
                ? ((Number) body.get("expiresInDays")).intValue() : 0;
        return Result.ok(shareService.createShare(taskId, userId, password, expiresInDays));
    }

    /** 查看分享报告（公开接口） */
    @GetMapping("/shared/{shareToken}")
    public Result<Map<String, Object>> getSharedReport(@PathVariable String shareToken,
                                                       @RequestParam(required = false) String password) {
        return Result.ok(shareService.getSharedReport(shareToken, password));
    }

    /** 列出我的分享 */
    @GetMapping("/shares")
    public Result<List<Map<String, Object>>> listShares() {
        return Result.ok(shareService.listShares(CurrentUser.get()));
    }

    /** 撤销分享 */
    @DeleteMapping("/shares/{shareToken}")
    public Result<Void> revokeShare(@PathVariable String shareToken) {
        shareService.revokeShare(shareToken, CurrentUser.get());
        return Result.ok(null);
    }

    private int parseScore(String reportJson) {
        try {
            JsonNode root = objectMapper.readTree(reportJson);
            return root.path("score").asInt(0);
        } catch (Exception e) {
            return 0;
        }
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : (s != null ? s : "");
    }
}
