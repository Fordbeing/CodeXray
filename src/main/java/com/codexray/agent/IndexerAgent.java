package com.codexray.agent;

import com.codexray.common.Constants;
import com.codexray.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * 索引 Agent：识别技术栈、推断架构模式、构建项目指纹。
 */
@Service
public class IndexerAgent {

    private static final Logger log = LoggerFactory.getLogger(IndexerAgent.class);

    private static final Set<String> CONFIG_FILES = Set.of(
            "pom.xml", "build.gradle", "build.gradle.kts", "package.json",
            "requirements.txt", "go.mod", "cargo.toml", "pyproject.toml",
            "dockerfile", "docker-compose.yml", "docker-compose.yaml",
            "readme.md", "readme.rst", "readme.txt"
    );

    private final LlmClient llmClient;

    public IndexerAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public ProjectProfile index(String repoPath) {
        Path root = Path.of(repoPath);

        // 收集关键配置文件内容
        Map<String, String> configs = new LinkedHashMap<>();
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
                    if (CONFIG_FILES.contains(name) && attrs.size() < 50_000) {
                        try {
                            String relative = root.relativize(file).toString();
                            configs.put(relative, Files.readString(file));
                        } catch (IOException ignored) {}
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed to collect config files: {}", e.getMessage());
        }

        // 统计目录结构
        Map<String, Integer> dirStats = new LinkedHashMap<>();
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                    .filter(d -> !Constants.SKIP_DIRS.contains(d.getFileName().toString()))
                    .forEach(d -> dirStats.put(d.getFileName().toString(), 0));
        } catch (IOException ignored) {}

        // 调用 LLM 识别技术栈
        String techStack = identifyTechStack(configs);

        return new ProjectProfile(techStack, configs.keySet().stream().toList(), dirStats, configs);
    }

    private String identifyTechStack(Map<String, String> configs) {
        if (configs.isEmpty()) return "Unknown";

        StringBuilder configContent = new StringBuilder();
        configs.forEach((path, content) -> {
            configContent.append("=== ").append(path).append(" ===\n");
            // 截断过长内容
            String c = content.length() > 2000 ? content.substring(0, 2000) : content;
            configContent.append(c).append("\n\n");
        });

        try {
            String systemPrompt = "你是一个技术栈识别专家。根据项目配置文件准确识别技术栈和架构模式。" +
                    "要求：列出具体的技术名称和版本（如果可见），不要遗漏关键技术。";
            String prompt = "根据以下项目配置文件，用一句话总结技术栈和架构模式。\n"
                    + "例如：'Spring Boot 3.3 + MyBatis-Plus + MySQL 8, MVC 分层架构'\n"
                    + "只输出总结，不要解释。\n\n"
                    + configContent;
            return llmClient.chat(systemPrompt, prompt);
        } catch (Exception e) {
            log.warn("Tech stack identification failed: {}", e.getMessage());
            return "Unknown";
        }
    }

    public record ProjectProfile(
            String techStack,
            List<String> configFiles,
            Map<String, Integer> topLevelDirs,
            Map<String, String> configContents
    ) {}
}
