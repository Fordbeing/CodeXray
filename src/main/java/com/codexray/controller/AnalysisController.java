package com.codexray.controller;

import com.codexray.common.Result;
import com.codexray.model.dto.AnalyzeRequest;
import com.codexray.model.dto.AnalysisResultResponse;
import com.codexray.service.AnalysisService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{taskId}")
    public Result<AnalysisResultResponse> getResult(@PathVariable String taskId) {
        AnalysisResultResponse response = analysisService.getResult(taskId);
        return Result.ok(response);
    }
}
