package com.codexray.common;

/**
 * Token 数量估算工具。混合中英文/代码场景，保守估计 text.length() / 3.5。
 */
public final class TokenEstimator {

    private TokenEstimator() {}

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.length() / 3.5);
    }

    /**
     * 将结果列表按 token 预算截断，超出部分丢弃。
     * 返回截断后的列表（保持顺序）。
     */
    public static <T> java.util.List<T> truncateByTokenBudget(
            java.util.List<T> items, java.util.function.Function<T, String> contentExtractor, int maxTokens) {
        int used = 0;
        int cutIndex = items.size();
        for (int i = 0; i < items.size(); i++) {
            used += estimateTokens(contentExtractor.apply(items.get(i)));
            if (used > maxTokens) {
                cutIndex = i;
                break;
            }
        }
        return items.subList(0, cutIndex);
    }
}
