package com.codexray.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codexray.llm.LlmClient;
import com.codexray.mapper.AnalysisTaskMapper;
import com.codexray.model.dto.AnalysisResultResponse;
import com.codexray.model.entity.AnalysisTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final AnalysisTaskMapper taskMapper;
    private final GitCloneService gitCloneService;
    private final LlmClient llmClient;

    public AnalysisService(AnalysisTaskMapper taskMapper, GitCloneService gitCloneService, LlmClient llmClient) {
        this.taskMapper = taskMapper;
        this.gitCloneService = gitCloneService;
        this.llmClient = llmClient;
    }

    public String submitAnalysis(String repoUrl) {
        AnalysisTask task = new AnalysisTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setRepoUrl(repoUrl);
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        asyncAnalyze(task.getId(), task.getTaskId(), repoUrl);

        return task.getTaskId();
    }

    @Async("analysisExecutor")
    public void asyncAnalyze(Long id, String taskId, String repoUrl) {
        try {
            updateStatus(id, "CLONING");
            String localPath = gitCloneService.clone(repoUrl);

            updateStatus(id, "ANALYZING");
            String report = llmClient.analyze(localPath);

            AnalysisTask task = new AnalysisTask();
            task.setId(id);
            task.setStatus("COMPLETED");
            task.setReport(report);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            log.info("Analysis completed for task: {}", taskId);
        } catch (Exception e) {
            log.error("Analysis failed for task: {}", taskId, e);

            AnalysisTask task = new AnalysisTask();
            task.setId(id);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    public AnalysisResultResponse getResult(String taskId) {
        AnalysisTask task = taskMapper.selectOne(
                new QueryWrapper<AnalysisTask>().eq("task_id", taskId)
        );
        if (task == null) {
            throw new RuntimeException("Task not found: " + taskId);
        }
        return new AnalysisResultResponse(
                task.getTaskId(),
                task.getRepoUrl(),
                task.getStatus(),
                task.getReport(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private void updateStatus(Long id, String status) {
        AnalysisTask task = new AnalysisTask();
        task.setId(id);
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }
}
