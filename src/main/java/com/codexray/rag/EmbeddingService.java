package com.codexray.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Embedding 向量生成服务。
 * 优先调用 Embedding API（OpenAI 兼容），不可用时降级为文本哈希向量。
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int DIMENSION = 768;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private boolean apiAvailable = true;

    public EmbeddingService(
            @Value("${codexray.llm.embedding-url:}") String embeddingUrl,
            @Value("${codexray.llm.api-key:}") String apiKey,
            @Value("${codexray.llm.embedding-model:text-embedding-3-small}") String model,
            ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
        this.webClient = (embeddingUrl != null && !embeddingUrl.isBlank())
                ? WebClient.builder()
                    .baseUrl(embeddingUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .build()
                : null;
    }

    /**
     * 对单个文本生成 embedding 向量。
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[DIMENSION];
        }

        // 截断过长文本
        if (text.length() > 8000) {
            text = text.substring(0, 8000);
        }

        if (apiAvailable && webClient != null) {
            try {
                return callEmbeddingApi(text);
            } catch (Exception e) {
                log.warn("Embedding API failed, falling back to hash: {}", e.getMessage());
                apiAvailable = false;
            }
        }

        return hashEmbed(text);
    }

    /**
     * 批量生成 embedding。
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        // 每批 20 条
        for (int i = 0; i < texts.size(); i += 20) {
            int end = Math.min(i + 20, texts.size());
            List<String> batch = texts.subList(i, end);

            if (apiAvailable && webClient != null) {
                try {
                    results.addAll(callBatchEmbeddingApi(batch));
                    continue;
                } catch (Exception e) {
                    log.warn("Batch embedding API failed: {}", e.getMessage());
                    apiAvailable = false;
                }
            }

            for (String text : batch) {
                results.add(hashEmbed(text));
            }
        }
        return results;
    }

    private float[] callEmbeddingApi(String text) {
        Map<String, Object> body = Map.of("model", model, "input", text);
        String response = webClient.post()
                .uri("/embeddings")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty()) {
                JsonNode embedding = data.get(0).path("embedding");
                float[] vec = new float[embedding.size()];
                for (int i = 0; i < vec.length; i++) {
                    vec[i] = (float) embedding.get(i).asDouble();
                }
                return vec;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse embedding response: " + e.getMessage(), e);
        }
        throw new RuntimeException("Invalid embedding response");
    }

    private List<float[]> callBatchEmbeddingApi(List<String> texts) {
        Map<String, Object> body = Map.of("model", model, "input", texts);
        String response = webClient.post()
                .uri("/embeddings")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            List<float[]> results = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    JsonNode embedding = item.path("embedding");
                    float[] vec = new float[embedding.size()];
                    for (int i = 0; i < vec.length; i++) {
                        vec[i] = (float) embedding.get(i).asDouble();
                    }
                    results.add(vec);
                }
            }
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse batch embedding response: " + e.getMessage(), e);
        }
    }

    /**
     * 哈希降级 embedding：确定性、可复现，但语义能力有限。
     * 用于 Embedding API 不可用时的降级方案。
     */
    private float[] hashEmbed(String text) {
        float[] vec = new float[DIMENSION];
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes());

            // 用哈希填充向量（归一化）
            ByteBuffer buf = ByteBuffer.wrap(hash);
            for (int i = 0; i < DIMENSION; i++) {
                int idx = i % hash.length;
                vec[i] = ((hash[idx] & 0xFF) / 128.0f) - 1.0f;
            }

            // 归一化
            float norm = 0;
            for (float v : vec) norm += v * v;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < vec.length; i++) vec[i] /= norm;
            }
        } catch (Exception e) {
            // 最差情况：随机填充
            for (int i = 0; i < DIMENSION; i++) {
                vec[i] = (float) (Math.random() * 2 - 1);
            }
        }
        return vec;
    }
}
