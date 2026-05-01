package com.codexray.rag;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 上下文组装器：将检索到的代码切片组装为 LLM prompt。
 */
@Service
public class ContextBuilder {

    private static final int MAX_CONTEXT_CHARS = 15000;

    /**
     * 组装 RAG 上下文。
     */
    public String build(String question, List<VectorStoreService.CodeChunkDoc> chunks,
                        String techStack) {
        StringBuilder sb = new StringBuilder();

        // 项目概述
        if (techStack != null && !techStack.isBlank()) {
            sb.append("## 项目信息\n");
            sb.append("技术栈: ").append(techStack).append("\n\n");
        }

        // 相关代码
        sb.append("## 相关代码\n\n");

        int totalChars = 0;
        Set<String> seenFiles = new HashSet<>();
        int rank = 1;

        for (VectorStoreService.CodeChunkDoc chunk : chunks) {
            if (totalChars >= MAX_CONTEXT_CHARS) break;

            StringBuilder chunkSb = new StringBuilder();
            chunkSb.append("### ").append(rank++).append(". ");
            chunkSb.append(chunk.file_path());
            chunkSb.append(" (行 ").append(chunk.start_line()).append("-").append(chunk.end_line()).append(")");
            if (chunk.symbol_name() != null) {
                chunkSb.append(" — ").append(chunk.symbol_name());
            }
            chunkSb.append("\n");
            if (chunk.category() != null) {
                chunkSb.append("模块: ").append(chunk.category()).append("\n");
            }
            chunkSb.append("```\n");
            chunkSb.append(truncateContent(chunk.content(), 3000));
            chunkSb.append("\n```\n\n");

            String chunkText = chunkSb.toString();
            if (totalChars + chunkText.length() > MAX_CONTEXT_CHARS) break;

            sb.append(chunkText);
            totalChars += chunkText.length();
            seenFiles.add(chunk.file_path());
        }

        return sb.toString();
    }

    /**
     * 组装系统 prompt。
     */
    public String buildSystemPrompt(String techStack) {
        return "你是一个专业的代码分析助手。根据提供的代码上下文，准确回答用户的问题。\n\n"
                + "回答要求:\n"
                + "1. 引用具体的文件路径和行号\n"
                + "2. 如果代码中有相关信息，直接引用代码片段\n"
                + "3. 如果代码中没有相关信息，明确说明\n"
                + "4. 回答简洁专业，不讲废话";
    }

    private String truncateContent(String content, int maxChars) {
        if (content == null) return "";
        if (content.length() <= maxChars) return content;
        return content.substring(0, maxChars) + "\n... (truncated)";
    }
}
