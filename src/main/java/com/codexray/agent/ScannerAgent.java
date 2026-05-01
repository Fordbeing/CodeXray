package com.codexray.agent;

import com.codexray.mapper.CodeChunkMapper;
import com.codexray.model.entity.CodeChunk;
import com.codexray.rag.CodeChunker;
import com.codexray.rag.EmbeddingService;
import com.codexray.rag.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 扫描 Agent：遍历仓库文件，语义切片，生成 Embedding，存入 ES + MySQL。
 */
@Service
public class ScannerAgent {

    private static final Logger log = LoggerFactory.getLogger(ScannerAgent.class);

    private final CodeChunker chunker;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final CodeChunkMapper codeChunkMapper;

    public ScannerAgent(CodeChunker chunker, EmbeddingService embeddingService,
                        VectorStoreService vectorStoreService, CodeChunkMapper codeChunkMapper) {
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.codeChunkMapper = codeChunkMapper;
    }

    /**
     * 扫描仓库：切片 → 嵌入 → 存储。
     */
    public ScanResult scan(String taskId, String repoPath) {
        log.info("ScannerAgent: chunking repo {}", repoPath);
        List<CodeChunker.Chunk> rawChunks = chunker.chunkRepo(repoPath);

        if (rawChunks.isEmpty()) {
            log.warn("No chunks generated for repo: {}", repoPath);
            return new ScanResult(0, 0);
        }

        // 批量生成 Embedding
        log.info("ScannerAgent: generating {} embeddings", rawChunks.size());
        List<String> texts = rawChunks.stream()
                .map(c -> c.filePath() + "\n" + c.content())
                .toList();
        List<float[]> embeddings = embeddingService.embedBatch(texts);

        // 构建 ES 文档并存储
        log.info("ScannerAgent: storing to ES");
        List<VectorStoreService.CodeChunkDoc> esDocs = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            CodeChunker.Chunk chunk = rawChunks.get(i);
            float[] emb = embeddings.get(i);
            List<Float> embList = new ArrayList<>(emb.length);
            for (float f : emb) embList.add(f);

            esDocs.add(new VectorStoreService.CodeChunkDoc(
                    taskId,
                    chunk.filePath(),
                    chunk.startLine(),
                    chunk.endLine(),
                    chunk.symbolName(),
                    chunk.category(),
                    chunk.content(),
                    embList
            ));
        }
        vectorStoreService.storeChunks(taskId, esDocs);

        // 存储到 MySQL（元数据）
        log.info("ScannerAgent: saving metadata to MySQL");
        for (int i = 0; i < rawChunks.size(); i++) {
            CodeChunker.Chunk chunk = rawChunks.get(i);
            CodeChunk entity = new CodeChunk();
            entity.setTaskId(taskId);
            entity.setFilePath(chunk.filePath());
            entity.setStartLine(chunk.startLine());
            entity.setEndLine(chunk.endLine());
            entity.setSymbolName(chunk.symbolName());
            entity.setCategory(chunk.category());
            entity.setContentHash(chunk.contentHash());
            entity.setChunkIndex(i);
            entity.setCreatedAt(LocalDateTime.now());
            codeChunkMapper.insert(entity);
        }

        // 统计
        int fileCount = (int) rawChunks.stream().map(CodeChunker.Chunk::filePath).distinct().count();
        log.info("ScannerAgent: done. {} files → {} chunks", fileCount, rawChunks.size());
        return new ScanResult(fileCount, rawChunks.size());
    }

    public record ScanResult(int fileCount, int chunkCount) {}
}
