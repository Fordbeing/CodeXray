package com.codexray.agent.tools;

import com.codexray.rag.VectorStoreService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 依赖检查工具 — 搜索引用指定类的代码。
 */
@Component
public class DependencyCheckTool implements ChatTool {

    private final VectorStoreService vectorStore;

    public DependencyCheckTool(VectorStoreService vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public String name() { return "dependency_check"; }

    @Override
    public String description() { return "搜索引用指定类名的代码。参数: taskId(必须), className(必须)"; }

    @Override
    public String paramSchema() { return "{\"taskId\":\"string\",\"className\":\"string\"}"; }

    @Override
    public String execute(Map<String, String> args) {
        String taskId = args.get("taskId");
        String className = args.get("className");
        if (taskId == null || className == null) return "错误: 缺少 taskId 或 className 参数";

        List<VectorStoreService.CodeChunkDoc> results = vectorStore.searchText(taskId, className, 10);
        if (results.isEmpty()) return "未找到引用 " + className + " 的代码。";

        return results.stream()
                .map(c -> c.file_path() + ":" + c.start_line() + "\n" + c.content())
                .collect(Collectors.joining("\n---\n"));
    }
}
