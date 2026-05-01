package com.codexray.llm;

import com.codexray.service.CodeReaderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

    private final WebClient webClient;
    private final CodeReaderService codeReaderService;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int maxTokens;

    private static final String SYSTEM_PROMPT = """
            你是一个专业的代码分析专家 CodeXray。根据提供的仓库代码内容，生成一份详细的分析报告。

            请严格以 JSON 格式输出，字段如下：
            {
              "summary": "项目一句话概述",
              "primaryLanguage": "主要编程语言",
              "techStack": ["技术栈列表"],
              "architecture": "架构模式描述（如 MVC、微服务、DDD 等）",
              "modules": [
                {"name": "模块名", "description": "模块职责"}
              ],
              "score": 85,
              "scoreDetails": {
                "codeQuality": 80,
                "structure": 85,
                "documentation": 70,
                "testing": 60,
                "dependencies": 90
              },
              "strengths": ["优点列表"],
              "improvements": ["改进建议列表"],
              "verdict": "一句话总结评价"
            }

            评分标准 (0-100)：
            - codeQuality: 代码规范、命名、复杂度
            - structure: 目录结构、模块划分、职责分离
            - documentation: README、注释、API 文档
            - testing: 测试覆盖、测试质量
            - dependencies: 依赖管理、版本锁定

            注意：
            1. 只输出 JSON，不要输出其他内容
            2. score 是综合评分（0-100）
            3. 根据实际代码内容分析，不要编造
            """;

    private static final String CHAT_SYSTEM_PROMPT = """
            你是一个专业的代码问答助手 CodeXray。根据提供的仓库代码内容，准确回答用户的问题。
            如果代码中没有相关信息，请明确说明。回答时请引用具体的文件路径和代码位置。
            """;

    public AnthropicLlmClient(
            @Value("${codexray.llm.base-url}") String baseUrl,
            @Value("${codexray.llm.api-key}") String apiKey,
            @Value("${codexray.llm.model}") String model,
            @Value("${codexray.llm.max-tokens:8192}") int maxTokens,
            CodeReaderService codeReaderService,
            ObjectMapper objectMapper) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.codeReaderService = codeReaderService;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("AnthropicLlmClient initialized: baseUrl={}, model={}, maxTokens={}", baseUrl, model, maxTokens);
    }

    @Override
    public String analyze(String repoPath) {
        log.info("Starting LLM analysis for repo: {}", repoPath);
        String codeContext = codeReaderService.readRepo(repoPath);
        log.debug("Code context size: {} chars", codeContext.length());

        String response = callMessagesApi(SYSTEM_PROMPT, codeContext);
        response = cleanJsonResponse(response);

        log.info("LLM analysis completed for repo: {}", repoPath);
        return response;
    }

    @Override
    public String chat(String repoPath, String question) {
        log.info("LLM chat for repo: {}, question length: {}", repoPath, question.length());
        String codeContext = codeReaderService.readRepo(repoPath);

        String userMessage = "以下是仓库的代码内容：\n\n" + codeContext
                + "\n\n用户问题：" + question;

        return callMessagesApi(CHAT_SYSTEM_PROMPT, userMessage);
    }

    @Override
    public String analyzeTrendingRepo(String repoName, String description, String lang) {
        boolean isZh = !"en".equals(lang);
        String systemPrompt = isZh
                ? """
                你是一个技术分析专家。请对以下 GitHub 热门项目进行简要分析，用中文回答。
                请按以下格式输出（每个部分一句话即可，简明扼要）：
                - 项目介绍：这个项目是什么，解决什么问题
                - 应用场景：适用于哪些场景，谁会用到它
                - 技术架构：核心技术栈和架构特点
                - 项目亮点：与其他同类项目相比的核心优势
                注意：每行以 "- " 开头，不要输出多余内容。"""
                : """
                You are a technology analyst. Provide a brief analysis of the following GitHub trending project in English.
                Use this format (one sentence per section, concise):
                - Introduction: What this project is and what problem it solves
                - Use Cases: Applicable scenarios and target users
                - Tech Architecture: Core tech stack and architecture highlights
                - Highlights: Key differentiators from similar projects
                Note: Each line starts with "- ". No extra content.""";

        String userContent = "项目名称: " + repoName + "\n项目描述: " + (description != null ? description : "No description");

        try {
            return callMessagesApi(systemPrompt, userContent);
        } catch (Exception e) {
            log.warn("Failed to analyze trending repo {}: {}", repoName, e.getMessage());
            return isZh ? "暂无分析" : "No analysis available";
        }
    }

    /**
     * 调用 Anthropic Messages API。
     * 小米端点格式: POST {baseUrl}/v1/messages
     */
    private String callMessagesApi(String systemPrompt, String userContent) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "system", systemPrompt,
                "messages", List.of(
                        Map.of("role", "user", "content", userContent)
                )
        );

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String responseBody = webClient.post()
                        .uri("/v1/messages")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(180))
                        .block();

                return extractContent(responseBody);
            } catch (Exception e) {
                log.warn("LLM API call attempt {}/3 failed: {}", attempt, e.getMessage());
                if (attempt == 3) {
                    log.error("LLM API call failed after 3 attempts", e);
                    throw new RuntimeException("LLM API call failed: " + e.getMessage(), e);
                }
                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
        throw new RuntimeException("Unreachable");
    }

    /**
     * 解析 Anthropic Messages API 响应。
     * 响应格式: { "content": [{"type":"text","text":"..."}], ... }
     */
    private String extractContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new RuntimeException("LLM API returned empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 检查错误
            JsonNode error = root.get("error");
            if (error != null) {
                String errorMsg = error.has("message") ? error.get("message").asText() : error.toString();
                throw new RuntimeException("LLM API error: " + errorMsg);
            }

            // Anthropic 格式: content[0].text
            JsonNode content = root.get("content");
            if (content != null && content.isArray() && !content.isEmpty()) {
                JsonNode firstBlock = content.get(0);
                JsonNode text = firstBlock.get("text");
                if (text != null && !text.isNull()) {
                    return text.asText();
                }
            }

            // 兼容 OpenAI 格式: choices[0].message.content
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).get("message");
                if (message != null) {
                    JsonNode msgContent = message.get("content");
                    if (msgContent != null && !msgContent.isNull()) {
                        return msgContent.asText();
                    }
                }
            }

            log.error("Unexpected LLM response structure: {}", responseBody.substring(0, Math.min(500, responseBody.length())));
            throw new RuntimeException("LLM API returned unexpected structure");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse LLM response: " + e.getMessage(), e);
        }
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        String trimmed = response.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
