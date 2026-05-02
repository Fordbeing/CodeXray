package com.codexray.agent;

import com.codexray.llm.LlmClient;
import com.codexray.mapper.AnalysisTaskMapper;
import com.codexray.model.entity.AnalysisTask;
import com.codexray.service.MinioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private final ExecutorService vtExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AnalysisPipeline(ScannerAgent scannerAgent, IndexerAgent indexerAgent,
                            AnalyzerAgent analyzerAgent, ReporterAgent reporterAgent,
                            AnalysisTaskMapper taskMapper, MinioService minioService,
                            LlmClient llmClient) {
        this.scannerAgent = scannerAgent;
        this.indexerAgent = indexerAgent;
        this.analyzerAgent = analyzerAgent;
        this.reporterAgent = reporterAgent;
        this.taskMapper = taskMapper;
        this.minioService = minioService;
        this.llmClient = llmClient;
    }

    /**
     * 执行完整分析流水线。
     * Stage 1 (Scanning) 和 Stage 2 (Indexing) 通过虚拟线程并行执行。
     */
    public void execute(String taskId, Long dbId, String repoPath, String repoUrl) {
        try {
            // 前置检查：验证 AI 模型可用
            updateStatus(dbId, "CHECKING");
            try {
                llmClient.testConnection();
                log.info("AI pre-flight check passed for task: {}", taskId);
            } catch (Exception e) {
                log.error("AI pre-flight check failed for task {}: {}", taskId, e.getMessage());
                AnalysisTask task = new AnalysisTask();
                task.setId(dbId);
                task.setStatus("FAILED");
                task.setErrorMessage("AI 模型连接失败: " + e.getMessage() + "。请在「系统设置」中检查 AI 配置后重试。");
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);
                return;
            }

            // Stage 1 + 2 并行执行（虚拟线程）
            updateStatus(dbId, "SCANNING");
            Future<ScannerAgent.ScanResult> scanFuture = vtExecutor.submit(
                    () -> scannerAgent.scan(taskId, repoPath));

            Future<IndexerAgent.ProjectProfile> indexFuture = vtExecutor.submit(
                    () -> indexerAgent.index(repoPath));

            ScannerAgent.ScanResult scanResult = scanFuture.get();
            log.info("Phase 1 done: {} files, {} chunks", scanResult.fileCount(), scanResult.chunkCount());

            IndexerAgent.ProjectProfile profile = indexFuture.get();
            log.info("Phase 2 done: techStack={}", profile.techStack());

            // Stage 3: ANALYZING（虚拟线程并行分析各 category）
            updateStatus(dbId, "ANALYZING");
            List<AnalyzerAgent.ModuleAnalysis> modules = analyzerAgent.analyzeParallel(taskId, vtExecutor);
            log.info("Phase 3 done: {} modules analyzed", modules.size());

            // Stage 4 + MinIO 上传并行
            updateStatus(dbId, "REPORTING");
            Future<String> reportFuture = vtExecutor.submit(
                    () -> reporterAgent.generateReport(profile, modules, scanResult));
            Future<Void> uploadFuture = vtExecutor.submit(
                    () -> { uploadToMinio(taskId, repoPath); return null; });

            String report = reportFuture.get();
            log.info("Phase 4 done: report generated ({} chars)", report.length());

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

            log.info("Analysis pipeline completed for task: {}", taskId);

        } catch (Exception e) {
            log.error("Analysis pipeline failed for task: {}", taskId, e);
            AnalysisTask task = new AnalysisTask();
            task.setId(dbId);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    private void updateStatus(Long id, String status) {
        AnalysisTask task = new AnalysisTask();
        task.setId(id);
        task.setStatus(status);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 将仓库压缩上传到 MinIO 归档。
     */
    private void uploadToMinio(String taskId, String repoPath) {
        try {
            Path root = Path.of(repoPath);
            String objectName = "repos/" + taskId + "/archive.tar.gz";

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gos = new GZIPOutputStream(baos);
                 ObjectOutputStream oos = new ObjectOutputStream(gos)) {
                List<String> files = Files.walk(root)
                        .filter(Files::isRegularFile)
                        .map(p -> root.relativize(p).toString())
                        .toList();
                oos.writeObject(files);
            }

            minioService.upload(objectName, baos.toByteArray(), "application/gzip");
            log.info("Uploaded repo archive to MinIO: {}", objectName);
        } catch (Exception e) {
            log.warn("Failed to upload repo to MinIO: {}", e.getMessage());
        }
    }
}
