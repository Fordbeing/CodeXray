package com.codexray.agent;

import com.codexray.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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

        // 基础信息
        context.append("## 项目概览\n");
        context.append("技术栈: ").append(profile.techStack()).append("\n");
        context.append("文件数: ").append(scanResult.fileCount()).append("\n");
        context.append("代码切片数: ").append(scanResult.chunkCount()).append("\n");
        context.append("顶层目录: ").append(String.join(", ", profile.topLevelDirs().keySet())).append("\n\n");

        // 配置文件内容
        if (!profile.configFiles().isEmpty()) {
            context.append("## 配置文件\n");
            context.append(String.join(", ", profile.configFiles())).append("\n\n");
        }

        // 各模块详细分析
        context.append("## 各模块分析\n");
        for (AnalyzerAgent.ModuleAnalysis m : modules) {
            context.append("### ").append(m.category()).append(" (")
                    .append(m.fileCount()).append(" files, ")
                    .append(m.chunkCount()).append(" chunks)\n");
            context.append(m.summary()).append("\n\n");
        }

        // 代表性代码片段（关键！让 LLM 看到实际代码）
        Map<String, List<String>> samples = scanResult.codeSamples();
        if (samples != null && !samples.isEmpty()) {
            context.append("## 代表性代码片段\n");
            int sampleCount = 0;
            for (Map.Entry<String, List<String>> entry : samples.entrySet()) {
                if (sampleCount >= 8) break; // 最多展示 8 个样本
                for (String sample : entry.getValue()) {
                    if (sampleCount >= 8) break;
                    context.append(sample).append("\n\n");
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
                    6. verdict 要给出有洞察力的评价
                    7. primaryLanguage 要准确反映主要编程语言
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
                    + "    \"dependencies\": 90\n"
                    + "  },\n"
                    + "  \"strengths\": [\"基于代码内容的具体优点\"],\n"
                    + "  \"improvements\": [\"基于代码内容的具体改进建议\"],\n"
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
}
