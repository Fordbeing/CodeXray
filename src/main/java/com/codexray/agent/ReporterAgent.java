package com.codexray.agent;

import com.codexray.llm.LlmClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 报告 Agent：汇总所有分析结果，生成综合报告。
 */
@Service
public class ReporterAgent {

    private static final Logger log = LoggerFactory.getLogger(ReporterAgent.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ReporterAgent(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public String generateReport(IndexerAgent.ProjectProfile profile,
                                 List<AnalyzerAgent.ModuleAnalysis> modules,
                                 ScannerAgent.ScanResult scanResult) {
        StringBuilder context = new StringBuilder();

        // 基础信息
        context.append("## 项目概览\n");
        context.append("技术栈: ").append(profile.techStack()).append("\n");
        context.append("文件数: ").append(scanResult.fileCount()).append("\n");
        context.append("代码切片数: ").append(scanResult.chunkCount()).append("\n");
        context.append("顶层目录: ").append(String.join(", ", profile.topLevelDirs().keySet())).append("\n\n");

        // README 内容（如果有）
        if (profile.configContents() != null) {
            for (var entry : profile.configContents().entrySet()) {
                if (entry.getKey().toLowerCase().contains("readme")) {
                    context.append("## README\n");
                    String readme = entry.getValue();
                    if (readme.length() > 1000) readme = readme.substring(0, 1000) + "...";
                    context.append(readme).append("\n\n");
                    break;
                }
            }
        }

        // 配置文件列表
        if (!profile.configFiles().isEmpty()) {
            context.append("## 配置文件\n");
            context.append(String.join(", ", profile.configFiles())).append("\n\n");
        }

        // 核心文件（按代码量排序）
        if (scanResult.topFiles() != null && !scanResult.topFiles().isEmpty()) {
            context.append("## 核心文件（按代码量排序）\n");
            for (var f : scanResult.topFiles().stream().limit(15).toList()) {
                context.append(f.path()).append(" (").append(f.lineCount()).append(" 行, ").append(f.category()).append(")\n");
            }
            context.append("\n");
        }

        // 各模块详细分析（截断长摘要以控制 token）
        context.append("## 各模块分析\n");
        for (AnalyzerAgent.ModuleAnalysis m : modules) {
            context.append("### ").append(m.category()).append(" (")
                    .append(m.fileCount()).append(" files, ")
                    .append(m.chunkCount()).append(" chunks)\n");
            String summary = m.summary();
            if (summary.length() > 600) summary = summary.substring(0, 600) + "...";
            context.append(summary).append("\n\n");
        }

        // 代表性代码片段
        Map<String, List<String>> samples = scanResult.codeSamples();
        if (samples != null && !samples.isEmpty()) {
            context.append("## 代表性代码片段\n");
            int sampleCount = 0;
            for (Map.Entry<String, List<String>> entry : samples.entrySet()) {
                if (sampleCount >= 8) break;
                for (String sample : entry.getValue()) {
                    if (sampleCount >= 8) break;
                    String s = sample.length() > 500 ? sample.substring(0, 500) + "..." : sample;
                    context.append(s).append("\n\n");
                    sampleCount++;
                }
            }
        }

        try {
            String systemPrompt = """
                    你是一个专业的代码分析专家 CodeXray。根据提供的项目分析数据和代码片段，生成一份详细的分析报告。

                    重要要求：
                    1. 必须基于实际提供的代码内容进行分析，不要泛泛而谈
                    2. summary 要体现项目的具体功能和技术特点
                    3. architecture 要基于实际的目录结构和代码组织方式描述
                    4. modules 的 description 要体现每个模块的具体业务逻辑
                    5. strengths 和 improvements 要针对这个具体项目提出，不要模板化
                    6. securityRisks 要指出具体的安全隐患（如 SQL 注入风险、敏感信息暴露等）
                    7. performanceNotes 要指出性能相关注意事项
                    8. verdict 要给出有洞察力的评价
                    """;

            String prompt = "根据以下分析数据，生成一份详细的代码分析报告：\n\n"
                    + context + "\n\n"
                    + "请以 JSON 格式输出：\n"
                    + "{\n"
                    + "  \"summary\": \"基于实际代码的项目概述（体现具体功能）\",\n"
                    + "  \"primaryLanguage\": \"主要编程语言\",\n"
                    + "  \"techStack\": [\"从代码中识别出的具体技术\"],\n"
                    + "  \"architecture\": \"基于实际目录结构的架构描述\",\n"
                    + "  \"modules\": [{\"name\":\"模块名\",\"description\":\"基于实际代码的模块职责\"}],\n"
                    + "  \"score\": 85,\n"
                    + "  \"scoreDetails\": {\n"
                    + "    \"codeQuality\": 80,\n"
                    + "    \"structure\": 85,\n"
                    + "    \"documentation\": 70,\n"
                    + "    \"testing\": 60,\n"
                    + "    \"dependencies\": 90,\n"
                    + "    \"security\": 75,\n"
                    + "    \"performance\": 70,\n"
                    + "    \"maintainability\": 80\n"
                    + "  },\n"
                    + "  \"strengths\": [\"基于代码内容的具体优点\"],\n"
                    + "  \"improvements\": [\"基于代码内容的具体改进建议\"],\n"
                    + "  \"securityRisks\": [\"具体的安全风险点\"],\n"
                    + "  \"performanceNotes\": [\"具体的性能注意事项\"],\n"
                    + "  \"keyDependencies\": [\"核心依赖及版本\"],\n"
                    + "  \"verdict\": \"一句话总结评价\"\n"
                    + "}\n"
                    + "只输出 JSON，不要其他内容。";

            String response = llmClient.chat(systemPrompt, prompt);
            response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            return response;
        } catch (Exception e) {
            log.error("Report generation failed", e);
            return "{\"summary\":\"Analysis completed\",\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 基于分析报告生成推荐问题。
     */
    public List<String> generateQuestions(String reportJson) {
        try {
            String prompt = """
                    基于以下代码分析报告，生成 6 个推荐的代码问答问题。
                    问题应该针对这个具体项目，帮助用户深入了解代码。
                    输出 JSON 数组格式：["问题1", "问题2", ...]
                    只输出 JSON，不要其他内容。

                    分析报告：
                    """ + reportJson;

            String response = llmClient.chat("你是代码分析助手，根据分析报告生成推荐问题。只输出 JSON 数组。", prompt);
            response = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            return objectMapper.readValue(response, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to generate questions: {}", e.getMessage());
            return List.of(
                    "这个项目的核心架构是什么？",
                    "有哪些潜在的性能问题？",
                    "代码测试覆盖情况如何？",
                    "主要模块之间的依赖关系是什么？"
            );
        }
    }
}
