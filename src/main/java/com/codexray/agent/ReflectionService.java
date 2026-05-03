package com.codexray.agent;

import com.codexray.llm.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 回答质量反思服务 — 用 LLM 审查回答质量，低质量时返回改进建议。
 */
@Service
public class ReflectionService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ReflectionService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public record ReflectionResult(int qualityScore, String critique, boolean needsRetry) {}

    /**
     * 审查回答质量。返回评分和改进建议。
     */
    public ReflectionResult reflect(String question, String answer, String context) {
        String systemPrompt = """
                你是一个代码问答质量审查员。评估以下回答的质量。

                评估维度:
                1. 准确性 — 回答是否基于代码事实
                2. 完整性 — 是否充分回答了问题
                3. 相关性 — 是否引用了正确的代码位置

                返回 JSON 格式:
                {"score": <1-10>, "critique": "<改进意见>", "needsRetry": <true/false>}

                如果评分 >= 7，needsRetry 设为 false。
                只返回 JSON，不要包含其他内容。
                """;

        String userMessage = "问题: " + question + "\n\n回答: " + answer
                + (context != null && !context.isBlank() ? "\n\n检索上下文: " + context.substring(0, Math.min(context.length(), 500)) : "");

        try {
            String response = llmClient.chatWithContext(systemPrompt, List.of(), userMessage);
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            }

            JsonNode root = objectMapper.readTree(json);
            int score = root.path("score").asInt(8);
            String critique = root.path("critique").asText("");
            boolean needsRetry = root.path("needsRetry").asBoolean(score < 7);

            return new ReflectionResult(score, critique, needsRetry);
        } catch (Exception e) {
            log.debug("Reflection failed: {}", e.getMessage());
            return new ReflectionResult(8, "", false); // 默认通过
        }
    }
}
