package com.codexray.agent;

import com.codexray.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 报告 Agent：汇总所有分析结果，生成综合报告。
 */
@Service
public class ReporterAgent {

    private static final Logger log = LoggerFactory.getLogger(ReporterAgent.class);

    private final LlmClient llmClient;

    public ReporterAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public String generateReport(IndexerAgent.ProjectProfile profile,
                                 List<AnalyzerAgent.ModuleAnalysis> modules,
                                 ScannerAgent.ScanResult scanResult) {
        StringBuilder context = new StringBuilder();
        context.append("项目技术栈: ").append(profile.techStack()).append("\n");
        context.append("文件数: ").append(scanResult.fileCount()).append("\n");
        context.append("代码切片数: ").append(scanResult.chunkCount()).append("\n");
        context.append("顶层目录: ").append(String.join(", ", profile.topLevelDirs().keySet())).append("\n\n");

        context.append("各模块分析:\n");
        for (AnalyzerAgent.ModuleAnalysis m : modules) {
            context.append("- ").append(m.category()).append(" (")
                    .append(m.fileCount()).append(" files, ")
                    .append(m.chunkCount()).append(" chunks): ")
                    .append(m.summary()).append("\n");
        }

        try {
            String prompt = "根据以下分析结果，生成一份详细的代码分析报告 JSON：\n\n"
                    + context + "\n\n"
                    + "请以 JSON 格式输出：\n"
                    + "{\n"
                    + "  \"summary\": \"项目一句话概述\",\n"
                    + "  \"techStack\": [\"技术栈列表\"],\n"
                    + "  \"architecture\": \"架构模式\",\n"
                    + "  \"modules\": [{\"name\":\"模块名\",\"description\":\"职责\"}],\n"
                    + "  \"score\": 85,\n"
                    + "  \"strengths\": [\"优点\"],\n"
                    + "  \"improvements\": [\"改进建议\"],\n"
                    + "  \"verdict\": \"一句话总结\"\n"
                    + "}\n"
                    + "只输出 JSON，不要其他内容。";

            String response = llmClient.chat("", prompt);
            // 清理 markdown 代码块
            response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            return response;
        } catch (Exception e) {
            log.error("Report generation failed", e);
            return "{\"summary\":\"Analysis completed\",\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
