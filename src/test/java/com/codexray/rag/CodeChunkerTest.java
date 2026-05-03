package com.codexray.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeChunkerTest {

    private CodeChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new CodeChunker();
    }

    @Test
    void chunkFile_smallFile_shouldCreateSingleChunk(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Hello.java");
        Files.writeString(file, """
                package com.example;

                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
                """);

        List<CodeChunker.Chunk> chunks = chunker.chunkRepo(tempDir.toString());
        assertEquals(1, chunks.size());
        assertEquals("Hello", chunks.get(0).symbolName());
        assertEquals("source", chunks.get(0).category());
        assertTrue(chunks.get(0).content().contains("Hello"));
    }

    @Test
    void chunkFile_shouldInferCategoryForController(@TempDir Path tempDir) throws IOException {
        Path dir = tempDir.resolve("src/main/java/com/example/controller");
        Files.createDirectories(dir);
        Path file = dir.resolve("UserController.java");
        Files.writeString(file, "public class UserController {}");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepo(tempDir.toString());
        assertEquals(1, chunks.size());
        assertEquals("controller", chunks.get(0).category());
    }

    @Test
    void chunkFile_shouldInferCategoryForService(@TempDir Path tempDir) throws IOException {
        Path dir = tempDir.resolve("src/main/java/com/example/service");
        Files.createDirectories(dir);
        Path file = dir.resolve("UserService.java");
        Files.writeString(file, "public class UserService {}");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepo(tempDir.toString());
        assertEquals(1, chunks.size());
        assertEquals("service", chunks.get(0).category());
    }

    @Test
    void chunkFile_shouldSkipNonCodeFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("readme.txt"), "Hello world");
        Files.writeString(tempDir.resolve("data.json"), "{}");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepo(tempDir.toString());
        assertTrue(chunks.isEmpty());
    }

    @Test
    void chunkFile_shouldSkipIgnoredDirs(@TempDir Path tempDir) throws IOException {
        Path nodeModules = tempDir.resolve("node_modules");
        Files.createDirectories(nodeModules);
        Files.writeString(nodeModules.resolve("index.js"), "console.log('test')");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepo(tempDir.toString());
        assertTrue(chunks.isEmpty());
    }

    @Test
    void chunkFile_shouldGenerateContentHash(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "public class Test {}");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepo(tempDir.toString());
        assertEquals(1, chunks.size());
        assertNotNull(chunks.get(0).contentHash());
        assertEquals(16, chunks.get(0).contentHash().length()); // 8 bytes = 16 hex chars
    }

    @Test
    void chunkFile_shouldSetLineNumbers(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Test.java");
        Files.writeString(file, "line1\nline2\nline3");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepo(tempDir.toString());
        assertEquals(1, chunks.size());
        assertEquals(1, chunks.get(0).startLine());
        assertEquals(3, chunks.get(0).endLine());
    }

    @Test
    void chunkFile_pythonShouldBeHandled(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("app.py");
        Files.writeString(file, """
                def hello():
                    print("Hello")

                def world():
                    print("World")
                """);

        List<CodeChunker.Chunk> chunks = chunker.chunkRepo(tempDir.toString());
        assertEquals(1, chunks.size());
        assertEquals("app", chunks.get(0).symbolName());
    }

    @Test
    void chunkFile_shouldHandleEmptyFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("Empty.java");
        Files.writeString(file, "");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepo(tempDir.toString());
        assertTrue(chunks.isEmpty());
    }

    @Test
    void chunkRepo_shouldChunkMultipleFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("A.java"), "public class A {}");
        Files.writeString(tempDir.resolve("B.py"), "class B: pass");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepo(tempDir.toString());
        assertEquals(2, chunks.size());
    }
}
