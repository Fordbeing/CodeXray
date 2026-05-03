package com.codexray.service;

import com.codexray.common.Constants;
import com.codexray.model.dto.RepoPreviewResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CodeReaderService {

    private static final Logger log = LoggerFactory.getLogger(CodeReaderService.class);

    private static final Set<String> KEY_CONFIG_FILES = Set.of(
            "readme", "pom.xml", "build.gradle", "build.gradle.kts",
            "package.json", "requirements.txt", "go.mod", "cargo.toml",
            "makefile", "dockerfile", "docker-compose.yml", "docker-compose.yaml",
            ".gitignore", "license", "setup.py", "pyproject.toml"
    );

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".ts", ".go", ".rs", ".kt", ".scala",
            ".c", ".cpp", ".h", ".hpp", ".cs", ".rb", ".php", ".swift",
            ".vue", ".jsx", ".tsx", ".sql", ".sh", ".yaml", ".yml", ".xml",
            ".properties", ".toml", ".cfg", ".ini", ".tf", ".hcl"
    );

    private static final int MAX_FILE_SIZE = 10_000;
    private static final int MAX_TOTAL_CHARS = 80_000;
    private static final int MAX_SOURCE_FILES = 30;

    /**
     * 读取仓库代码内容，返回结构化的代码摘要供 LLM 分析。
     */
    public String readRepo(String repoPath) {
        Path root = Path.of(repoPath);
        if (!Files.isDirectory(root)) {
            return "Repository path not found: " + repoPath;
        }

        StringBuilder sb = new StringBuilder();
        int totalChars = 0;

        // 1. 收集目录树
        String tree = buildFileTree(root);
        sb.append("=== PROJECT STRUCTURE (top 3 levels) ===\n");
        sb.append(tree).append("\n\n");
        totalChars += tree.length();

        // 2. 读取关键配置文件
        sb.append("=== KEY CONFIGURATION FILES ===\n");
        List<Path> configFiles = collectConfigFiles(root);
        for (Path file : configFiles) {
            if (totalChars >= MAX_TOTAL_CHARS) break;
            String content = readFileSafe(file, MAX_FILE_SIZE);
            String relative = root.relativize(file).toString();
            String section = "--- " + relative + " ---\n" + content + "\n\n";
            sb.append(section);
            totalChars += section.length();
        }

        // 3. 采样源代码文件
        sb.append("=== SOURCE CODE SAMPLES ===\n");
        List<Path> sourceFiles = collectSourceFiles(root);
        int count = 0;
        for (Path file : sourceFiles) {
            if (totalChars >= MAX_TOTAL_CHARS || count >= MAX_SOURCE_FILES) break;
            String content = readFileSafe(file, MAX_FILE_SIZE);
            String relative = root.relativize(file).toString();
            String section = "--- " + relative + " ---\n" + content + "\n\n";
            sb.append(section);
            totalChars += section.length();
            count++;
        }

        log.info("Read {} chars from {} config + {} source files in {}",
                totalChars, configFiles.size(), Math.min(count, MAX_SOURCE_FILES), repoPath);

        return sb.toString();
    }

    /**
     * 构建 3 层深度的目录树。
     */
    private String buildFileTree(Path root) {
        StringBuilder tree = new StringBuilder();
        try {
            buildTreeRecursive(root, root.getFileName().toString(), tree, 0, 3);
        } catch (IOException e) {
            tree.append("(failed to build tree: ").append(e.getMessage()).append(")");
        }
        return tree.toString();
    }

    private void buildTreeRecursive(Path dir, String name, StringBuilder tree, int depth, int maxDepth) throws IOException {
        if (depth > maxDepth) return;

        String indent = "  ".repeat(depth);
        tree.append(indent).append(name).append("/\n");

        List<Path> entries = Files.list(dir)
                .filter(p -> !Constants.SKIP_DIRS.contains(p.getFileName().toString()))
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .limit(50)
                .toList();

        for (Path entry : entries) {
            if (Files.isDirectory(entry)) {
                buildTreeRecursive(entry, entry.getFileName().toString(), tree, depth + 1, maxDepth);
            } else {
                tree.append(indent).append("  ").append(entry.getFileName()).append("\n");
            }
        }
    }

    /**
     * 收集关键配置文件（README、pom.xml 等）。
     */
    private List<Path> collectConfigFiles(Path root) {
        List<Path> result = new ArrayList<>();
        try {
            Files.list(root)
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return KEY_CONFIG_FILES.contains(name) || name.startsWith("readme");
                    })
                    .sorted()
                    .forEach(result::add);

            // 也检查 src/main/resources 下的配置
            Path resources = root.resolve("src/main/resources");
            if (Files.isDirectory(resources)) {
                Files.walk(resources, 2)
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.endsWith(".yml") || name.endsWith(".yaml")
                                    || name.endsWith(".properties") || name.endsWith(".xml");
                        })
                        .limit(5)
                        .forEach(result::add);
            }
        } catch (IOException e) {
            log.warn("Failed to collect config files: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 收集源代码文件，按文件大小排序（小文件优先，更可能是关键文件）。
     */
    private List<Path> collectSourceFiles(Path root) {
        List<Path> result = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (Constants.SKIP_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = file.getFileName().toString().toLowerCase();
                    int dotIdx = name.lastIndexOf('.');
                    String ext = dotIdx >= 0 ? name.substring(dotIdx) : "";
                    if (SOURCE_EXTENSIONS.contains(ext) && attrs.size() <= MAX_FILE_SIZE) {
                        result.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed to collect source files: {}", e.getMessage());
        }

        // 按文件大小排序，小文件优先
        result.sort(Comparator.comparingLong(p -> {
            try { return Files.size(p); } catch (IOException e) { return Long.MAX_VALUE; }
        }));

        return result;
    }

    private String readFileSafe(Path file, int maxChars) {
        try {
            String content = Files.readString(file);
            if (content.length() > maxChars) {
                return content.substring(0, maxChars) + "\n... (truncated)";
            }
            return content;
        } catch (IOException e) {
            return "(failed to read: " + e.getMessage() + ")";
        }
    }

    /**
     * 读取指定文件（用于 RAG 问答时精确定位文件内容）。
     */
    public String readFile(String repoPath, String relativePath) {
        Path file = Path.of(repoPath, relativePath);
        if (!Files.isRegularFile(file)) {
            return "File not found: " + relativePath;
        }
        return readFileSafe(file, MAX_FILE_SIZE);
    }

    /**
     * 快速预览仓库：统计语言分布、文件数等（不调用 LLM）。
     */
    public RepoPreviewResponse preview(String repoUrl, String repoPath) {
        Path root = Path.of(repoPath);
        Map<String, Integer> langStats = new LinkedHashMap<>();
        List<String> topLevelFiles = new ArrayList<>();
        List<String> keyConfigFiles = new ArrayList<>();
        AtomicInteger totalFiles = new AtomicInteger();
        AtomicInteger totalSourceFiles = new AtomicInteger();

        try {
            // 顶层文件
            try (var stream = Files.list(root)) {
                stream.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .sorted()
                        .forEach(topLevelFiles::add);
            }

            // 遍历统计
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (Constants.SKIP_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    totalFiles.incrementAndGet();
                    String name = file.getFileName().toString().toLowerCase();
                    int dotIdx = name.lastIndexOf('.');
                    String ext = dotIdx >= 0 ? name.substring(dotIdx) : "";

                    if (SOURCE_EXTENSIONS.contains(ext)) {
                        totalSourceFiles.incrementAndGet();
                        String lang = extToLang(ext);
                        langStats.merge(lang, 1, Integer::sum);
                    }
                    if (KEY_CONFIG_FILES.contains(name) || name.startsWith("readme")) {
                        keyConfigFiles.add(root.relativize(file).toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed to preview repo: {}", e.getMessage());
        }

        // 按数量排序语言
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(langStats.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        Map<String, Integer> orderedStats = new LinkedHashMap<>();
        sorted.forEach(e -> orderedStats.put(e.getKey(), e.getValue()));

        String primaryLang = sorted.isEmpty() ? "Unknown" : sorted.get(0).getKey();

        return new RepoPreviewResponse(
                repoUrl, totalFiles.get(), totalSourceFiles.get(),
                primaryLang, orderedStats,
                topLevelFiles, keyConfigFiles
        );
    }

    private String extToLang(String ext) {
        return switch (ext) {
            case ".java" -> "Java";
            case ".py" -> "Python";
            case ".js" -> "JavaScript";
            case ".ts", ".tsx" -> "TypeScript";
            case ".go" -> "Go";
            case ".rs" -> "Rust";
            case ".kt" -> "Kotlin";
            case ".c", ".h" -> "C";
            case ".cpp", ".hpp" -> "C++";
            case ".cs" -> "C#";
            case ".rb" -> "Ruby";
            case ".php" -> "PHP";
            case ".swift" -> "Swift";
            case ".vue" -> "Vue";
            case ".jsx" -> "React";
            case ".sql" -> "SQL";
            case ".sh" -> "Shell";
            case ".yaml", ".yml" -> "YAML";
            case ".xml" -> "XML";
            case ".tf", ".hcl" -> "Terraform";
            case ".scala" -> "Scala";
            default -> ext.isEmpty() ? "Other" : ext.substring(1);
        };
    }
}
