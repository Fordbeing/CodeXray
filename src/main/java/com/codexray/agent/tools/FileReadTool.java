package com.codexray.agent.tools;

import com.codexray.rag.VectorStoreService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件读取工具 — 从 ES 中读取指定文件的内容。
 */
@Component
public class FileReadTool implements ChatTool {

    private final VectorStoreService vectorStore;

    public FileReadTool(VectorStoreService vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public String name() { return "file_read"; }

    @Override
    public String description() { return "读取指定文件的内容。参数: taskId(必须), path(必须)"; }

    @Override
    public String paramSchema() { return "{\"taskId\":\"string\",\"path\":\"string\"}"; }

    @Override
    public String execute(Map<String, String> args) {
        String taskId = args.get("taskId");
        String path = args.get("path");
        if (taskId == null || path == null) return "错误: 缺少 taskId 或 path 参数";

        // 通过全文搜索精确匹配文件路径
        List<VectorStoreService.CodeChunkDoc> results = vectorStore.searchText(taskId, path, 50);
        List<VectorStoreService.CodeChunkDoc> fileChunks = results.stream()
                .filter(c -> c.file_path().equals(path) || c.file_path().endsWith("/" + path))
                .sorted((a, b) -> Integer.compare(a.start_line(), b.start_line()))
                .collect(Collectors.toList());

        if (fileChunks.isEmpty()) return "未找到文件: " + path;

        return fileChunks.stream()
                .map(c -> "[" + c.start_line() + "-" + c.end_line() + "]\n" + c.content())
                .collect(Collectors.joining("\n"));
    }
}
