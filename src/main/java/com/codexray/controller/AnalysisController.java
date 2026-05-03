package com.codexray.controller;

import com.codexray.agent.ReporterAgent;
import com.codexray.common.Result;
import com.codexray.model.dto.AnalyzeRequest;
import com.codexray.model.dto.AnalysisResultResponse;
import com.codexray.model.dto.RepoPreviewResponse;
import com.codexray.rag.VectorStoreService;
import com.codexray.service.AnalysisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final VectorStoreService vectorStoreService;
    private final ReporterAgent reporterAgent;

    public AnalysisController(AnalysisService analysisService, VectorStoreService vectorStoreService,
                              ReporterAgent reporterAgent) {
        this.analysisService = analysisService;
        this.vectorStoreService = vectorStoreService;
        this.reporterAgent = reporterAgent;
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
}
