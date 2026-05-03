package com.codexray.agent.tools;

import com.codexray.rag.VectorStoreService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 代码搜索工具 — 按关键词或向量搜索代码片段。
 */
@Component
public class CodeSearchTool implements ChatTool {

    private final VectorStoreService vectorStore;

    public CodeSearchTool(VectorStoreService vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public String name() { return "code_search"; }

    @Override
    public String description() { return "搜索代码。参数: taskId(必须), query(必须), category(可选), topK(可选,默认5)"; }

    @Override
    public String paramSchema() { return "{\"taskId\":\"string\",\"query\":\"string\",\"category\":\"string?\",\"topK\":\"int?\"}"; }

    @Override
    public String execute(Map<String, String> args) {
        String taskId = args.get("taskId");
        String query = args.get("query");
        if (taskId == null || query == null) return "错误: 缺少 taskId 或 query 参数";

        int topK = Integer.parseInt(args.getOrDefault("topK", "5"));
        String category = args.get("category");

        List<VectorStoreService.CodeChunkDoc> results;
        if (category != null && !category.isBlank()) {
            results = vectorStore.searchByCategory(taskId, category, topK);
        } else {
            results = vectorStore.searchText(taskId, query, topK);
        }

        if (results.isEmpty()) return "未找到相关代码。";

        return results.stream()
                .map(c -> c.file_path() + ":" + c.start_line() + "-" + c.end_line() + "\n" + c.content())
                .collect(Collectors.joining("\n---\n"));
    }
}
