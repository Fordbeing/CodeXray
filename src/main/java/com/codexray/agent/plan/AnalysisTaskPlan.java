package com.codexray.agent.plan;

import java.util.List;

/**
 * LLM 生成的分析执行计划。
 */
public record AnalysisTaskPlan(
        List<AnalysisStep> steps,
        String rationale
) {}
