package com.codexray.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codexray.agent.AnalysisPipeline;
import com.codexray.mapper.AnalysisTaskMapper;
import com.codexray.model.dto.AnalysisResultResponse;
import com.codexray.model.dto.RepoPreviewResponse;
import com.codexray.model.entity.AnalysisTask;
import com.codexray.rag.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final AnalysisTaskMapper taskMapper;
    private final GitCloneService gitCloneService;
    private final CodeReaderService codeReaderService;
    private final AnalysisPipeline analysisPipeline;
    private final VectorStoreService vectorStoreService;
    private final MinioService minioService;

    // 预览缓存: repoUrl → preview result
    private final ConcurrentHashMap<String, RepoPreviewResponse> previewCache = new ConcurrentHashMap<>();

    public AnalysisService(AnalysisTaskMapper taskMapper, GitCloneService gitCloneService,
                           CodeReaderService codeReaderService, AnalysisPipeline analysisPipeline,
                           VectorStoreService vectorStoreService, MinioService minioService) {
        this.taskMapper = taskMapper;
        this.gitCloneService = gitCloneService;
        this.codeReaderService = codeReaderService;
        this.analysisPipeline = analysisPipeline;
        this.vectorStoreService = vectorStoreService;
        this.minioService = minioService;
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
        // 1. 删除 Elasticsearch 向量索引
        try {
            vectorStoreService.deleteByTaskId(taskId);
            log.info("Deleted ES chunks for task: {}", taskId);
        } catch (Exception e) {
            log.warn("Failed to delete ES chunks for task {}: {}", taskId, e.getMessage());
        }

        // 2. 删除 MinIO 归档文件
        try {
            String objectName = "repos/" + taskId + "/archive.tar.gz";
            if (minioService.exists(objectName)) {
                minioService.delete(objectName);
                log.info("Deleted MinIO archive for task: {}", taskId);
            }
        } catch (Exception e) {
            log.warn("Failed to delete MinIO archive for task {}: {}", taskId, e.getMessage());
        }

        // 3. 删除数据库记录
        return taskMapper.delete(
                new QueryWrapper<AnalysisTask>().eq("task_id", taskId)
        ) > 0;
    }

    public RepoPreviewResponse previewRepo(String repoUrl) {
        // 命中缓存直接返回
        RepoPreviewResponse cached = previewCache.get(repoUrl);
        if (cached != null) {
            log.debug("Preview cache hit for: {}", repoUrl);
            return cached;
        }

        String localPath = null;
        try {
            localPath = gitCloneService.clone(repoUrl);
            RepoPreviewResponse preview = codeReaderService.preview(repoUrl, localPath);
            previewCache.put(repoUrl, preview);
            return preview;
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
