package com.codexray.rag;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 查询路由器：意图识别 + 查询改写。
 * 将用户自然语言问题转化为向量搜索和关键词搜索的 query。
 */
@Service
public class QueryRouter {

    public record QueryPlan(
            List<String> vectorQueries,  // 向量搜索的 query 列表
            List<String> textQueries,    // 全文搜索的 query 列表
            String category,             // 可选的模块过滤
            QueryType type               // 意图类型
    ) {}

    public enum QueryType {
        CODE_LOCATE,    // "XX 在哪里" → 返回文件路径
        EXPLAIN,        // "XX 是怎么实现的" → 返回代码 + 解释
        OVERVIEW,       // "用了什么技术" → 返回 ProjectProfile
        OPTIMIZE        // "怎么优化" → 返回代码 + 建议
    }

    /**
     * 分析用户意图，生成查询计划。
     */
    public QueryPlan route(String question) {
        String q = question.toLowerCase();
        QueryType type = classifyIntent(q);
        List<String> vectorQueries = new ArrayList<>();
        List<String> textQueries = new ArrayList<>();
        String category = null;

        // 提取关键词
        Set<String> keywords = extractKeywords(q);

        // 向量查询：用原始问题 + 提取的关键词
        vectorQueries.add(question);
        if (!keywords.isEmpty()) {
            vectorQueries.add(String.join(" ", keywords));
        }

        // 全文查询：提取名词和动词
        textQueries.addAll(keywords);

        // 根据意图类型调整
        switch (type) {
            case CODE_LOCATE -> {
                // 增加路径相关的搜索词
                textQueries.add("class");
                textQueries.add("def");
            }
            case EXPLAIN -> {
                // 扩展查询
                for (String kw : new ArrayList<>(keywords)) {
                    if (kw.contains("auth")) textQueries.addAll(List.of("token", "jwt", "login", "session"));
                    if (kw.contains("db") || kw.contains("database")) textQueries.addAll(List.of("sql", "query", "mapper", "repository"));
                    if (kw.contains("api")) textQueries.addAll(List.of("controller", "endpoint", "rest"));
                }
            }
            case OPTIMIZE -> {
                // 增加性能相关词
                textQueries.addAll(List.of("cache", "pool", "async", "batch", "lock"));
            }
            default -> {}
        }

        // 推断可能的 category
        category = inferCategory(q);

        return new QueryPlan(vectorQueries, textQueries, category, type);
    }

    private QueryType classifyIntent(String q) {
        if (q.contains("在哪") || q.contains("where") || q.contains("哪个文件") || q.contains("查找")) {
            return QueryType.CODE_LOCATE;
        }
        if (q.contains("怎么") || q.contains("如何") || q.contains("how") || q.contains("实现")
                || q.contains("原理") || q.contains("逻辑")) {
            return QueryType.EXPLAIN;
        }
        if (q.contains("什么技术") || q.contains("架构") || q.contains("用了") || q.contains("tech")
                || q.contains("overview") || q.contains("介绍")) {
            return QueryType.OVERVIEW;
        }
        if (q.contains("优化") || q.contains("改进") || q.contains("improve") || q.contains("optimize")) {
            return QueryType.OPTIMIZE;
        }
        return QueryType.EXPLAIN;
    }

    private Set<String> extractKeywords(String q) {
        Set<String> keywords = new LinkedHashSet<>();
        // 移除常见停用词
        String cleaned = q.replaceAll("[是的了在有什么怎么如何哪里为什么吗呢吧啊]", " ")
                .replaceAll("\\s+", " ").trim();

        for (String word : cleaned.split("\\s+")) {
            if (word.length() >= 2) {
                keywords.add(word);
            }
        }

        // 常见技术术语映射
        Map<String, List<String>> synonymMap = Map.of(
                "认证", List.of("auth", "login", "token", "jwt", "security"),
                "认证逻辑", List.of("auth", "login", "token", "jwt"),
                "数据库", List.of("database", "db", "sql", "mysql", "mapper"),
                "api", List.of("controller", "endpoint", "rest", "route"),
                "缓存", List.of("cache", "redis"),
                "消息", List.of("message", "mq", "rocketmq", "kafka"),
                "配置", List.of("config", "properties", "yml", "yaml")
        );

        for (Map.Entry<String, List<String>> entry : synonymMap.entrySet()) {
            if (q.contains(entry.getKey())) {
                keywords.addAll(entry.getValue());
            }
        }

        return keywords;
    }

    private String inferCategory(String q) {
        if (q.contains("controller") || q.contains("接口") || q.contains("api") || q.contains("端点")) return "controller";
        if (q.contains("service") || q.contains("业务") || q.contains("逻辑")) return "service";
        if (q.contains("model") || q.contains("entity") || q.contains("dto") || q.contains("实体")) return "model";
        if (q.contains("config") || q.contains("配置")) return "config";
        if (q.contains("mapper") || q.contains("repository") || q.contains("数据库")) return "data";
        return null;
    }
}
