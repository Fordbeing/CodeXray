package com.codexray.agent;

import com.codexray.agent.tools.ChatTool;
import com.codexray.llm.LlmClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Consumer;

/**
 * Tool-Using Chat Agent — ReAct（Reason→Act→Observe）循环。
 * Agent 自主决定何时调用工具、调用哪个工具，直到能回答用户问题。
 */
@Service
public class ToolChatAgent {

    private static final Logger log = LoggerFactory.getLogger(ToolChatAgent.class);
    private static final int MAX_ITERATIONS = 4;

    private final LlmClient llmClient;
    private final List<ChatTool> tools;
    private final ObjectMapper objectMapper;
    private final ReflectionService reflectionService;

    public ToolChatAgent(LlmClient llmClient, List<ChatTool> tools, ObjectMapper objectMapper,
                         ReflectionService reflectionService) {
        this.llmClient = llmClient;
        this.tools = tools;
        this.objectMapper = objectMapper;
        this.reflectionService = reflectionService;
    }

    /**
     * ReAct 循环回答。
     * @param taskId 当前分析任务 ID
     * @param question 用户问题
     * @param history 对话历史
     * @param onStep 每步推理过程的回调（用于 SSE 推送）
     * @return 最终回答
     */
    public String react(String taskId, String question, List<Map<String, String>> history,
                         Consumer<String> onStep) {
        String toolDescriptions = buildToolDescriptions(taskId);

        String systemPrompt = """
                你是一个代码分析 AI 助手，拥有使用工具的能力。
                你可以使用以下工具来查找信息:

                %s

                使用格式:
                THOUGHT: <你的推理过程>
                ACTION: {"tool": "<工具名>", "args": {<参数>}}

                或者当你准备好回答时:
                THOUGHT: <推理>
                FINAL_ANSWER: <你的最终回答>

                重要:
                1. 每次只调用一个工具
                2. 等待工具返回结果后再继续推理
                3. 如果工具返回的信息足够回答问题，直接给出 FINAL_ANSWER
                4. 最多使用 %d 次工具调用
                5. 回答默认使用中文
                """.formatted(toolDescriptions, MAX_ITERATIONS);

        // 构建对话历史（最近 5 条）
        List<Map<String, String>> recentHistory = history.size() > 10
                ? history.subList(history.size() - 10, history.size()) : history;

        StringBuilder conversationLog = new StringBuilder();

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            // 构建当前上下文
            String userMessage = conversationLog.isEmpty() ? question
                    : "继续分析。之前的工具调用结果:\n" + conversationLog + "\n\n原始问题: " + question;

            String response;
            try {
                response = llmClient.chatWithContext(systemPrompt, recentHistory, userMessage);
            } catch (Exception e) {
                log.error("ReAct LLM call failed at iteration {}: {}", iteration, e.getMessage());
                break;
            }

            // 解析 THOUGHT 和 ACTION/FINAL_ANSWER
            String thought = extractSection(response, "THOUGHT:");
            if (thought != null && onStep != null) {
                onStep.accept("思考: " + thought);
            }

            // 检查是否为最终回答
            String finalAnswer = extractSection(response, "FINAL_ANSWER:");
            if (finalAnswer != null && !finalAnswer.isBlank()) {
                return reflectAndEnhance(taskId, question, finalAnswer, conversationLog, systemPrompt, recentHistory, onStep);
            }

            // 解析工具调用
            String actionStr = extractSection(response, "ACTION:");
            if (actionStr == null || actionStr.isBlank()) {
                // LLM 没有给出有效的 action 或 final answer，把整个回复作为回答
                return reflectAndEnhance(taskId, question, response, conversationLog, systemPrompt, recentHistory, onStep);
            }

            // 执行工具调用
            try {
                Map<String, Object> action = objectMapper.readValue(actionStr.trim(), Map.class);
                String toolName = (String) action.get("tool");
                @SuppressWarnings("unchecked")
                Map<String, String> args = (Map<String, String>) action.get("args");

                if (args == null) args = new HashMap<>();
                args.putIfAbsent("taskId", taskId);

                ChatTool tool = findTool(toolName);
                if (tool == null) {
                    conversationLog.append("工具 ").append(toolName).append(" 不存在。\n");
                    continue;
                }

                if (onStep != null) {
                    onStep.accept("调用工具: " + toolName + "(" + args + ")");
                }

                String toolResult = tool.execute(args);
                conversationLog.append("工具 ").append(toolName).append(" 返回:\n")
                        .append(toolResult).append("\n\n");

                if (onStep != null) {
                    String preview = toolResult.length() > 200 ? toolResult.substring(0, 200) + "..." : toolResult;
                    onStep.accept("工具结果: " + preview);
                }

            } catch (JsonProcessingException e) {
                log.warn("Failed to parse action at iteration {}: {}", iteration, actionStr);
                conversationLog.append("工具调用格式错误，请重新尝试。\n");
            } catch (Exception e) {
                log.warn("Tool execution failed at iteration {}: {}", iteration, e.getMessage());
                conversationLog.append("工具执行出错: ").append(e.getMessage()).append("\n");
            }
        }

        // 达到最大迭代次数，用 LLM 基于已有信息生成回答
        String lastMessage = "基于以下工具调用结果回答用户问题:\n" + conversationLog + "\n问题: " + question;
        String answer = llmClient.chatWithContext(systemPrompt, recentHistory, lastMessage);
        return reflectAndEnhance(taskId, question, answer, conversationLog, systemPrompt, recentHistory, onStep);
    }

    /**
     * Reflection 审查：低质量时追加一轮推理迭代。
     * 跳过短答案（<100字）和长答案（>1500字）的审查以降低延迟。
     */
    private String reflectAndEnhance(String taskId, String question, String answer,
                                      StringBuilder conversationLog, String systemPrompt,
                                      List<Map<String, String>> recentHistory, Consumer<String> onStep) {
        if (answer.length() < 100 || answer.length() > 1500) {
            return answer;
        }
        try {
            ReflectionService.ReflectionResult result = reflectionService.reflect(question, answer, null);
            if (!result.needsRetry()) {
                return answer;
            }
            log.info("ReAct Reflection: score={}, adding retry iteration with critique", result.qualityScore());
            if (onStep != null) {
                onStep.accept("反思: 质量评分 " + result.qualityScore() + "，改进: " + result.critique());
            }
            // 追加一轮推理，将 critique 注入上下文
            conversationLog.append("【质量审查反馈】评分: ").append(result.qualityScore())
                    .append("，改进建议: ").append(result.critique()).append("\n\n");
            String retryMessage = "根据质量审查反馈改进你的回答。工具调用结果:\n" + conversationLog + "\n\n原始问题: " + question;
            String improved = llmClient.chatWithContext(systemPrompt, recentHistory, retryMessage);
            return improved;
        } catch (Exception e) {
            log.debug("ReAct reflection skipped: {}", e.getMessage());
            return answer;
        }
    }

    private String buildToolDescriptions(String taskId) {
        StringBuilder sb = new StringBuilder();
        for (ChatTool tool : tools) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description())
              .append("\n  参数格式: ").append(tool.paramSchema()).append("\n");
        }
        return sb.toString();
    }

    private ChatTool findTool(String name) {
        return tools.stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
    }

    private String extractSection(String text, String marker) {
        int idx = text.indexOf(marker);
        if (idx < 0) return null;
        String after = text.substring(idx + marker.length()).trim();
        // 取到下一个 THOUGHT/ACTION/FINAL_ANSWER 或末尾
        int end = after.length();
        for (String next : new String[]{"THOUGHT:", "ACTION:", "FINAL_ANSWER:"}) {
            if (next.equals(marker)) continue;
            int nextIdx = after.indexOf(next);
            if (nextIdx >= 0 && nextIdx < end) end = nextIdx;
        }
        return after.substring(0, end).trim();
    }
}
