package com.codexray.agent.tools;

import com.codexray.rag.VectorStoreService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 符号查找工具 — 按类名/方法名搜索代码。
 */
@Component
public class SymbolLookupTool implements ChatTool {

    private final VectorStoreService vectorStore;

    public SymbolLookupTool(VectorStoreService vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public String name() { return "symbol_lookup"; }

    @Override
    public String description() { return "按类名/方法名查找符号定义。参数: taskId(必须), symbol(必须)"; }

    @Override
    public String paramSchema() { return "{\"taskId\":\"string\",\"symbol\":\"string\"}"; }

    @Override
    public String execute(Map<String, String> args) {
        String taskId = args.get("taskId");
        String symbol = args.get("symbol");
        if (taskId == null || symbol == null) return "错误: 缺少 taskId 或 symbol 参数";

        // 用 symbol 作为搜索词进行全文搜索
        List<VectorStoreService.CodeChunkDoc> results = vectorStore.searchText(taskId, symbol, 5);
        if (results.isEmpty()) return "未找到符号: " + symbol;

        return results.stream()
                .map(c -> c.file_path() + ":" + c.start_line() + " (" + (c.symbol_name() != null ? c.symbol_name() : "") + ")\n" + c.content())
                .collect(Collectors.joining("\n---\n"));
    }
}
