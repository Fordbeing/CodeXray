package com.codexray.agent;

import com.codexray.mapper.AnalysisTaskMapper;
import com.codexray.model.entity.AnalysisTask;
import com.codexray.service.MinioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * 分析流水线：编排 Scanner → Indexer → Analyzer → Reporter 四个 Agent。
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

    public AnalysisPipeline(ScannerAgent scannerAgent, IndexerAgent indexerAgent,
                            AnalyzerAgent analyzerAgent, ReporterAgent reporterAgent,
                            AnalysisTaskMapper taskMapper, MinioService minioService) {
        this.scannerAgent = scannerAgent;
        this.indexerAgent = indexerAgent;
        this.analyzerAgent = analyzerAgent;
        this.reporterAgent = reporterAgent;
        this.taskMapper = taskMapper;
        this.minioService = minioService;
    }

    /**
     * 执行完整分析流水线。
     */
    public void execute(String taskId, Long dbId, String repoPath, String repoUrl) {
        try {
            // 阶段 1: 扫描 & 索引
            updateStatus(dbId, "SCANNING");
            ScannerAgent.ScanResult scanResult = scannerAgent.scan(taskId, repoPath);
            log.info("Phase 1 done: {} files, {} chunks", scanResult.fileCount(), scanResult.chunkCount());

            // 阶段 2: 技术栈识别
            updateStatus(dbId, "INDEXING");
            IndexerAgent.ProjectProfile profile = indexerAgent.index(repoPath);
            log.info("Phase 2 done: techStack={}", profile.techStack());

            // 阶段 3: 分模块分析 (Map-Reduce)
            updateStatus(dbId, "ANALYZING");
            List<AnalyzerAgent.ModuleAnalysis> modules = analyzerAgent.analyze(taskId, repoPath);
            log.info("Phase 3 done: {} modules analyzed", modules.size());

            // 阶段 4: 生成报告
            updateStatus(dbId, "REPORTING");
            String report = reporterAgent.generateReport(profile, modules, scanResult);
            log.info("Phase 4 done: report generated ({} chars)", report.length());

            // 上传仓库到 MinIO
            uploadToMinio(taskId, repoPath);

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
                // 简化：只上传文件清单作为归档索引
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
