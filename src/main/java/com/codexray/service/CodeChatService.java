package com.codexray.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codexray.agent.ReflectionService;
import com.codexray.agent.ToolChatAgent;
import com.codexray.common.TokenEstimator;
import com.codexray.llm.LlmClient;
import com.codexray.mapper.ChatHistoryMapper;
import com.codexray.model.dto.ChatMessage;
import com.codexray.model.dto.ChatPending;
import com.codexray.model.entity.ChatHistory;
import com.codexray.rag.EmbeddingService;
import com.codexray.rag.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 代码问答服务 — 支持多轮对话，基于 RAG 检索增强生成，流式输出。
 * 对话历史持久化到 MySQL，重启后可恢复。
 */
@Service
public class CodeChatService {

    private static final Logger log = LoggerFactory.getLogger(CodeChatService.class);

    private final VectorStoreService vectorStore;
    private final EmbeddingService embeddingService;
    private final LlmClient llmClient;
    private final ChatHistoryMapper chatHistoryMapper;
    private final RedisSessionStore redisSessionStore;
    private final ToolChatAgent toolChatAgent;
    private final ReflectionService reflectionService;
    private final SettingService settingService;
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // 会话历史：sessionId → List<{role, content}>（内存热缓存）
    private final Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();
    // 会话元信息
    private final Map<String, SessionInfo> sessionMeta = new ConcurrentHashMap<>();
    // 异步请求结果：pollId → ChatPending
    private final Map<String, ChatPending> pendingResults = new ConcurrentHashMap<>();

    public CodeChatService(VectorStoreService vectorStore, EmbeddingService embeddingService,
                           LlmClient llmClient, ChatHistoryMapper chatHistoryMapper,
                           RedisSessionStore redisSessionStore, ToolChatAgent toolChatAgent,
                           ReflectionService reflectionService, SettingService settingService) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.llmClient = llmClient;
        this.chatHistoryMapper = chatHistoryMapper;
        this.redisSessionStore = redisSessionStore;
        this.toolChatAgent = toolChatAgent;
        this.reflectionService = reflectionService;
        this.settingService = settingService;
    }

    public record SessionInfo(String sessionId, String repoUrl, String taskId,
                              String firstQuestion, LocalDateTime createdAt, Long userId) {}

    /**
     * 异步流式发送问答消息：立即保存用户消息，后台流式执行 LLM 调用。
     * 返回 pollId 供前端轮询，轮询可获取增量内容。
     */
    public String sendAsync(String sessionId, String repoUrl, String taskId, String question, Long userId) {
        boolean isNew = sessionId == null || sessionId.isBlank();
        if (isNew) {
            sessionId = UUID.randomUUID().toString();
        }
        final String sid = sessionId;

        sessions.putIfAbsent(sid, new ArrayList<>());
        if (isNew) {
            sessionMeta.put(sid,
                    new SessionInfo(sid, repoUrl, taskId, question, LocalDateTime.now(), userId));
        }

        // 立即保存用户消息
        List<Map<String, String>> history = sessions.get(sid);

        // 检查用户消息是否已经添加过（防重复）
        boolean alreadyAdded = !history.isEmpty()
                && "user".equals(history.get(history.size() - 1).get("role"))
                && question.equals(history.get(history.size() - 1).get("content"));
        if (!alreadyAdded) {
            history.add(Map.of("role", "user", "content", question));
            // 异步持久化用户消息到 MySQL（不阻塞响应）
            virtualExecutor.execute(() -> saveToDb(sid, repoUrl, userId, "user", question));
        }

        // 生成 pollId
        String pollId = UUID.randomUUID().toString();
        pendingResults.put(pollId, new ChatPending(
                pollId, sid, "assistant", "", false, false, null, LocalDateTime.now()));

        // 后台流式执行 LLM 调用
        virtualExecutor.execute(() -> {
            try {
                StringBuilder answerBuilder = new StringBuilder();
                // 令牌缓冲：每 200ms 最多更新一次 pendingResults，减少高频写入
                final long[] lastUpdate = {0};
                java.util.function.Consumer<String> onToken = token -> {
                    answerBuilder.append(token);
                    long now = System.currentTimeMillis();
                    if (now - lastUpdate[0] >= 200) {
                        lastUpdate[0] = now;
                        pendingResults.put(pollId, new ChatPending(
                                pollId, sid, "assistant", answerBuilder.toString(),
                                false, true, null, LocalDateTime.now()));
                    }
                };

                if (taskId != null && !taskId.isBlank()) {
                    ragAnswerStreaming(taskId, question, history, onToken);
                } else {
                    freeAnswerStreaming(repoUrl, question, history, onToken);
                }

                String fullAnswer = answerBuilder.toString();
                // 保存助手回复到会话历史
                history.add(Map.of("role", "assistant", "content", fullAnswer));
                // 写入 Redis
                redisSessionStore.putHistory(sid, history);
                // 异步持久化助手回复到 MySQL
                virtualExecutor.execute(() -> saveToDb(sid, repoUrl, userId, "assistant", fullAnswer));
                // 生成追问建议
                List<String> suggestions = generateFollowUps(question, fullAnswer);
                // 标记完成
                pendingResults.put(pollId, new ChatPending(
                        pollId, sid, "assistant", fullAnswer,
                        true, false, null, LocalDateTime.now(), suggestions));
            } catch (Exception e) {
                log.error("Chat async failed for pollId={}: {}", pollId, e.getMessage(), e);
                pendingResults.put(pollId, new ChatPending(
                        pollId, sid, "assistant", null,
                        true, false, "抱歉，发生了错误：" + e.getMessage(), LocalDateTime.now()));
            }
        });

        return pollId;
    }

    /**
     * 轮询异步结果。
     */
    public ChatPending pollResult(String pollId) {
        return pendingResults.get(pollId);
    }

    /**
     * SSE 流式问答：直接通过 SSE 推送 token，无需轮询。
     */
    public void askStreaming(String sessionId, String repoUrl, String taskId,
                             String question, Long userId, SseEmitter emitter) {
        boolean isNew = sessionId == null || sessionId.isBlank();
        if (isNew) {
            sessionId = UUID.randomUUID().toString();
        }
        final String sid = sessionId;
        sessions.putIfAbsent(sid, new ArrayList<>());
        if (isNew) {
            sessionMeta.put(sid,
                    new SessionInfo(sid, repoUrl, taskId, question, LocalDateTime.now(), userId));
        }

        List<Map<String, String>> history = sessions.get(sid);

        // 防重复
        boolean alreadyAdded = !history.isEmpty()
                && "user".equals(history.get(history.size() - 1).get("role"))
                && question.equals(history.get(history.size() - 1).get("content"));
        if (!alreadyAdded) {
            history.add(Map.of("role", "user", "content", question));
            virtualExecutor.execute(() -> saveToDb(sid, repoUrl, userId, "user", question));
        }

        // 推送 sessionId 事件
        try {
            emitter.send(SseEmitter.event().name("session").data(sid));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return;
        }

        try {
            StringBuilder answerBuilder = new StringBuilder();
            java.util.function.Consumer<String> onToken = token -> {
                answerBuilder.append(token);
                try {
                    emitter.send(SseEmitter.event().name("token").data(token));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            };

            if (taskId != null && !taskId.isBlank()) {
                ragAnswerStreaming(taskId, question, history, onToken);
            } else {
                freeAnswerStreaming(repoUrl, question, history, onToken);
            }

            String fullAnswer = answerBuilder.toString();
            history.add(Map.of("role", "assistant", "content", fullAnswer));
            // 写入 Redis
            redisSessionStore.putHistory(sid, history);
            virtualExecutor.execute(() -> saveToDb(sid, repoUrl, userId, "assistant", fullAnswer));

            // 先发送 done，不阻塞等待追问建议
            emitter.send(SseEmitter.event().name("done").data(""));

            // 异步生成追问建议并推送
            virtualExecutor.execute(() -> {
                try {
                    List<String> suggestions = generateFollowUps(question, fullAnswer);
                    if (!suggestions.isEmpty()) {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        emitter.send(SseEmitter.event().name("suggestions").data(mapper.writeValueAsString(suggestions)));
                    }
                } catch (Exception e) {
                    log.debug("Failed to send suggestions: {}", e.getMessage());
                }
            });
            emitter.complete();
        } catch (Exception e) {
            log.error("SSE streaming failed for session={}: {}", sid, e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data("抱歉，发生了错误：" + e.getMessage()));
            } catch (IOException ignored) {}
            emitter.completeWithError(e);
        }
    }

    /**
     * 发送问答消息（同步）。
     */
    public ChatMessage ask(String sessionId, String repoUrl, String taskId, String question, Long userId) {
        boolean isNew = sessionId == null || sessionId.isBlank();
        if (isNew) {
            sessionId = UUID.randomUUID().toString();
        }
        sessions.putIfAbsent(sessionId, new ArrayList<>());
        if (isNew) {
            sessionMeta.put(sessionId,
                    new SessionInfo(sessionId, repoUrl, taskId, question, LocalDateTime.now(), userId));
        }

        List<Map<String, String>> history = sessions.get(sessionId);

        // 先将用户消息添加到历史，让 LLM 能看到当前问题
        history.add(Map.of("role", "user", "content", question));

        String answer;
        if (taskId != null && !taskId.isBlank()) {
            answer = ragAnswer(taskId, question, history);
        } else {
            answer = freeAnswer(repoUrl, question, history);
        }

        history.add(Map.of("role", "assistant", "content", answer));

        // 写入 Redis
        redisSessionStore.putHistory(sessionId, history);
        // 持久化
        saveToDb(sessionId, repoUrl, userId, "user", question);
        saveToDb(sessionId, repoUrl, userId, "assistant", answer);

        return new ChatMessage(sessionId, "assistant", answer, LocalDateTime.now());
    }

    /**
     * RAG 问答（流式）。
     */
    private void ragAnswerStreaming(String taskId, String question, List<Map<String, String>> history,
                                     java.util.function.Consumer<String> onToken) {
        // 检查是否启用 Agent 模式
        String agentMode = settingService.get("chat_agent_mode");
        if ("react".equalsIgnoreCase(agentMode)) {
            String answer = toolChatAgent.react(taskId, question, history, step -> {
                // 推送 Agent 步骤事件
                try { onToken.accept(""); } catch (Exception ignored) {}
            });
            // 逐字符流式输出最终回答
            for (int i = 0; i < answer.length(); i++) {
                onToken.accept(String.valueOf(answer.charAt(i)));
                try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            return;
        }

        String context = retrieveContext(taskId, question);

        String systemPrompt = """
                你是 CodeXray，一个专业的代码分析助手。
                基于以下检索到的代码片段回答用户问题。

                ## 检索到的代码
                %s

                要求：
                1. 基于代码内容回答，不要编造
                2. 引用具体文件路径和行号
                3. 回答简洁专业，默认中文
                """.formatted(context);

        // Token 感知历史截断：systemPrompt 已占约 6000 tokens，留给历史约 2000 tokens
        int historyBudget = 2000;
        List<Map<String, String>> recentHistory = truncateHistoryByTokens(history, historyBudget);

        llmClient.chatWithContextStreaming(systemPrompt, recentHistory, question, onToken);
    }

    /**
     * RAG 问答（非流式）。
     */
    private String ragAnswer(String taskId, String question, List<Map<String, String>> history) {
        // 检查是否启用 Agent 模式
        String agentMode = settingService.get("chat_agent_mode");
        if ("react".equalsIgnoreCase(agentMode)) {
            String answer = toolChatAgent.react(taskId, question, history, null);
            // Reflection 审查
            return reflectAndRetry(taskId, question, answer, null, history);
        }

        String context = retrieveContext(taskId, question);

        String systemPrompt = """
                你是 CodeXray，一个专业的代码分析助手。
                基于以下检索到的代码片段回答用户问题。

                ## 检索到的代码
                %s

                要求：
                1. 基于代码内容回答，不要编造
                2. 引用具体文件路径和行号
                3. 回答简洁专业，默认中文
                """.formatted(context);

        int historyBudget = 2000;
        List<Map<String, String>> recentHistory = truncateHistoryByTokens(history, historyBudget);

        String answer = llmClient.chatWithContext(systemPrompt, recentHistory, question);
        // Reflection 审查
        return reflectAndRetry(taskId, question, answer, context, recentHistory);
    }

    /**
     * Reflection 审查 + 重试（最多 1 次）。
     */
    private String reflectAndRetry(String taskId, String question, String answer,
                                    String context, List<Map<String, String>> history) {
        try {
            ReflectionService.ReflectionResult result = reflectionService.reflect(question, answer, context);
            if (!result.needsRetry()) {
                return answer;
            }
            log.info("Reflection: score={}, retrying with critique: {}", result.qualityScore(), result.critique());
            // 用 critique 扩充检索词重新回答
            String enhancedQuestion = question + "\n\n[改进建议: " + result.critique() + "]";
            String newContext = retrieveContext(taskId, enhancedQuestion);
            String retryPrompt = """
                    你是 CodeXray，一个专业的代码分析助手。
                    基于以下检索到的代码片段回答用户问题。
                    之前的回答有不足，请根据改进建议给出更好的回答。

                    ## 检索到的代码
                    %s

                    ## 改进建议
                    %s

                    要求：
                    1. 基于代码内容回答，不要编造
                    2. 引用具体文件路径和行号
                    3. 回答简洁专业，默认中文
                    """.formatted(newContext, result.critique());
            int historyBudget = 2000;
            List<Map<String, String>> recentHistory = truncateHistoryByTokens(history, historyBudget);
            return llmClient.chatWithContext(retryPrompt, recentHistory, question);
        } catch (Exception e) {
            log.debug("Reflection skipped: {}", e.getMessage());
            return answer;
        }
    }

    private String retrieveContext(String taskId, String question) {
        // 并行执行向量搜索和文本搜索
        Future<List<VectorStoreService.CodeChunkDoc>> vectorFuture = virtualExecutor.submit(() -> {
            float[] qvec = embeddingService.embed(question);
            return vectorStore.search(taskId, qvec, 5, null);
        });
        Future<List<VectorStoreService.CodeChunkDoc>> textFuture = virtualExecutor.submit(() ->
                vectorStore.searchText(taskId, question, 3));

        List<VectorStoreService.CodeChunkDoc> chunks = new ArrayList<>();
        boolean vectorFailed = false;
        try {
            chunks.addAll(vectorFuture.get(3, java.util.concurrent.TimeUnit.SECONDS));
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage());
            vectorFailed = true;
        }
        try {
            chunks.addAll(textFuture.get(3, java.util.concurrent.TimeUnit.SECONDS));
        } catch (Exception e) {
            log.warn("Text search failed: {}", e.getMessage());
        }

        // 向量搜索失败时，扩大文本搜索兜底
        if (vectorFailed && chunks.isEmpty()) {
            try {
                chunks.addAll(vectorStore.searchText(taskId, question, 20));
            } catch (Exception e) {
                log.warn("Fallback text search also failed: {}", e.getMessage());
            }
        }

        List<VectorStoreService.CodeChunkDoc> unique = deduplicate(chunks);

        if (unique.isEmpty()) {
            return "（未找到相关代码片段。可能原因：Embedding API 不可达或仓库尚未完成分析）";
        }

        // Token 感知截断：context 预算 = 6000 tokens（为 system prompt + history + question 预留空间）
        int contextBudget = 6000;
        List<VectorStoreService.CodeChunkDoc> truncated =
                TokenEstimator.truncateByTokenBudget(unique, c -> c.content(), contextBudget);

        StringBuilder sb = new StringBuilder();
        for (var c : truncated) {
            sb.append("### ").append(c.file_path())
              .append(" [").append(c.start_line()).append("-").append(c.end_line()).append("]\n")
              .append(c.content()).append("\n\n");
        }
        if (truncated.size() < unique.size()) {
            sb.append("（已省略 ").append(unique.size() - truncated.size()).append(" 个代码片段以控制上下文长度）");
        }
        return sb.toString();
    }

    /**
     * 自由问答（流式）。
     */
    private void freeAnswerStreaming(String repoUrl, String question, List<Map<String, String>> history,
                                      java.util.function.Consumer<String> onToken) {
        String systemPrompt = """
                你是 CodeXray，一个专业的代码分析助手。
                用户正在讨论仓库: %s
                请基于你的知识回答问题。
                """.formatted(repoUrl != null ? repoUrl : "未知仓库");

        int historyBudget = 6000;
        List<Map<String, String>> recentHistory = truncateHistoryByTokens(history, historyBudget);

        llmClient.chatWithContextStreaming(systemPrompt, recentHistory, question, onToken);
    }

    /**
     * 自由问答（非流式）。
     */
    private String freeAnswer(String repoUrl, String question, List<Map<String, String>> history) {
        String systemPrompt = """
                你是 CodeXray，一个专业的代码分析助手。
                用户正在讨论仓库: %s
                请基于你的知识回答问题。
                """.formatted(repoUrl != null ? repoUrl : "未知仓库");

        int historyBudget = 6000;
        List<Map<String, String>> recentHistory = truncateHistoryByTokens(history, historyBudget);

        return llmClient.chatWithContext(systemPrompt, recentHistory, question);
    }

    /**
     * Token 感知历史截断：从最近消息开始累加 token，超出预算则截断。
     */
    private List<Map<String, String>> truncateHistoryByTokens(List<Map<String, String>> history, int maxTokens) {
        if (history.isEmpty()) return history;
        int used = 0;
        int startIdx = history.size();
        for (int i = history.size() - 1; i >= 0; i--) {
            int tokens = TokenEstimator.estimateTokens(history.get(i).get("content"));
            if (used + tokens > maxTokens) break;
            used += tokens;
            startIdx = i;
        }
        List<Map<String, String>> result = history.subList(startIdx, history.size());
        if (startIdx > 0) {
            // 插入提示消息
            List<Map<String, String>> withHint = new ArrayList<>();
            withHint.add(Map.of("role", "system", "content", "（以下早期对话已被省略）"));
            withHint.addAll(result);
            return withHint;
        }
        return result;
    }

    private List<VectorStoreService.CodeChunkDoc> deduplicate(List<VectorStoreService.CodeChunkDoc> results) {
        Map<String, VectorStoreService.CodeChunkDoc> map = new LinkedHashMap<>();
        for (var doc : results) {
            String key = doc.file_path() + ":" + doc.start_line();
            map.putIfAbsent(key, doc);
        }
        return new ArrayList<>(map.values());
    }

    /**
     * 获取会话历史：L1 内存 → L2 Redis → MySQL。
     */
    public List<ChatMessage> getHistory(String sessionId) {
        List<Map<String, String>> history = sessions.get(sessionId);
        if (history == null || history.isEmpty()) {
            // L2: Redis
            history = redisSessionStore.getHistory(sessionId);
            if (history != null && !history.isEmpty()) {
                sessions.put(sessionId, history);
            }
        }
        if (history == null || history.isEmpty()) {
            // L3: MySQL 恢复
            List<ChatHistory> dbHistory = chatHistoryMapper.selectList(
                    new QueryWrapper<ChatHistory>().eq("session_id", sessionId)
                            .orderByAsc("created_at"));
            if (!dbHistory.isEmpty()) {
                history = new ArrayList<>();
                String firstQuestion = null;
                for (ChatHistory h : dbHistory) {
                    history.add(Map.of("role", h.getRole(), "content", h.getContent()));
                    if (firstQuestion == null && "user".equals(h.getRole()) && h.getContent() != null) {
                        String c = h.getContent().trim();
                        firstQuestion = c.length() > 50 ? c.substring(0, 50) + "..." : c;
                    }
                }
                sessions.put(sessionId, history);
                // 恢复元信息
                ChatHistory first = dbHistory.get(0);
                SessionInfo meta = new SessionInfo(sessionId, first.getRepoUrl(), null, firstQuestion,
                                first.getCreatedAt(), first.getUserId());
                sessionMeta.putIfAbsent(sessionId, meta);
                // 回写 Redis
                redisSessionStore.putHistory(sessionId, history);
                redisSessionStore.putMeta(sessionId, meta);
            } else {
                return List.of();
            }
        }

        List<ChatMessage> result = new ArrayList<>();
        for (Map<String, String> msg : history) {
            String role = msg.get("role");
            if ("user".equals(role) || "assistant".equals(role)) {
                result.add(new ChatMessage(sessionId, role, msg.get("content"), LocalDateTime.now()));
            }
        }
        return result;
    }

    /**
     * 获取所有会话列表：L1 内存 → L2 Redis → MySQL。
     */
    public List<SessionInfo> getSessions(String repoUrl, Long userId) {
        // 如果内存中没有数据，先从 Redis 恢复，再从 MySQL
        if (sessionMeta.isEmpty()) {
            if (userId != null) {
                List<SessionInfo> redisMetas = redisSessionStore.getAllMeta(userId);
                for (SessionInfo meta : redisMetas) {
                    sessionMeta.putIfAbsent(meta.sessionId(), meta);
                }
            }
            if (sessionMeta.isEmpty()) {
                loadSessionsFromDb(userId);
            }
        }

        return sessionMeta.values().stream()
                .filter(s -> userId == null || Objects.equals(s.userId(), userId))
                .filter(s -> repoUrl == null || repoUrl.isBlank()
                        || repoUrl.equals(s.repoUrl()))
                .sorted(Comparator.comparing(SessionInfo::createdAt).reversed())
                .toList();
    }

    private void loadSessionsFromDb(Long userId) {
        QueryWrapper<ChatHistory> wrapper = new QueryWrapper<ChatHistory>()
                .select("DISTINCT session_id", "repo_url", "user_id", "MIN(created_at) as created_at")
                .groupBy("session_id", "repo_url", "user_id");
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        wrapper.orderByAsc("created_at");
        wrapper.last("LIMIT 50");

        List<ChatHistory> records = chatHistoryMapper.selectList(wrapper);
        if (records.isEmpty()) return;

        // 批量获取所有 session 的第一条用户消息（1 条 SQL 替代 N 条）
        List<String> sessionIds = records.stream().map(ChatHistory::getSessionId).toList();
        Map<String, String> firstQuestions = batchGetFirstQuestions(sessionIds);

        for (ChatHistory h : records) {
            String q = firstQuestions.get(h.getSessionId());
            SessionInfo meta = new SessionInfo(h.getSessionId(), h.getRepoUrl(), null,
                            q, h.getCreatedAt(), h.getUserId());
            sessionMeta.putIfAbsent(h.getSessionId(), meta);
            // 回写 Redis
            redisSessionStore.putMeta(h.getSessionId(), meta);
        }
    }

    /**
     * 批量获取多个 session 的第一条用户消息，仅 1 条 SQL。
     */
    private Map<String, String> batchGetFirstQuestions(List<String> sessionIds) {
        Map<String, String> result = new HashMap<>();
        try {
            // 一条 SQL 获取所有 session 的 user 消息（按时间排序）
            QueryWrapper<ChatHistory> q = new QueryWrapper<ChatHistory>()
                    .select("session_id", "content")
                    .in("session_id", sessionIds)
                    .eq("role", "user")
                    .orderByAsc("created_at");
            List<ChatHistory> allMessages = chatHistoryMapper.selectList(q);
            // 每个 session 只取第一条
            Set<String> seen = new HashSet<>();
            for (ChatHistory h : allMessages) {
                if (seen.add(h.getSessionId()) && h.getContent() != null) {
                    String content = h.getContent().trim();
                    result.put(h.getSessionId(),
                            content.length() > 50 ? content.substring(0, 50) + "..." : content);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to batch get first questions: {}", e.getMessage());
        }
        return result;
    }

    public String newSession(String repoUrl, String taskId, Long userId) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new ArrayList<>());
        SessionInfo meta = new SessionInfo(sessionId, repoUrl, taskId, null, LocalDateTime.now(), userId);
        sessionMeta.put(sessionId, meta);
        // 写入 Redis
        redisSessionStore.putMeta(sessionId, meta);
        return sessionId;
    }

    public void deleteSession(String sessionId) {
        SessionInfo meta = sessionMeta.remove(sessionId);
        sessions.remove(sessionId);
        // 删除 Redis
        redisSessionStore.deleteHistory(sessionId);
        redisSessionStore.deleteMeta(sessionId, meta != null ? meta.userId() : null);
        // 删除 MySQL 记录
        chatHistoryMapper.delete(
                new QueryWrapper<ChatHistory>().eq("session_id", sessionId));
    }

    private void saveToDb(String sessionId, String repoUrl, Long userId, String role, String content) {
        try {
            ChatHistory record = new ChatHistory();
            record.setSessionId(sessionId);
            record.setUserId(userId);
            record.setRepoUrl(repoUrl != null ? repoUrl : "");
            record.setRole(role);
            record.setContent(content);
            record.setCreatedAt(LocalDateTime.now());
            chatHistoryMapper.insert(record);
        } catch (Exception e) {
            log.warn("Failed to save chat history to DB: {}", e.getMessage());
        }
    }

    /**
     * 生成智能追问建议（3 个后续问题）。
     */
    public List<String> generateFollowUps(String question, String answer) {
        try {
            String prompt = """
                    基于以下问答，生成 3 个相关的后续问题（简洁、有价值、适合深入理解代码）。
                    只返回 JSON 数组格式，不要其他内容: ["问题1", "问题2", "问题3"]

                    问题: %s
                    回答: %s
                    """.formatted(question, answer.length() > 500 ? answer.substring(0, 500) : answer);
            String response = llmClient.chatWithContext("你是追问建议生成器。", List.of(), prompt);
            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            }
            if (json.startsWith("[") && json.endsWith("]")) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.readValue(json, List.class);
            }
        } catch (Exception e) {
            log.debug("Follow-up generation failed: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * 导出会话为 Markdown。
     */
    public String exportAsMarkdown(String sessionId) {
        List<ChatMessage> messages = getHistory(sessionId);
        if (messages.isEmpty()) return "（空会话）";

        StringBuilder sb = new StringBuilder();
        sb.append("# CodeXray 会话导出\n\n");
        SessionInfo meta = sessionMeta.get(sessionId);
        if (meta != null) {
            sb.append("**仓库**: ").append(meta.repoUrl() != null ? meta.repoUrl() : "未知").append("\n");
            sb.append("**创建时间**: ").append(meta.createdAt()).append("\n\n");
        }
        sb.append("---\n\n");

        for (ChatMessage msg : messages) {
            if ("user".equals(msg.role())) {
                sb.append("## 用户\n\n").append(msg.content()).append("\n\n");
            } else {
                sb.append("## CodeXray\n\n").append(msg.content()).append("\n\n");
            }
            sb.append("---\n\n");
        }
        return sb.toString();
    }

    /**
     * 清理过期数据：pendingResults 超过 10 分钟的，chat_history 超过 3 天的。
     */
    @Scheduled(fixedRate = 300000) // 每 5 分钟
    public void cleanup() {
        LocalDateTime cutoffPoll = LocalDateTime.now().minusMinutes(10);
        pendingResults.entrySet().removeIf(e -> e.getValue().timestamp().isBefore(cutoffPoll));

        LocalDateTime cutoffHistory = LocalDateTime.now().minusDays(3);
        int deleted = chatHistoryMapper.delete(
                new QueryWrapper<ChatHistory>().lt("created_at", cutoffHistory));
        if (deleted > 0) {
            log.info("Cleaned up {} old chat history records", deleted);
        }
    }
}
