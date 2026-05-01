package com.codexray.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codexray.llm.LlmClient;
import com.codexray.mapper.ChatHistoryMapper;
import com.codexray.model.entity.ChatHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CodeChatService {

    private static final Logger log = LoggerFactory.getLogger(CodeChatService.class);

    private final GitCloneService gitCloneService;
    private final LlmClient llmClient;
    private final ChatHistoryMapper chatHistoryMapper;

    public CodeChatService(GitCloneService gitCloneService, LlmClient llmClient,
                           ChatHistoryMapper chatHistoryMapper) {
        this.gitCloneService = gitCloneService;
        this.llmClient = llmClient;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    /**
     * 对仓库代码提问，持久化问答记录。
     *
     * @param sessionId 会话 ID（为空则新建会话）
     * @return [answer, sessionId]
     */
    public String[] ask(String repoUrl, String question, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        // 保存用户问题
        saveMessage(sessionId, repoUrl, "user", question);

        String answer;
        String localPath = null;
        try {
            localPath = gitCloneService.clone(repoUrl);
            answer = llmClient.chat(localPath, question);
        } catch (Exception e) {
            log.error("Chat failed for repo: {}", repoUrl, e);
            answer = "抱歉，处理您的问题时发生错误：" + e.getMessage();
        } finally {
            if (localPath != null) {
                gitCloneService.cleanup(localPath);
            }
        }

        // 保存回答
        saveMessage(sessionId, repoUrl, "assistant", answer);

        return new String[]{answer, sessionId};
    }

    /**
     * 查询会话历史。
     */
    public List<ChatHistory> getHistory(String sessionId) {
        return chatHistoryMapper.selectList(
                new QueryWrapper<ChatHistory>().eq("session_id", sessionId)
                        .orderByAsc("created_at")
        );
    }

    /**
     * 查询所有会话列表（按最近活跃时间倒序）。
     */
    public List<ChatSessionInfo> listSessions(int limit) {
        // 按 session_id 分组，取最新一条记录的时间
        String sql = """
                SELECT session_id, repo_url, MAX(created_at) as last_active
                FROM chat_history
                GROUP BY session_id, repo_url
                ORDER BY last_active DESC
                LIMIT %d
                """.formatted(Math.min(limit, 50));

        return chatHistoryMapper.selectObjs(
                new QueryWrapper<ChatHistory>()
                        .select("DISTINCT session_id")
                        .orderByDesc("created_at")
                        .last("LIMIT " + Math.min(limit, 50))
        ).stream()
                .map(sid -> {
                    List<ChatHistory> msgs = getHistory((String) sid);
                    String repoUrl = msgs.isEmpty() ? "" : msgs.get(0).getRepoUrl();
                    String preview = msgs.isEmpty() ? "" :
                            msgs.stream().filter(m -> "user".equals(m.getRole()))
                                    .findFirst().map(m -> m.getContent().length() > 50
                                            ? m.getContent().substring(0, 50) + "..." : m.getContent())
                                    .orElse("");
                    LocalDateTime lastActive = msgs.isEmpty() ? null :
                            msgs.get(msgs.size() - 1).getCreatedAt();
                    return new ChatSessionInfo((String) sid, repoUrl, preview, lastActive, msgs.size());
                })
                .toList();
    }

    private void saveMessage(String sessionId, String repoUrl, String role, String content) {
        ChatHistory history = new ChatHistory();
        history.setSessionId(sessionId);
        history.setRepoUrl(repoUrl);
        history.setRole(role);
        history.setContent(content);
        history.setCreatedAt(LocalDateTime.now());
        chatHistoryMapper.insert(history);
    }

    public record ChatSessionInfo(
            String sessionId,
            String repoUrl,
            String preview,
            LocalDateTime lastActive,
            int messageCount
    ) {}
}
