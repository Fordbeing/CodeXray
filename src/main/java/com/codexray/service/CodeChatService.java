package com.codexray.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // 会话历史：sessionId → List<{role, content}>（内存热缓存）
    private final Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();
    // 会话元信息
    private final Map<String, SessionInfo> sessionMeta = new ConcurrentHashMap<>();
    // 异步请求结果：pollId → ChatPending
    private final Map<String, ChatPending> pendingResults = new ConcurrentHashMap<>();

    public CodeChatService(VectorStoreService vectorStore, EmbeddingService embeddingService,
                           LlmClient llmClient, ChatHistoryMapper chatHistoryMapper) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.llmClient = llmClient;
        this.chatHistoryMapper = chatHistoryMapper;
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
                // 异步持久化助手回复到 MySQL
                virtualExecutor.execute(() -> saveToDb(sid, repoUrl, userId, "assistant", fullAnswer));
                // 标记完成
                pendingResults.put(pollId, new ChatPending(
                        pollId, sid, "assistant", fullAnswer,
                        true, false, null, LocalDateTime.now()));
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
            virtualExecutor.execute(() -> saveToDb(sid, repoUrl, userId, "assistant", fullAnswer));

            emitter.send(SseEmitter.event().name("done").data(""));
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

        List<Map<String, String>> recentHistory = history.size() > 20
                ? history.subList(history.size() - 20, history.size())
                : history;

        llmClient.chatWithContextStreaming(systemPrompt, recentHistory, question, onToken);
    }

    /**
     * RAG 问答（非流式）。
     */
    private String ragAnswer(String taskId, String question, List<Map<String, String>> history) {
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

        List<Map<String, String>> recentHistory = history.size() > 20
                ? history.subList(history.size() - 20, history.size())
                : history;

        return llmClient.chatWithContext(systemPrompt, recentHistory, question);
    }

    private String retrieveContext(String taskId, String question) {
        // 并行执行向量搜索和文本搜索
        Future<List<VectorStoreService.CodeChunkDoc>> vectorFuture = virtualExecutor.submit(() -> {
            float[] qvec = embeddingService.embed(question);
            return vectorStore.search(taskId, qvec, 6, null);
        });
        Future<List<VectorStoreService.CodeChunkDoc>> textFuture = virtualExecutor.submit(() ->
                vectorStore.searchText(taskId, question, 4));

        List<VectorStoreService.CodeChunkDoc> chunks = new ArrayList<>();
        boolean vectorFailed = false;
        try {
            chunks.addAll(vectorFuture.get(5, java.util.concurrent.TimeUnit.SECONDS));
        } catch (Exception e) {
            log.warn("Vector search failed: {}", e.getMessage());
            vectorFailed = true;
        }
        try {
            chunks.addAll(textFuture.get(5, java.util.concurrent.TimeUnit.SECONDS));
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
        return unique.stream().map(c ->
                "### " + c.file_path() + " [" + c.start_line() + "-" + c.end_line() + "]\n"
                        + c.content()
        ).collect(Collectors.joining("\n\n"));
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

        List<Map<String, String>> recentHistory = history.size() > 20
                ? history.subList(history.size() - 20, history.size())
                : history;

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

        List<Map<String, String>> recentHistory = history.size() > 20
                ? history.subList(history.size() - 20, history.size())
                : history;

        return llmClient.chatWithContext(systemPrompt, recentHistory, question);
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
     * 获取会话历史：优先从内存恢复，否则从 MySQL 加载。
     */
    public List<ChatMessage> getHistory(String sessionId) {
        List<Map<String, String>> history = sessions.get(sessionId);
        if (history == null || history.isEmpty()) {
            // 从 MySQL 恢复
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
                sessionMeta.putIfAbsent(sessionId,
                        new SessionInfo(sessionId, first.getRepoUrl(), null, firstQuestion,
                                first.getCreatedAt(), first.getUserId()));
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
     * 获取所有会话列表：优先内存，否则从 MySQL 恢复。
     */
    public List<SessionInfo> getSessions(String repoUrl, Long userId) {
        // 如果内存中没有数据，从 MySQL 恢复
        if (sessionMeta.isEmpty()) {
            loadSessionsFromDb(userId);
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
            sessionMeta.putIfAbsent(h.getSessionId(),
                    new SessionInfo(h.getSessionId(), h.getRepoUrl(), null,
                            q, h.getCreatedAt(), h.getUserId()));
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
        sessionMeta.put(sessionId,
                new SessionInfo(sessionId, repoUrl, taskId, null, LocalDateTime.now(), userId));
        return sessionId;
    }

    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
        sessionMeta.remove(sessionId);
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
