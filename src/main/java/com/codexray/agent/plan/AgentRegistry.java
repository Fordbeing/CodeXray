package com.codexray.agent.plan;

import com.codexray.agent.*;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 注册表 — 将 agent 名称映射到 Spring Bean 实例。
 */
@Component
public class AgentRegistry {

    private final Map<String, Object> agents;

    public AgentRegistry(ScannerAgent scanner, IndexerAgent indexer,
                         AnalyzerAgent analyzer, ReporterAgent reporter) {
        this.agents = Map.of(
                "scanner", scanner,
                "indexer", indexer,
                "analyzer", analyzer,
                "reporter", reporter
        );
    }

    public Object getAgent(String name) {
        return agents.get(name);
    }

    public boolean exists(String name) {
        return agents.containsKey(name);
    }

    public String getAgentDescription() {
        return """
                - scanner: 扫描仓库代码，切片并生成 Embedding 向量，存储到 ES
                - indexer: 识别项目技术栈、配置文件和目录结构
                - analyzer: 按模块类别（controller/service/model/config/data/util/source）并行分析代码
                - reporter: 综合所有分析结果生成最终报告（JSON 格式）
                """;
    }
}
