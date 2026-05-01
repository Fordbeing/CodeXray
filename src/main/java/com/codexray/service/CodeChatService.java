package com.codexray.service;

import com.codexray.llm.LlmClient;
import com.codexray.model.dto.ChatMessage;
import com.codexray.rag.EmbeddingService;
import com.codexray.rag.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 代码问答服务 — 支持多轮对话，基于 RAG 检索增强生成。
 */
@Service
public class CodeChatService {

    private static final Logger log = LoggerFactory.getLogger(CodeChatService.class);

    private final VectorStoreService vectorStore;
    private final EmbeddingService embeddingService;
    private final LlmClient llmClient;

    // 会话历史：sessionId → List<{role, content}>
    private final Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();
    // 会话元信息
    private final Map<String, SessionInfo> sessionMeta = new ConcurrentHashMap<>();

    public CodeChatService(VectorStoreService vectorStore, EmbeddingService embeddingService,
                           LlmClient llmClient) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.llmClient = llmClient;
    }

    public record SessionInfo(String sessionId, String repoUrl, String taskId,
                              String firstQuestion, LocalDateTime createdAt) {}

    /**
     * 发送问答消息。
     */
    public ChatMessage ask(String sessionId, String repoUrl, String taskId, String question) {
        boolean isNew = sessionId == null || sessionId.isBlank();
        if (isNew) {
            sessionId = UUID.randomUUID().toString();
        }
        sessions.putIfAbsent(sessionId, new ArrayList<>());
        if (isNew) {
            sessionMeta.put(sessionId,
                    new SessionInfo(sessionId, repoUrl, taskId, question, LocalDateTime.now()));
        }

        List<Map<String, String>> history = sessions.get(sessionId);

        String answer;
        if (taskId != null && !taskId.isBlank()) {
            answer = ragAnswer(taskId, question, history);
        } else {
            answer = freeAnswer(repoUrl, question, history);
        }

        history.add(Map.of("role", "user", "content", question));
        history.add(Map.of("role", "assistant", "content", answer));

        return new ChatMessage(sessionId, "assistant", answer, LocalDateTime.now());
    }

    /**
     * RAG 问答：基于已分析仓库的向量检索。
     */
    private String ragAnswer(String taskId, String question, List<Map<String, String>> history) {
        List<VectorStoreService.CodeChunkDoc> chunks = new ArrayList<>();
        try {
            float[] qvec = embeddingService.embed(question);
            chunks.addAll(vectorStore.search(taskId, qvec, 6, null));
        } catch (Exception e) {
            log.warn("Vector search failed, falling back to text search: {}", e.getMessage());
        }
        chunks.addAll(vectorStore.searchText(taskId, question, 4));

        List<VectorStoreService.CodeChunkDoc> unique = deduplicate(chunks);

        String context;
        if (unique.isEmpty()) {
            context = "（未找到相关代码片段，请确认仓库已完成分析）";
        } else {
            context = unique.stream().map(c ->
                    "### " + c.file_path() + " [" + c.start_line() + "-" + c.end_line() + "]\n"
                            + c.content()
            ).collect(Collectors.joining("\n\n"));
        }

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

    /**
     * 自由问答：无分析任务时的通用模式。
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

    public List<ChatMessage> getHistory(String sessionId) {
        List<Map<String, String>> history = sessions.getOrDefault(sessionId, List.of());
        List<ChatMessage> result = new ArrayList<>();
        for (Map<String, String> msg : history) {
            String role = msg.get("role");
            if ("user".equals(role) || "assistant".equals(role)) {
                result.add(new ChatMessage(sessionId, role, msg.get("content"), LocalDateTime.now()));
            }
        }
        return result;
    }

    public List<SessionInfo> getSessions(String repoUrl) {
        return sessionMeta.values().stream()
                .filter(s -> repoUrl == null || repoUrl.isBlank()
                        || repoUrl.equals(s.repoUrl()))
                .sorted(Comparator.comparing(SessionInfo::createdAt).reversed())
                .toList();
    }

    public String newSession(String repoUrl, String taskId) {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new ArrayList<>());
        sessionMeta.put(sessionId,
                new SessionInfo(sessionId, repoUrl, taskId, null, LocalDateTime.now()));
        return sessionId;
    }

    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
        sessionMeta.remove(sessionId);
    }
}
