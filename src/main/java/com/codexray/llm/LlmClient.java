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

    /**
     * 自定义上下文问答（支持 RAG 检索结果 + 多轮对话历史）。
     *
     * @param systemPrompt 系统提示（包含检索到的代码上下文）
     * @param history      对话历史，每条为 {"role":"user"/"assistant","content":"..."}
     * @param question     当前问题
     * @return LLM 回答
     */
    String chatWithContext(String systemPrompt, java.util.List<java.util.Map<String, String>> history, String question);

    /**
     * 对 GitHub Trending 热点仓库进行简要分析。
     *
     * @param repoName     仓库名称
     * @param description  仓库描述
     * @param lang         语言: zh 或 en
     * @return 分析文本
     */
    String analyzeTrendingRepo(String repoName, String description, String lang);
}
