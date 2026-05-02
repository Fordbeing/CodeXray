package com.codexray.llm;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface LlmClient {

    /**
     * 对仓库代码进行深度分析，返回结构化 JSON 报告。
     */
    String analyze(String repoPath);

    /**
     * 基于仓库代码上下文回答用户问题（RAG 问答）。
     */
    String chat(String repoPath, String question);

    /**
     * 自定义上下文问答（支持 RAG 检索结果 + 多轮对话历史）。
     */
    String chatWithContext(String systemPrompt, List<Map<String, String>> history, String question);

    /**
     * 流式自定义上下文问答，每收到一个 token 回调 onToken。
     * 默认实现调用非流式方法并将完整结果一次性回调。
     */
    default void chatWithContextStreaming(String systemPrompt, List<Map<String, String>> history,
                                          String question, Consumer<String> onToken) {
        String result = chatWithContext(systemPrompt, history, question);
        onToken.accept(result);
    }

    /**
     * 对 GitHub Trending 热点仓库进行简要分析。
     */
    String analyzeTrendingRepo(String repoName, String description, String lang);

    /**
     * 测试 AI 连接是否正常，返回模型回复内容。
     */
    String testConnection();
}
