package com.codexray.controller;

import com.codexray.common.Result;
import com.codexray.model.dto.AnalyzeRequest;
import com.codexray.model.dto.AnalysisResultResponse;
import com.codexray.model.dto.RepoPreviewResponse;
import com.codexray.service.AnalysisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
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
}
