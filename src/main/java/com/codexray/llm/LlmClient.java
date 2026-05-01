package com.codexray.llm;

public interface LlmClient {

    /**
     * 对仓库代码进行深度分析，返回结构化 JSON 报告。
     */
    String analyze(String repoPath);

    /**
     * 基于仓库代码上下文回答用户问题（RAG 问答）。
     *
     * @param repoPath  本地仓库路径
     * @param question  用户提问
     * @return LLM 回答
     */
    String chat(String repoPath, String question);
}
