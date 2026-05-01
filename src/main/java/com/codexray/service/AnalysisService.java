package com.codexray.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codexray.agent.AnalysisPipeline;
import com.codexray.mapper.AnalysisTaskMapper;
import com.codexray.model.dto.AnalysisResultResponse;
import com.codexray.model.dto.RepoPreviewResponse;
import com.codexray.model.entity.AnalysisTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final AnalysisTaskMapper taskMapper;
    private final GitCloneService gitCloneService;
    private final CodeReaderService codeReaderService;
    private final AnalysisPipeline analysisPipeline;

    public AnalysisService(AnalysisTaskMapper taskMapper, GitCloneService gitCloneService,
                           CodeReaderService codeReaderService, AnalysisPipeline analysisPipeline) {
        this.taskMapper = taskMapper;
        this.gitCloneService = gitCloneService;
        this.codeReaderService = codeReaderService;
        this.analysisPipeline = analysisPipeline;
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
        String localPath = null;
        try {
            updateStatus(id, "CLONING");
            localPath = gitCloneService.clone(repoUrl);

            analysisPipeline.execute(taskId, id, localPath, repoUrl);

        } catch (Exception e) {
            log.error("Analysis failed for task: {}", taskId, e);
            AnalysisTask task = new AnalysisTask();
            task.setId(id);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        } finally {
            if (localPath != null) {
                gitCloneService.cleanup(localPath);
            }
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

    public List<AnalysisResultResponse> listTasks(int limit) {
        QueryWrapper<AnalysisTask> wrapper = new QueryWrapper<AnalysisTask>()
                .orderByDesc("created_at")
                .last("LIMIT " + Math.min(limit, 100));
        return taskMapper.selectList(wrapper).stream()
                .map(t -> new AnalysisResultResponse(
                        t.getTaskId(), t.getRepoUrl(), t.getStatus(),
                        t.getReport(), t.getErrorMessage(),
                        t.getCreatedAt(), t.getUpdatedAt()))
                .toList();
    }

    public boolean deleteTask(String taskId) {
        return taskMapper.delete(
                new QueryWrapper<AnalysisTask>().eq("task_id", taskId)
        ) > 0;
    }

    public RepoPreviewResponse previewRepo(String repoUrl) {
        String localPath = null;
        try {
            localPath = gitCloneService.clone(repoUrl);
            return codeReaderService.preview(repoUrl, localPath);
        } catch (Exception e) {
            log.error("Preview failed for repo: {}", repoUrl, e);
            throw new RuntimeException("Preview failed: " + e.getMessage(), e);
        } finally {
            if (localPath != null) {
                gitCloneService.cleanup(localPath);
            }
        }
    }

    private void updateStatus(Long id, String status) {
        AnalysisTask task = new AnalysisTask();
        task.setId(id);
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }
}
