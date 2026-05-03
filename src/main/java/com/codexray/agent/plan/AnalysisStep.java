package com.codexray.agent.plan;

import java.util.Map;

/**
 * 分析计划中的单个步骤。
 */
public record AnalysisStep(
        String agentName,
        String description,
        Map<String, Object> params,
        int order
) {}
