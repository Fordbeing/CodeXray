package com.codexray.agent;

import com.codexray.agent.plan.AnalysisStep;
import com.codexray.agent.plan.AnalysisTaskPlan;
import com.codexray.llm.LlmClient;
import com.codexray.mapper.AnalysisTaskMapper;
import com.codexray.model.entity.AnalysisTask;
import com.codexray.service.AnalysisEventService;
import com.codexray.service.MinioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPOutputStream;

/**
 * 分析流水线：编排 Scanner → Indexer → Analyzer → Reporter 四个 Agent。
 * 使用虚拟线程并行执行 Stage 1 (Scanning) 和 Stage 2 (Indexing)。
 */
@Service
public class AnalysisPipeline {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPipeline.class);

    private final ScannerAgent scannerAgent;
    private final IndexerAgent indexerAgent;
    private final AnalyzerAgent analyzerAgent;
    private final ReporterAgent reporterAgent;
    private final AnalysisTaskMapper taskMapper;
    private final MinioService minioService;
    private final LlmClient llmClient;
    private final AnalysisEventService eventService;
    private final PlannerAgent plannerAgent;
    private final ExecutorService vtExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AnalysisPipeline(ScannerAgent scannerAgent, IndexerAgent indexerAgent,
                            AnalyzerAgent analyzerAgent, ReporterAgent reporterAgent,
                            AnalysisTaskMapper taskMapper, MinioService minioService,
                            LlmClient llmClient, AnalysisEventService eventService,
                            PlannerAgent plannerAgent) {
        this.scannerAgent = scannerAgent;
        this.indexerAgent = indexerAgent;
        this.analyzerAgent = analyzerAgent;
        this.reporterAgent = reporterAgent;
        this.taskMapper = taskMapper;
        this.minioService = minioService;
        this.llmClient = llmClient;
        this.eventService = eventService;
        this.plannerAgent = plannerAgent;
    }

    /**
     * 执行完整分析流水线。
     * Stage 1 (Scanning) 和 Stage 2 (Indexing) 通过虚拟线程并行执行。
     */
    public void execute(String taskId, Long dbId, String repoPath, String repoUrl) {
        try {
            // Stage 1 + 2 + AI 检查并行执行（虚拟线程）
            updateStatus(dbId, "SCANNING");
            publishEvent(taskId, "status", "SCANNING");
            publishEvent(taskId, "progress", 10);

            Future<Boolean> aiCheckFuture = vtExecutor.submit(() -> {
                try {
                    llmClient.testConnection();
                    log.info("AI pre-flight check passed for task: {}", taskId);
                    return true;
                } catch (Exception e) {
                    log.error("AI pre-flight check failed for task {}: {}", taskId, e.getMessage());
                    return false;
                }
            });

            Future<ScannerAgent.ScanResult> scanFuture = vtExecutor.submit(
                    () -> scannerAgent.scan(taskId, repoPath));

            Future<IndexerAgent.ProjectProfile> indexFuture = vtExecutor.submit(
                    () -> indexerAgent.index(repoPath));

            // 检查 AI 连接（与扫描并行，不增加延迟）
            try {
                if (!aiCheckFuture.get(30, TimeUnit.SECONDS)) {
                    AnalysisTask task = new AnalysisTask();
                    task.setId(dbId);
                    task.setStatus("FAILED");
                    task.setErrorMessage("AI 模型连接失败。请在「系统设置」中检查 AI 配置后重试。");
                    task.setUpdatedAt(LocalDateTime.now());
                    taskMapper.updateById(task);
                    return;
                }
            } catch (Exception e) {
                log.warn("AI check timed out, proceeding with analysis: {}", e.getMessage());
            }

            ScannerAgent.ScanResult scanResult = scanFuture.get(5, TimeUnit.MINUTES);
            log.info("Phase 1 done: {} files, {} chunks", scanResult.fileCount(), scanResult.chunkCount());
            publishEvent(taskId, "message", "已扫描 " + scanResult.fileCount() + " 个文件，生成 " + scanResult.chunkCount() + " 个代码切片");
            publishEvent(taskId, "progress", 30);

            IndexerAgent.ProjectProfile profile = indexFuture.get(5, TimeUnit.MINUTES);
            log.info("Phase 2 done: techStack={}", profile.techStack());
            publishEvent(taskId, "message", "技术栈识别完成: " + profile.techStack());
            publishEvent(taskId, "progress", 40);

            // Stage 3: ANALYZING — 使用 PlannerAgent 生成动态执行计划
            updateStatus(dbId, "ANALYZING");
            publishEvent(taskId, "status", "ANALYZING");
            publishEvent(taskId, "progress", 45);

            AnalysisTaskPlan plan = plannerAgent.createPlan(profile);
            log.info("Planner generated plan ({} steps): {}", plan.steps().size(), plan.rationale());
            publishEvent(taskId, "message", "执行计划: " + plan.rationale());

            // 从计划中提取 analyzer 类别（如有指定）
            List<String> analyzerCategories = plan.steps().stream()
                    .filter(s -> "analyzer".equals(s.agentName()))
                    .findFirst()
                    .map(s -> {
                        @SuppressWarnings("unchecked")
                        List<String> cats = (List<String>) s.params().get("categories");
                        return cats;
                    })
                    .orElse(null);

            publishEvent(taskId, "progress", 50);
            List<AnalyzerAgent.ModuleAnalysis> modules;
            if (analyzerCategories != null && !analyzerCategories.isEmpty()) {
                log.info("Running analyzer with planned categories: {}", analyzerCategories);
                modules = analyzerAgent.analyzeParallel(taskId, vtExecutor, analyzerCategories);
            } else {
                modules = analyzerAgent.analyzeParallel(taskId, vtExecutor);
            }
            log.info("Phase 3 done: {} modules analyzed", modules.size());
            publishEvent(taskId, "message", "已分析 " + modules.size() + " 个模块");
            publishEvent(taskId, "progress", 75);

            // Stage 4 + MinIO 上传并行
            updateStatus(dbId, "REPORTING");
            publishEvent(taskId, "status", "REPORTING");
            publishEvent(taskId, "progress", 80);
            Future<String> reportFuture = vtExecutor.submit(
                    () -> reporterAgent.generateReport(profile, modules, scanResult));
            Future<Void> uploadFuture = vtExecutor.submit(
                    () -> { uploadToMinio(taskId, repoPath); return null; });

            String report = reportFuture.get(3, TimeUnit.MINUTES);
            log.info("Phase 4 done: report generated ({} chars)", report.length());
            publishEvent(taskId, "progress", 95);

            try { uploadFuture.get(); } catch (Exception e) {
                log.warn("MinIO upload failed: {}", e.getMessage());
            }

            // 完成
            AnalysisTask task = new AnalysisTask();
            task.setId(dbId);
            task.setStatus("COMPLETED");
            task.setReport(report);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            publishEvent(taskId, "progress", 100);
            publishEvent(taskId, "status", "COMPLETED");
            eventService.completeAll(taskId);
            log.info("Analysis pipeline completed for task: {}", taskId);

        } catch (Exception e) {
            log.error("Analysis pipeline failed for task: {}", taskId, e);
            AnalysisTask task = new AnalysisTask();
            task.setId(dbId);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            publishEvent(taskId, "status", "FAILED");
            publishEvent(taskId, "error", e.getMessage());
            eventService.completeAll(taskId);
        }
    }

    private void updateStatus(Long id, String status) {
        AnalysisTask task = new AnalysisTask();
        task.setId(id);
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void publishEvent(String taskId, String event, Object data) {
        try {
            eventService.publish(taskId, event, data);
        } catch (Exception e) {
            log.debug("Failed to publish SSE event for task {}: {}", taskId, e.getMessage());
        }
    }

    /**
     * 将仓库压缩上传到 MinIO 归档。
     */
    private void uploadToMinio(String taskId, String repoPath) {
        try {
            Path root = Path.of(repoPath);
            String objectName = "repos/" + taskId + "/archive.tar.gz";

            // 创建 tar.gz 归档，包含实际文件内容
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gos = new GZIPOutputStream(baos)) {
                List<Path> files = Files.walk(root)
                        .filter(Files::isRegularFile)
                        .toList();
                for (Path file : files) {
                    String entryName = root.relativize(file).toString().replace('\\', '/');
                    byte[] content = Files.readAllBytes(file);

                    // 写入 tar header (512 bytes)
                    byte[] header = new byte[512];
                    writeTarString(header, 0, 100, entryName);
                    writeTarOctal(header, 100, 8, 0644L); // mode
                    writeTarOctal(header, 108, 8, 0L);     // uid
                    writeTarOctal(header, 116, 8, 0L);     // gid
                    writeTarOctal(header, 124, 12, content.length);
                    writeTarOctal(header, 136, 12, System.currentTimeMillis() / 1000);
                    header[156] = '0'; // regular file

                    // 计算 checksum
                    long checksum = 0;
                    for (int i = 0; i < 512; i++) checksum += (header[i] & 0xFF);
                    for (int i = 0; i < 8; i++) header[148 + i] = (byte) ' ';
                    writeTarOctal(header, 148, 7, checksum);

                    gos.write(header);

                    // 写入文件内容
                    gos.write(content);
                    // tar block 对齐到 512 字节
                    int remainder = content.length % 512;
                    if (remainder > 0) {
                        gos.write(new byte[512 - remainder]);
                    }
                }
            }

            minioService.upload(objectName, baos.toByteArray(), "application/gzip");
            log.info("Uploaded repo archive to MinIO: {}", objectName);
        } catch (Exception e) {
            log.warn("Failed to upload repo to MinIO: {}", e.getMessage());
        }
    }

    private static void writeTarString(byte[] header, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(bytes.length, length - 1);
        System.arraycopy(bytes, 0, header, offset, len);
    }

    private static void writeTarOctal(byte[] header, int offset, int length, long value) {
        String octal = Long.toOctalString(value);
        byte[] bytes = octal.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(bytes.length, length - 1);
        System.arraycopy(bytes, 0, header, offset + length - 1 - len, len);
        header[offset + length - 1] = 0;
    }
}
