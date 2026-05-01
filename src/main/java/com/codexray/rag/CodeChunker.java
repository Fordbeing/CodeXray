package com.codexray.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;

/**
 * 代码语义切片器。按函数/类边界切分代码文件。
 */
@Service
public class CodeChunker {

    private static final Logger log = LoggerFactory.getLogger(CodeChunker.class);

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "dist", ".idea", ".vscode",
            "__pycache__", ".gradle", ".mvn", "vendor", "venv", ".venv"
    );

    private static final Set<String> CODE_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".ts", ".go", ".rs", ".kt", ".scala",
            ".c", ".cpp", ".h", ".hpp", ".cs", ".rb", ".php", ".swift",
            ".vue", ".jsx", ".tsx", ".sql", ".sh", ".yaml", ".yml",
            ".xml", ".properties", ".toml", ".tf"
    );

    private static final Set<String> CONFIG_EXTENSIONS = Set.of(
            ".yml", ".yaml", ".properties", ".toml", ".xml", ".json", ".cfg", ".ini"
    );

    private static final int MAX_CHUNK_LINES = 300;
    private static final int MIN_CHUNK_LINES = 20;

    public record Chunk(
            String filePath,
            int startLine,
            int endLine,
            String symbolName,
            String category,
            String content,
            String contentHash
    ) {}

    /**
     * 切分整个仓库，返回所有代码切片。
     */
    public List<Chunk> chunkRepo(String repoPath) {
        Path root = Path.of(repoPath);
        List<Chunk> allChunks = new ArrayList<>();
        int chunkIndex = 0;

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (SKIP_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString().toLowerCase();
                    int dotIdx = name.lastIndexOf('.');
                    String ext = dotIdx >= 0 ? name.substring(dotIdx) : "";

                    if (!CODE_EXTENSIONS.contains(ext)) {
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        String content = Files.readString(file);
                        if (content.isBlank()) return FileVisitResult.CONTINUE;

                        String relativePath = root.relativize(file).toString();
                        String category = inferCategory(relativePath);
                        List<Chunk> fileChunks = chunkFile(relativePath, content, category);
                        allChunks.addAll(fileChunks);
                    } catch (Exception e) {
                        log.warn("Failed to chunk file: {}", file, e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.error("Failed to walk repo: {}", repoPath, e);
        }

        log.info("Chunked repo {} into {} chunks", repoPath, allChunks.size());
        return allChunks;
    }

    /**
     * 单文件切片：按类/函数边界或固定行数切分。
     */
    private List<Chunk> chunkFile(String filePath, String content, String category) {
        String[] lines = content.split("\n", -1);
        List<Chunk> chunks = new ArrayList<>();

        if (lines.length <= MAX_CHUNK_LINES) {
            // 小文件：整体作为一个 chunk
            chunks.add(new Chunk(filePath, 1, lines.length, extractSymbolName(filePath, content),
                    category, content, hash(content)));
            return chunks;
        }

        // 大文件：按类/函数边界切分
        List<int[]> boundaries = detectBoundaries(lines, filePath);

        if (boundaries.isEmpty()) {
            // 无边界可切，按固定行数切分
            for (int i = 0; i < lines.length; i += MAX_CHUNK_LINES) {
                int end = Math.min(i + MAX_CHUNK_LINES, lines.length);
                String chunkContent = String.join("\n", Arrays.copyOfRange(lines, i, end));
                chunks.add(new Chunk(filePath, i + 1, end, null, category, chunkContent, hash(chunkContent)));
            }
        } else {
            // 按边界切分
            int prevEnd = 0;
            for (int[] boundary : boundaries) {
                int start = boundary[0];
                int end = boundary[1];
                // 加入前一个边界到当前边界之间的内容
                if (start > prevEnd + 1 && (start - prevEnd) >= MIN_CHUNK_LINES) {
                    String between = String.join("\n", Arrays.copyOfRange(lines, prevEnd, start));
                    if (!between.isBlank()) {
                        chunks.add(new Chunk(filePath, prevEnd + 1, start, null, category, between, hash(between)));
                    }
                }
                String chunkContent = String.join("\n", Arrays.copyOfRange(lines, start, end));
                String symbol = extractSymbolFromLines(lines, start, end);
                chunks.add(new Chunk(filePath, start + 1, end, symbol, category, chunkContent, hash(chunkContent)));
                prevEnd = end;
            }
            // 剩余内容
            if (prevEnd < lines.length) {
                String remaining = String.join("\n", Arrays.copyOfRange(lines, prevEnd, lines.length));
                if (!remaining.isBlank() && remaining.length() > 50) {
                    chunks.add(new Chunk(filePath, prevEnd + 1, lines.length, null, category, remaining, hash(remaining)));
                }
            }
        }

        return chunks;
    }

    /**
     * 检测代码边界（类定义、函数定义等）。
     * 简化实现：按空行分组 + 识别函数/类声明。
     */
    private List<int[]> detectBoundaries(String[] lines, String filePath) {
        List<int[]> boundaries = new ArrayList<>();
        String ext = filePath.substring(filePath.lastIndexOf('.'));
        boolean isJavaLike = Set.of(".java", ".kt", ".scala", ".cs", ".ts", ".js", ".go", ".rs", ".swift", ".php", ".cpp", ".c").contains(ext);

        if (!isJavaLike) {
            return boundaries; // 非类 C 语言，按空行或固定行数切
        }

        int braceCount = 0;
        int blockStart = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            // 检测类/函数/方法声明
            boolean isDeclaration = line.contains("class ") || line.contains("interface ")
                    || line.contains("enum ") || line.contains("def ")
                    || (line.contains("(") && (line.contains("public ") || line.contains("private ")
                    || line.contains("protected ") || line.contains("fun ")
                    || line.contains("func ") || line.contains("fn ")));

            if (isDeclaration && blockStart < 0 && braceCount == 0) {
                blockStart = i;
            }

            for (char c : line.toCharArray()) {
                if (c == '{') braceCount++;
                if (c == '}') braceCount--;
            }

            if (blockStart >= 0 && braceCount == 0 && i > blockStart) {
                boundaries.add(new int[]{blockStart, i + 1});
                blockStart = -1;
            }
        }

        return boundaries;
    }

    private String extractSymbolName(String filePath, String content) {
        String name = filePath.substring(filePath.lastIndexOf('/') + 1);
        int dotIdx = name.lastIndexOf('.');
        return dotIdx > 0 ? name.substring(0, dotIdx) : name;
    }

    private String extractSymbolFromLines(String[] lines, int start, int end) {
        for (int i = start; i < Math.min(start + 5, end); i++) {
            String line = lines[i].trim();
            if (line.contains("class ") || line.contains("interface ")
                    || line.contains("enum ") || line.contains("def ")
                    || line.contains("func ") || line.contains("fn ")) {
                // 提取类/函数名
                int braceIdx = line.indexOf('{');
                String decl = braceIdx > 0 ? line.substring(0, braceIdx).trim() : line;
                if (decl.length() > 100) decl = decl.substring(0, 100);
                return decl;
            }
        }
        return null;
    }

    private String inferCategory(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.contains("/controller/") || lower.contains("/controllers/")) return "controller";
        if (lower.contains("/service/") || lower.contains("/services/")) return "service";
        if (lower.contains("/model/") || lower.contains("/entity/") || lower.contains("/dto/")) return "model";
        if (lower.contains("/config/") || lower.contains("/configuration/")) return "config";
        if (lower.contains("/mapper/") || lower.contains("/repository/")) return "data";
        if (lower.contains("/test/") || lower.contains("test_") || lower.contains("_test.")) return "test";
        if (lower.contains("/util/") || lower.contains("/helper/") || lower.contains("/common/")) return "util";
        return "source";
    }

    private String hash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }
}
