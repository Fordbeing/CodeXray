package com.codexray.agent;

import com.codexray.agent.plan.AnalysisStep;
import com.codexray.agent.plan.AnalysisTaskPlan;
import com.codexray.agent.plan.AgentRegistry;
import com.codexray.llm.LlmClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PlannerAgent — 根据仓库特征用 LLM 生成动态分析执行计划。
 * LLM 失败时 fallback 到默认 4 阶段管道。
 */
@Service
public class PlannerAgent {

    private static final Logger log = LoggerFactory.getLogger(PlannerAgent.class);

    private final LlmClient llmClient;
    private final AgentRegistry registry;
    private final ObjectMapper objectMapper;

    public PlannerAgent(LlmClient llmClient, AgentRegistry registry, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据仓库特征生成分析执行计划。
     */
    public AnalysisTaskPlan createPlan(IndexerAgent.ProjectProfile profile) {
        String systemPrompt = """
                你是一个代码分析流水线规划器。根据项目的特征，决定分析步骤的执行顺序和参数。

                可用 Agent:
                %s

                项目信息:
                - 技术栈: %s
                - 顶层目录: %s
                - 配置文件: %s

                请返回 JSON 格式的执行计划，包含以下字段:
                - steps: 步骤数组，每步有 agentName, description, params, order
                - rationale: 规划理由

                规则:
                1. scanner 必须最先执行（产生 ES 索引数据）
                2. analyzer 依赖 scanner 的数据
                3. reporter 必须最后执行
                4. indexer 已在规划前完成，不需要重复
                5. 如果项目很小（文件少），可以减少 analyzer 的类别
                6. analyzer 的 params.categories 可以指定分析哪些类别

                返回纯 JSON，不要包含 markdown 代码块。
                """.formatted(
                        registry.getAgentDescription(),
                        profile.techStack() != null ? profile.techStack() : "未知",
                        profile.topLevelDirs() != null ? String.join(", ", profile.topLevelDirs().keySet()) : "无",
                        profile.configFiles() != null ? String.join(", ", profile.configFiles()) : "无"
                );

        try {
            String response = llmClient.chatWithContext(systemPrompt, List.of(),
                    "请根据项目特征生成分析执行计划。");

            // 清理可能的 markdown 代码块
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            }

            return parsePlan(json);
        } catch (Exception e) {
            log.warn("PlannerAgent failed, using default plan: {}", e.getMessage());
            return defaultPlan();
        }
    }

    private AnalysisTaskPlan parsePlan(String json) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(json);

        String rationale = root.path("rationale").asText("LLM 生成的执行计划");
        JsonNode stepsNode = root.path("steps");

        List<AnalysisStep> steps = new ArrayList<>();
        if (stepsNode.isArray()) {
            for (JsonNode stepNode : stepsNode) {
                String agentName = stepNode.path("agentName").asText();
                if (!registry.exists(agentName)) {
                    log.warn("Unknown agent in plan: {}, skipping", agentName);
                    continue;
                }
                String desc = stepNode.path("description").asText("");
                int order = stepNode.path("order").asInt(steps.size() + 1);

                @SuppressWarnings("unchecked")
                Map<String, Object> params = objectMapper.convertValue(stepNode.path("params"), Map.class);
                if (params == null) params = Map.of();

                steps.add(new AnalysisStep(agentName, desc, params, order));
            }
        }

        if (steps.isEmpty()) {
            return defaultPlan();
        }

        return new AnalysisTaskPlan(steps, rationale);
    }

    private AnalysisTaskPlan defaultPlan() {
        return new AnalysisTaskPlan(
                List.of(
                        new AnalysisStep("scanner", "扫描并索引代码", Map.of(), 1),
                        new AnalysisStep("analyzer", "分析代码模块", Map.of(), 2),
                        new AnalysisStep("reporter", "生成分析报告", Map.of(), 3)
                ),
                "默认管道（LLM 规划失败）"
        );
    }
}
