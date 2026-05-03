package com.codexray.agent;

import com.codexray.agent.plan.AnalysisStep;
import com.codexray.agent.plan.AnalysisTaskPlan;
import com.codexray.agent.plan.AgentRegistry;
import com.codexray.service.AnalysisEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Agent 执行器 — 按计划步骤调度 Agent 执行，统一错误处理和进度发布。
 */
@Component
public class AgentExecutor {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutor.class);

    private final AgentRegistry registry;
    private final AnalysisEventService eventService;

    public AgentExecutor(AgentRegistry registry, AnalysisEventService eventService) {
        this.registry = registry;
        this.eventService = eventService;
    }

    /**
     * 按计划执行分析步骤。
     * 返回各步骤的结果列表（按顺序）。
     */
    public List<Object> execute(AnalysisTaskPlan plan, String taskId, String repoPath,
                                 ExecutorService executor, ScannerAgent.ScanResult scanResult) {
        List<Object> results = new ArrayList<>();
        List<AnalysisStep> sortedSteps = plan.steps().stream()
                .sorted((a, b) -> Integer.compare(a.order(), b.order()))
                .toList();

        int totalSteps = sortedSteps.size();
        for (int i = 0; i < totalSteps; i++) {
            AnalysisStep step = sortedSteps.get(i);
            String agentName = step.agentName();
            publishEvent(taskId, "message", "执行步骤 " + (i + 1) + "/" + totalSteps + ": " + step.description());

            try {
                Object result = executeStep(step, taskId, repoPath, executor, scanResult);
                results.add(result);
                log.info("Step {}/{} completed: {} → {}", i + 1, totalSteps, agentName,
                        result != null ? result.getClass().getSimpleName() : "null");
            } catch (Exception e) {
                log.error("Step {}/{} failed: {}: {}", i + 1, totalSteps, agentName, e.getMessage(), e);
                publishEvent(taskId, "error", "步骤 " + step.description() + " 失败: " + e.getMessage());
                results.add(null);
            }
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    private Object executeStep(AnalysisStep step, String taskId, String repoPath,
                                ExecutorService executor, ScannerAgent.ScanResult scanResult) throws Exception {
        Object agent = registry.getAgent(step.agentName());
        if (agent == null) {
            throw new IllegalArgumentException("Unknown agent: " + step.agentName());
        }

        Map<String, Object> params = step.params();

        return switch (step.agentName()) {
            case "scanner" -> ((ScannerAgent) agent).scan(taskId, repoPath);
            case "indexer" -> ((IndexerAgent) agent).index(repoPath);
            case "analyzer" -> {
                List<String> categories = (List<String>) params.get("categories");
                if (categories != null && !categories.isEmpty()) {
                    yield ((AnalyzerAgent) agent).analyzeParallel(taskId, executor, categories);
                }
                yield ((AnalyzerAgent) agent).analyzeParallel(taskId, executor);
            }
            case "reporter" -> {
                // reporter 需要 profile, modules, scanResult — 从上下文获取
                // 这里返回 null，由调用方处理（因为需要多步骤结果聚合）
                yield null;
            }
            default -> throw new IllegalArgumentException("Unsupported agent: " + step.agentName());
        };
    }

    private void publishEvent(String taskId, String event, Object data) {
        try {
            eventService.publish(taskId, event, data);
        } catch (Exception ignored) {}
    }
}
