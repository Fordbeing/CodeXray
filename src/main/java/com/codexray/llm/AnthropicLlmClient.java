package com.codexray.llm;

import com.codexray.service.CodeReaderService;
import com.codexray.service.SettingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

    private final CodeReaderService codeReaderService;
    private final SettingService settingService;
    private final ObjectMapper objectMapper;

    // 默认值来自 application.yml（仅 max_tokens 有默认值，其余必须在设置页配置）
    private final int defaultMaxTokens;

    // 缓存 WebClient，设置变更时重建
    private WebClient cachedWebClient;
    private String cachedBaseUrl;
    private String cachedApiKey;

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
            @Value("${codexray.llm.max-tokens:8192}") int maxTokens,
            CodeReaderService codeReaderService,
            SettingService settingService,
            ObjectMapper objectMapper) {
        this.defaultMaxTokens = maxTokens;
        this.codeReaderService = codeReaderService;
        this.settingService = settingService;
        this.objectMapper = objectMapper;
        log.info("AnthropicLlmClient initialized (settings-driven, maxTokens={})", maxTokens);
    }

    /** 检查 AI 设置是否已配置 */
    private void checkConfigured() {
        String baseUrl = settingService.get("ai_base_url");
        String apiKey = settingService.get("ai_api_key");
        String model = settingService.get("ai_model");
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) {
            throw new RuntimeException("AI 模型未配置，请先在「系统设置」页面填写 API Key、Base URL 和模型名称");
        }
    }

    private String getBaseUrl() {
        return settingService.get("ai_base_url");
    }

    private String getApiKey() {
        return settingService.get("ai_api_key");
    }

    private String getModel() {
        return settingService.get("ai_model");
    }

    private int getMaxTokens() {
        String val = settingService.get("ai_max_tokens");
        if (val != null && !val.isBlank()) {
            try { return Integer.parseInt(val); } catch (NumberFormatException ignored) {}
        }
        return defaultMaxTokens;
    }

    /** 获取或重建 WebClient（配置变更时自动重建） */
    private synchronized WebClient getWebClient() {
        String baseUrl = getBaseUrl();
        String apiKey = getApiKey();
        if (cachedWebClient == null || !baseUrl.equals(cachedBaseUrl) || !apiKey.equals(cachedApiKey)) {
            cachedWebClient = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("x-api-key", apiKey)
                    .defaultHeader("anthropic-version", "2023-06-01")
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build();
            cachedBaseUrl = baseUrl;
            cachedApiKey = apiKey;
            log.info("WebClient rebuilt: baseUrl={}, model={}", baseUrl, getModel());
        }
        return cachedWebClient;
    }

    @Override
    public String analyze(String repoPath) {
        checkConfigured();
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
        checkConfigured();
        log.info("LLM chat for repo: {}, question length: {}", repoPath, question.length());
        String codeContext = codeReaderService.readRepo(repoPath);

        String userMessage = "以下是仓库的代码内容：\n\n" + codeContext
                + "\n\n用户问题：" + question;

        return callMessagesApi(CHAT_SYSTEM_PROMPT, userMessage);
    }

    @Override
    public String chatWithContext(String systemPrompt, List<Map<String, String>> history, String question) {
        checkConfigured();
        log.info("LLM chatWithContext, history turns: {}, question length: {}", history.size(), question.length());

        List<Map<String, Object>> messages = buildMessages(history, question);
        return callMessagesApiMultiTurn(systemPrompt, messages);
    }

    @Override
    public void chatWithContextStreaming(String systemPrompt, List<Map<String, String>> history,
                                         String question, Consumer<String> onToken) {
        checkConfigured();
        log.info("LLM chatWithContextStreaming, history turns: {}, question length: {}", history.size(), question.length());

        List<Map<String, Object>> messages = buildMessages(history, question);
        callMessagesApiStream(systemPrompt, messages, onToken);
    }

    @Override
    public String analyzeTrendingRepo(String repoName, String description, String lang) {
        checkConfigured();
        boolean isZh = !"en".equals(lang);
        String systemPrompt = isZh
                ? """
                你是一个资深技术分析专家，擅长快速评估开源项目的价值和适用性。
                请对以下 GitHub 热门项目进行分析，帮助开发者在 30 秒内判断这个项目是否值得关注。

                使用 Markdown 格式输出，每个标题用 ###，每部分 2-3 句话，信息密度高：

                ### 项目定位
                （这个项目是什么？一句话核心定位 + 它要解决的核心痛点）

                ### 技术架构
                （核心技术栈是什么？架构设计有何特点？用了哪些关键技术？）

                ### 适用场景
                （谁会用到它？适合什么场景？能替代什么现有方案？）

                ### 核心亮点
                （与其他方案相比，它的 2-3 个最突出优势是什么？）

                ### 上手难度
                （学习曲线如何？有没有快速入门路径？）

                注意：严格按照以上 5 个标题输出，不要输出多余的解释或寒暄。"""
                : """
                You are a senior technology analyst skilled at quickly evaluating open-source project value and applicability.
                Analyze the following GitHub trending project to help developers decide within 30 seconds whether it's worth attention.

                Use Markdown format with ### headings, 2-3 sentences per section, high information density:

                ### Positioning
                (What is this project? One-sentence core positioning + the core pain point it solves.)

                ### Architecture
                (What is the core tech stack? What's notable about the architecture? Key technologies used?)

                ### Use Cases
                (Who uses it? What scenarios is it suited for? What existing solutions can it replace?)

                ### Highlights
                (What are 2-3 most prominent advantages over alternatives?)

                ### Learning Curve
                (How steep is the learning curve? Quick start path available?)

                Note: Output exactly with the 5 section titles above. No extra explanation.""";

        String userContent = "项目名称: " + repoName + "\n项目描述: " + (description != null ? description : "No description");

        try {
            return callMessagesApi(systemPrompt, userContent);
        } catch (Exception e) {
            log.warn("Failed to analyze trending repo {}: {}", repoName, e.getMessage());
            return isZh ? "暂无分析" : "No analysis available";
        }
    }

    @Override
    public String testConnection() {
        checkConfigured();
        log.info("Testing AI connection: baseUrl={}, model={}", getBaseUrl(), getModel());
        String response = callMessagesApi("You are a helpful assistant. Reply with only 'OK'.", "Say OK");
        log.info("AI connection test succeeded, response: {}", response);
        return response;
    }

    private List<Map<String, Object>> buildMessages(List<Map<String, String>> history, String question) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Map<String, String> h : history) {
            String role = h.get("role");
            // Anthropic API 只允许 user/assistant 角色在 messages 中
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            messages.add(Map.of("role", role, "content", h.get("content")));
        }
        messages.add(Map.of("role", "user", "content", question));
        return messages;
    }

    private String callMessagesApi(String systemPrompt, String userContent) {
        return callMessagesApiMultiTurn(systemPrompt,
                List.of(Map.of("role", "user", "content", userContent)));
    }

    private String callMessagesApiMultiTurn(String systemPrompt, List<Map<String, Object>> messages) {
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("model", getModel());
        requestBody.put("max_tokens", getMaxTokens());
        requestBody.put("system", systemPrompt);
        requestBody.put("messages", messages);

        WebClient wc = getWebClient();

        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                String responseBody = wc.post()
                        .uri("/v1/messages")
                        .bodyValue(requestBody)
                        .exchangeToMono(response -> {
                            if (response.statusCode().isError()) {
                                return response.bodyToMono(String.class)
                                        .defaultIfEmpty("")
                                        .flatMap(body -> {
                                            log.error("Anthropic API error {} — body: {}", response.statusCode().value(),
                                                    body.substring(0, Math.min(1000, body.length())));
                                            return reactor.core.publisher.Mono.error(
                                                    new RateLimitException("Anthropic API " + response.statusCode().value() + ": " + body,
                                                            response.statusCode().value()));
                                        });
                            }
                            return response.bodyToMono(String.class);
                        })
                        .timeout(Duration.ofSeconds(120))
                        .block();

                return extractContent(responseBody);
            } catch (Exception e) {
                int statusCode = extractStatusCode(e);
                boolean isRateLimit = statusCode == 429;
                log.warn("LLM API call attempt {}/5 failed ({}): {}", attempt,
                        isRateLimit ? "rate-limited" : "error", e.getMessage());
                if (attempt == 5) {
                    log.error("LLM API call failed after 5 attempts", e);
                    throw new RuntimeException("LLM API call failed: " + e.getMessage(), e);
                }
                long delayMs = isRateLimit
                        ? Math.min((long) (1000 * Math.pow(2, attempt - 1)) + (long) (Math.random() * 500), 30000)
                        : 500L * attempt;
                log.info("Retrying in {}ms...", delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
        throw new RuntimeException("Unreachable");
    }

    private void callMessagesApiStream(String systemPrompt, List<Map<String, Object>> messages,
                                        Consumer<String> onToken) {
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("model", getModel());
        requestBody.put("max_tokens", getMaxTokens());
        requestBody.put("system", systemPrompt);
        requestBody.put("messages", messages);
        requestBody.put("stream", true);

        WebClient wc = getWebClient();

        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.atomic.AtomicReference<RuntimeException> streamError = new java.util.concurrent.atomic.AtomicReference<>();
                StringBuilder fullContent = new StringBuilder();

                wc.post()
                        .uri("/v1/messages")
                        .bodyValue(requestBody)
                        .exchangeToFlux(response -> {
                            if (response.statusCode().isError()) {
                                return response.bodyToMono(String.class)
                                        .defaultIfEmpty("")
                                        .flatMapMany(body -> {
                                            log.error("Anthropic API streaming error {} — body: {}",
                                                    response.statusCode().value(),
                                                    body.substring(0, Math.min(1000, body.length())));
                                            return reactor.core.publisher.Flux.error(
                                                    new RateLimitException("Anthropic API " + response.statusCode().value() + ": " + body,
                                                            response.statusCode().value()));
                                        });
                            }
                            return response.bodyToFlux(String.class);
                        })
                        .timeout(Duration.ofSeconds(180))
                        .subscribe(
                                data -> {
                                    try {
                                        if (data == null || data.isBlank()) return;
                                        String json = data.startsWith("data: ") ? data.substring(6) : data;
                                        if ("[DONE]".equals(json.trim())) return;

                                        JsonNode node = objectMapper.readTree(json);
                                        String type = node.has("type") ? node.get("type").asText() : "";

                                        if ("content_block_delta".equals(type)) {
                                            JsonNode delta = node.get("delta");
                                            if (delta != null) {
                                                String text = delta.has("text") ? delta.get("text").asText() : "";
                                                if (!text.isEmpty()) {
                                                    fullContent.append(text);
                                                    onToken.accept(text);
                                                }
                                            }
                                        } else if ("message_delta".equals(type)) {
                                            JsonNode delta = node.get("delta");
                                            if (delta != null && delta.has("text")) {
                                                String text = delta.get("text").asText();
                                                if (!text.isEmpty()) {
                                                    fullContent.append(text);
                                                    onToken.accept(text);
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.debug("Failed to parse SSE event: {}", e.getMessage());
                                    }
                                },
                                error -> {
                                    streamError.set(new RuntimeException("Stream error", error));
                                    latch.countDown();
                                },
                                latch::countDown
                        );

                if (!latch.await(240, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new RuntimeException("Streaming timed out");
                }
                if (streamError.get() != null) {
                    throw streamError.get();
                }

                if (fullContent.isEmpty()) {
                    throw new RuntimeException("Streaming returned no content");
                }
                return;
            } catch (Exception e) {
                int statusCode = extractStatusCode(e);
                boolean isRateLimit = statusCode == 429;
                log.warn("LLM stream call attempt {}/5 failed ({}): {}", attempt,
                        isRateLimit ? "rate-limited" : "error", e.getMessage());
                if (attempt == 5) {
                    log.error("LLM stream call failed after 5 attempts", e);
                    throw new RuntimeException("LLM stream call failed: " + e.getMessage(), e);
                }
                long delayMs = isRateLimit
                        ? Math.min((long) (1000 * Math.pow(2, attempt - 1)) + (long) (Math.random() * 500), 30000)
                        : 500L * attempt;
                log.info("Retrying in {}ms...", delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry", ie);
                }
            }
        }
    }

    private String extractContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new RuntimeException("LLM API returned empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            JsonNode error = root.get("error");
            if (error != null) {
                String errorMsg = error.has("message") ? error.get("message").asText() : error.toString();
                throw new RuntimeException("LLM API error: " + errorMsg);
            }

            JsonNode content = root.get("content");
            if (content != null && content.isArray() && !content.isEmpty()) {
                JsonNode firstBlock = content.get(0);
                JsonNode text = firstBlock.get("text");
                if (text != null && !text.isNull()) {
                    return text.asText();
                }
            }

            // 兼容 OpenAI 格式
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

    private int extractStatusCode(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof RateLimitException rle) {
                return rle.statusCode();
            }
            // 从异常消息中提取 "Anthropic API 429:" 格式
            String msg = t.getMessage();
            if (msg != null && msg.startsWith("Anthropic API ")) {
                try {
                    return Integer.parseInt(msg.substring(14, msg.indexOf(':')));
                } catch (NumberFormatException ignored) {}
            }
            t = t.getCause();
        }
        return -1;
    }

    private static class RateLimitException extends RuntimeException {
        private final int statusCode;

        RateLimitException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        int statusCode() { return statusCode; }
    }
}
