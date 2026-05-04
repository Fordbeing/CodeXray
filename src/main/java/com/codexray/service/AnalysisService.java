package com.codexray.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codexray.agent.AnalysisPipeline;
import com.codexray.common.CurrentUser;
import com.codexray.mapper.CodeChunkMapper;
import com.codexray.mapper.AnalysisTaskMapper;
import com.codexray.mapper.ChatHistoryMapper;
import com.codexray.mapper.CodeReviewRecordMapper;
import com.codexray.model.dto.AnalysisResultResponse;
import com.codexray.model.dto.RepoPreviewResponse;
import com.codexray.model.dto.UserAnalysisStats;
import com.codexray.model.entity.AnalysisTask;
import com.codexray.rag.VectorStoreService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private static final Duration CACHE_TTL = Duration.ofDays(1);
    private static final String PREVIEW_KEY_PREFIX = "codexray:preview:";
    private static final String RESULT_KEY_PREFIX = "codexray:result:";

    private final AnalysisTaskMapper taskMapper;
    private final CodeChunkMapper codeChunkMapper;
    private final ChatHistoryMapper chatHistoryMapper;
    private final CodeReviewRecordMapper codeReviewRecordMapper;
    private final GitCloneService gitCloneService;
    private final CodeReaderService codeReaderService;
    private final AnalysisPipeline analysisPipeline;
    private final VectorStoreService vectorStoreService;
    private final MinioService minioService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public AnalysisService(AnalysisTaskMapper taskMapper, CodeChunkMapper codeChunkMapper,
                           ChatHistoryMapper chatHistoryMapper, CodeReviewRecordMapper codeReviewRecordMapper,
                           GitCloneService gitCloneService,
                           CodeReaderService codeReaderService, AnalysisPipeline analysisPipeline,
                           VectorStoreService vectorStoreService, MinioService minioService,
                           RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.codeChunkMapper = codeChunkMapper;
        this.chatHistoryMapper = chatHistoryMapper;
        this.codeReviewRecordMapper = codeReviewRecordMapper;
        this.gitCloneService = gitCloneService;
        this.codeReaderService = codeReaderService;
        this.analysisPipeline = analysisPipeline;
        this.vectorStoreService = vectorStoreService;
        this.minioService = minioService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String submitAnalysis(String repoUrl) {
        AnalysisTask task = new AnalysisTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setRepoUrl(repoUrl);
        task.setUserId(CurrentUser.get());
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        asyncAnalyze(task.getId(), task.getTaskId(), repoUrl);

        return task.getTaskId();
    }

    public String uploadAndAnalyze(MultipartFile file) {
        String taskId = UUID.randomUUID().toString();
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("codexray-upload-");
            String originalName = file.getOriginalFilename();
            Path targetFile = tempDir.resolve(originalName != null ? originalName : "upload.zip");
            file.transferTo(targetFile);

            // 解压
            Path extractDir = tempDir.resolve("extracted");
            Files.createDirectories(extractDir);
            if (originalName != null && originalName.endsWith(".zip")) {
                unzip(targetFile, extractDir);
            } else {
                // tar.gz - 简单解压处理
                untargz(targetFile, extractDir);
            }

            // 如果解压后只有一个顶层目录，使用它作为仓库路径
            String repoPath = extractDir.toString();
            try (var stream = Files.list(extractDir)) {
                var entries = stream.toList();
                if (entries.size() == 1 && Files.isDirectory(entries.get(0))) {
                    repoPath = entries.get(0).toString();
                }
            }

            // 创建任务
            AnalysisTask task = new AnalysisTask();
            task.setTaskId(taskId);
            task.setRepoUrl("upload:" + (originalName != null ? originalName : "archive"));
            task.setUserId(CurrentUser.get());
            task.setStatus("PENDING");
            task.setCreatedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.insert(task);

            // 启动异步分析（传递最终路径，由 pipeline 负责清理）
            asyncAnalyzeUploaded(task.getId(), taskId, repoPath, tempDir.toString());

            return taskId;
        } catch (Exception e) {
            log.error("Upload analysis failed", e);
            if (tempDir != null) cleanupDir(tempDir);
            throw new RuntimeException("上传分析失败: " + e.getMessage(), e);
        }
    }

    @Async("analysisExecutor")
    public void asyncAnalyzeUploaded(Long id, String taskId, String repoPath, String tempBaseDir) {
        try {
            updateStatus(id, "ANALYZING");
            analysisPipeline.execute(taskId, id, repoPath, "upload:" + taskId);
        } catch (Exception e) {
            log.error("Upload analysis failed for task: {}", taskId, e);
            AnalysisTask task = new AnalysisTask();
            task.setId(id);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        } finally {
            cleanupDir(Path.of(tempBaseDir));
        }
    }

    private void unzip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = destDir.resolve(entry.getName()).normalize();
                if (!entryPath.startsWith(destDir)) {
                    throw new IOException("Invalid zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(zis, entryPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private void untargz(Path targzFile, Path destDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("tar", "xzf", targzFile.toString(), "-C", destDir.toString());
        try {
            Process p = pb.start();
            int code = p.waitFor();
            if (code != 0) throw new IOException("tar extraction failed with code " + code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("tar extraction interrupted", e);
        }
    }

    private void cleanupDir(Path dir) {
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        } catch (IOException ignored) {}
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
        // 尝试从 Redis 缓存读取
        String cacheKey = RESULT_KEY_PREFIX + taskId;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                Map<String, Object> map = objectMapper.readValue(cached, new TypeReference<>() {});
                return objectMapper.convertValue(map, AnalysisResultResponse.class);
            }
        } catch (Exception e) {
            log.warn("Redis read failed for result cache: {}", e.getMessage());
        }

        AnalysisTask task = taskMapper.selectOne(
                new QueryWrapper<AnalysisTask>().eq("task_id", taskId)
        );
        if (task == null) {
            throw new RuntimeException("Task not found: " + taskId);
        }
        AnalysisResultResponse result = new AnalysisResultResponse(
                task.getTaskId(),
                task.getRepoUrl(),
                task.getStatus(),
                task.getReport(),
                task.getErrorMessage(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );

        // 只缓存已完成或失败的结果
        if ("COMPLETED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            try {
                String json = objectMapper.writeValueAsString(result);
                redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
            } catch (Exception e) {
                log.warn("Redis write failed for result cache: {}", e.getMessage());
            }
        }

        return result;
    }

    public List<AnalysisResultResponse> listTasks(int limit) {
        Long userId = CurrentUser.get();
        Page<AnalysisTask> page = new Page<>(1, Math.min(limit, 100));
        QueryWrapper<AnalysisTask> wrapper = new QueryWrapper<AnalysisTask>();
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        wrapper.orderByDesc("created_at");
        return taskMapper.selectPage(page, wrapper).getRecords().stream()
                .map(t -> new AnalysisResultResponse(
                        t.getTaskId(), t.getRepoUrl(), t.getStatus(),
                        t.getReport(), t.getErrorMessage(),
                        t.getCreatedAt(), t.getUpdatedAt()))
                .toList();
    }

    public boolean deleteTask(String taskId) {
        // 0. 清除 Redis 缓存
        try {
            redisTemplate.delete(RESULT_KEY_PREFIX + taskId);
        } catch (Exception ignored) {}

        // 1. 删除 Elasticsearch 向量索引
        try {
            vectorStoreService.deleteByTaskId(taskId);
            log.info("Deleted ES chunks for task: {}", taskId);
        } catch (Exception e) {
            log.warn("Failed to delete ES chunks for task {}: {}", taskId, e.getMessage());
        }

        // 2. 删除 MySQL code_chunk 记录
        try {
            codeChunkMapper.delete(
                    new QueryWrapper<com.codexray.model.entity.CodeChunk>().eq("task_id", taskId)
            );
            log.info("Deleted MySQL code_chunks for task: {}", taskId);
        } catch (Exception e) {
            log.warn("Failed to delete MySQL code_chunks for task {}: {}", taskId, e.getMessage());
        }

        // 3. 删除 MinIO 归档文件
        try {
            String objectName = "repos/" + taskId + "/archive.tar.gz";
            if (minioService.exists(objectName)) {
                minioService.delete(objectName);
                log.info("Deleted MinIO archive for task: {}", taskId);
            }
        } catch (Exception e) {
            log.warn("Failed to delete MinIO archive for task {}: {}", taskId, e.getMessage());
        }

        // 4. 删除数据库记录
        return taskMapper.delete(
                new QueryWrapper<AnalysisTask>().eq("task_id", taskId)
        ) > 0;
    }

    public RepoPreviewResponse previewRepo(String repoUrl) {
        // 尝试从 Redis 缓存读取
        String cacheKey = PREVIEW_KEY_PREFIX + repoUrl;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("Preview cache hit for: {}", repoUrl);
                Map<String, Object> map = objectMapper.readValue(cached, new TypeReference<>() {});
                return objectMapper.convertValue(map, RepoPreviewResponse.class);
            }
        } catch (Exception e) {
            log.warn("Redis read failed for preview cache: {}", e.getMessage());
        }

        String localPath = null;
        try {
            localPath = gitCloneService.clone(repoUrl);
            RepoPreviewResponse preview = codeReaderService.preview(repoUrl, localPath);

            // 写入 Redis 缓存
            try {
                String json = objectMapper.writeValueAsString(preview);
                redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
            } catch (Exception e) {
                log.warn("Redis write failed for preview cache: {}", e.getMessage());
            }

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

    public UserAnalysisStats getUserStats(Long userId) {
        if (userId == null) {
            return new UserAnalysisStats(0, 0, 0, 0, List.of(), List.of());
        }

        // 总分析数
        Long totalAnalyses = taskMapper.selectCount(
                new QueryWrapper<AnalysisTask>().eq("user_id", userId)
        );
        // 完成分析数
        Long completedAnalyses = taskMapper.selectCount(
                new QueryWrapper<AnalysisTask>().eq("user_id", userId).eq("status", "COMPLETED")
        );
        // 总问答数
        Long totalChats = chatHistoryMapper.selectCount(
                new QueryWrapper<com.codexray.model.entity.ChatHistory>().eq("user_id", userId)
        );
        // 总审查数
        Long totalReviews = codeReviewRecordMapper.selectCount(
                new QueryWrapper<com.codexray.model.entity.CodeReviewRecord>().eq("user_id", userId)
        );

        // 热力图：最近 180 天每天的分析次数
        LocalDate start = LocalDate.now().minusDays(179);
        List<AnalysisTask> recentTasks = taskMapper.selectList(
                new QueryWrapper<AnalysisTask>()
                        .eq("user_id", userId)
                        .ge("created_at", start.atStartOfDay())
                        .select("DATE(created_at) as created_at")
        );
        Map<LocalDate, Long> dayCounts = new HashMap<>();
        for (AnalysisTask t : recentTasks) {
            LocalDate date = t.getCreatedAt() != null ? t.getCreatedAt().toLocalDate() : null;
            if (date != null) {
                dayCounts.merge(date, 1L, Long::sum);
            }
        }
        List<UserAnalysisStats.DailyCount> heatmap = new ArrayList<>();
        for (int i = 0; i < 180; i++) {
            LocalDate d = start.plusDays(i);
            heatmap.add(new UserAnalysisStats.DailyCount(d, dayCounts.getOrDefault(d, 0L).intValue()));
        }

        // 最近分析的仓库
        List<AnalysisTask> recent = taskMapper.selectList(
                new QueryWrapper<AnalysisTask>()
                        .eq("user_id", userId)
                        .orderByDesc("created_at")
                        .last("LIMIT 5")
        );
        List<UserAnalysisStats.RepoSummary> recentRepos = recent.stream()
                .map(t -> new UserAnalysisStats.RepoSummary(
                        t.getTaskId(), t.getRepoUrl(), t.getStatus(), t.getCreatedAt()))
                .toList();

        return new UserAnalysisStats(
                totalAnalyses.intValue(),
                completedAnalyses.intValue(),
                totalChats.intValue(),
                totalReviews.intValue(),
                heatmap,
                recentRepos
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
