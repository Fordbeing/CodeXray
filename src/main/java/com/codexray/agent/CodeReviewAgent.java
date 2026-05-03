package com.codexray.agent;

import com.codexray.llm.LlmClient;
import com.codexray.model.dto.CodeReviewResult;
import com.codexray.rag.VectorStoreService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AI 代码审查 Agent — 逐 hunk 审查 git diff，或从已有分析中审查文件。
 */
@Service
public class CodeReviewAgent {

    private static final Logger log = LoggerFactory.getLogger(CodeReviewAgent.class);
    private static final int MAX_HUNKS = 20;
    private static final int MAX_HUNK_LENGTH = 3000;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public CodeReviewAgent(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /** 将 diff 按 diff --git 和 @@ 分割为 hunk */
    public List<DiffHunk> splitIntoHunks(String diff) {
        List<DiffHunk> hunks = new ArrayList<>();
        if (diff == null || diff.isBlank()) return hunks;

        String currentFile = "";
        String[] lines = diff.split("\n");
        StringBuilder currentContent = new StringBuilder();
        String currentHeader = "";
        int startLine = 0;

        for (String line : lines) {
            if (line.startsWith("diff --git")) {
                // 保存前一个 hunk
                if (!currentContent.isEmpty()) {
                    hunks.add(new DiffHunk(currentFile, currentHeader, currentContent.toString(), startLine));
                    currentContent = new StringBuilder();
                }
                // 提取文件名
                int idx = line.indexOf(" b/");
                currentFile = idx > 0 ? line.substring(idx + 3) : line;
                currentHeader = line;
            } else if (line.startsWith("@@")) {
                // 新 hunk 开始
                if (!currentContent.isEmpty()) {
                    hunks.add(new DiffHunk(currentFile, currentHeader, currentContent.toString(), startLine));
                    currentContent = new StringBuilder();
                }
                currentHeader = line;
                startLine = parseStartLine(line);
            } else {
                currentContent.append(line).append("\n");
            }
        }
        // 最后一个 hunk
        if (!currentContent.isEmpty()) {
            hunks.add(new DiffHunk(currentFile, currentHeader, currentContent.toString(), startLine));
        }
        return hunks;
    }

    private int parseStartLine(String header) {
        try {
            // @@ -oldStart,oldCount +newStart,newCount @@
            int plus = header.indexOf('+');
            if (plus < 0) return 0;
            String sub = header.substring(plus + 1);
            int comma = sub.indexOf(',');
            return Integer.parseInt(comma > 0 ? sub.substring(0, comma) : sub.split("\\s")[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 审查完整 diff：拆分 hunk 后逐个审查，合并结果 */
    public CodeReviewResult review(String diff) {
        if (diff == null || diff.isBlank()) {
            return new CodeReviewResult(List.of(), "空 diff，无需审查。", 10, List.of());
        }

        List<DiffHunk> hunks = splitIntoHunks(diff);
        if (hunks.isEmpty()) {
            return new CodeReviewResult(List.of(), "无法解析 diff 内容。", 5, List.of());
        }

        // 限制 hunk 数量
        List<DiffHunk> reviewHunks = hunks.size() > MAX_HUNKS
                ? hunks.subList(0, MAX_HUNKS)
                : hunks;

        List<CodeReviewResult.ReviewComment> allComments = new ArrayList<>();
        List<CodeReviewResult.HunkResult> hunkResults = new ArrayList<>();
        int totalScore = 0;

        for (DiffHunk hunk : reviewHunks) {
            String truncatedContent = hunk.content.length() > MAX_HUNK_LENGTH
                    ? hunk.content.substring(0, MAX_HUNK_LENGTH) + "\n..."
                    : hunk.content;

            String reviewInput = hunk.header + "\n" + truncatedContent;
            SingleHunkResult result = reviewSingleHunk(hunk.file, reviewInput);
            allComments.addAll(result.comments);
            hunkResults.add(new CodeReviewResult.HunkResult(hunk.file, hunk.header, result.summary, result.score, result.comments));
            totalScore += result.score;
        }

        int avgScore = reviewHunks.isEmpty() ? 7 : Math.round((float) totalScore / reviewHunks.size());
        String overallSummary = generateOverallSummary(hunkResults, avgScore);
        if (hunks.size() > MAX_HUNKS) {
            overallSummary += " (已审查前 " + MAX_HUNKS + "/" + hunks.size() + " 个 hunk)";
        }

        return new CodeReviewResult(allComments, overallSummary, avgScore, hunkResults);
    }

    /** 从已有分析任务中审查文件内容 */
    public CodeReviewResult reviewFile(String taskId, String filePath, VectorStoreService vectorStoreService) {
        List<VectorStoreService.CodeChunkDoc> chunks = vectorStoreService.searchByCategory(taskId, null, 5000);
        String content = chunks.stream()
                .filter(c -> filePath.equals(c.file_path()))
                .sorted(Comparator.comparingInt(VectorStoreService.CodeChunkDoc::start_line))
                .map(VectorStoreService.CodeChunkDoc::content)
                .collect(java.util.stream.Collectors.joining("\n"));

        if (content.isBlank()) {
            return new CodeReviewResult(List.of(), "文件未找到: " + filePath, 0, List.of());
        }

        // 构造 synthetic diff
        String syntheticDiff = "diff --git a/" + filePath + " b/" + filePath + "\n"
                + "new file mode 100644\n"
                + "--- /dev/null\n"
                + "+++ b/" + filePath + "\n"
                + "@@ -0,0 +1," + content.split("\n").length + " @@\n";

        for (String line : content.split("\n")) {
            syntheticDiff += "+" + line + "\n";
        }

        return review(syntheticDiff);
    }

    private SingleHunkResult reviewSingleHunk(String file, String diffContent) {
        String systemPrompt = """
                你是一个资深代码审查员。请审查以下代码 diff hunk。

                审查维度:
                1. 正确性 — 是否有 bug、逻辑错误
                2. 安全性 — 是否存在注入、越权等安全问题
                3. 性能 — 是否有性能隐患
                4. 可读性 — 命名、结构是否清晰
                5. 最佳实践 — 是否遵循语言/框架最佳实践

                返回 JSON 格式:
                {
                  "comments": [
                    {"file": "文件路径", "line": 行号, "severity": "error|warning|info", "message": "审查意见"}
                  ],
                  "summary": "这个 hunk 的评价（1-2 句）",
                  "score": <1-10>
                }

                只返回 JSON，不要包含其他内容。
                """;

        try {
            String response = llmClient.chatWithContext(systemPrompt, List.of(),
                    "审查文件 " + file + " 的以下 diff:\n\n```diff\n" + diffContent + "\n```");

            String json = response.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            }

            JsonNode root = objectMapper.readTree(json);

            List<CodeReviewResult.ReviewComment> comments = new ArrayList<>();
            JsonNode commentsNode = root.path("comments");
            if (commentsNode.isArray()) {
                for (JsonNode node : commentsNode) {
                    comments.add(new CodeReviewResult.ReviewComment(
                            node.path("file").asText(file),
                            node.path("line").asInt(0),
                            node.path("severity").asText("info"),
                            node.path("message").asText("")
                    ));
                }
            }

            String summary = root.path("summary").asText("审查完成。");
            int score = root.path("score").asInt(7);

            return new SingleHunkResult(comments, summary, score);
        } catch (Exception e) {
            log.error("Hunk review failed for {}: {}", file, e.getMessage());
            return new SingleHunkResult(List.of(), "审查失败: " + e.getMessage(), 5);
        }
    }

    private String generateOverallSummary(List<CodeReviewResult.HunkResult> hunkResults, int avgScore) {
        if (hunkResults.isEmpty()) return "无变更内容。";
        long errors = hunkResults.stream()
                .flatMap(h -> h.comments().stream())
                .filter(c -> "error".equals(c.severity()))
                .count();
        long warnings = hunkResults.stream()
                .flatMap(h -> h.comments().stream())
                .filter(c -> "warning".equals(c.severity()))
                .count();

        StringBuilder sb = new StringBuilder();
        sb.append("审查 ").append(hunkResults.size()).append(" 个代码块，平均评分 ").append(avgScore).append("/10。");
        if (errors > 0) sb.append(" 发现 ").append(errors).append(" 个错误。");
        if (warnings > 0) sb.append(" ").append(warnings).append(" 个警告。");
        if (errors == 0 && warnings == 0) sb.append(" 代码质量良好。");
        return sb.toString();
    }

    public record DiffHunk(String file, String header, String content, int startLine) {}
    private record SingleHunkResult(List<CodeReviewResult.ReviewComment> comments, String summary, int score) {}
}
