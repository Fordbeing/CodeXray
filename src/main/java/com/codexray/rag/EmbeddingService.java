package com.codexray.rag;

import com.codexray.service.SettingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 配置从 SettingService 动态读取。
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int DIMENSION = 768;

    private final SettingService settingService;
    private final ObjectMapper objectMapper;

    // 缓存 WebClient，配置变更时重建
    private WebClient cachedWebClient;
    private String cachedUrl;
    private String cachedKey;

    // 失败恢复
    private boolean apiAvailable = true;
    private long lastFailureTime = 0;
    private static final long RETRY_INTERVAL_MS = 60_000; // 1 分钟后重试

    public EmbeddingService(SettingService settingService, ObjectMapper objectMapper) {
        this.settingService = settingService;
        this.objectMapper = objectMapper;
        log.info("EmbeddingService initialized (settings-driven)");
    }

    private String getEmbeddingUrl() {
        String val = settingService.get("ai_embedding_url");
        if (val != null && !val.isBlank()) return val;
        // 没有单独配置时，使用主 API URL
        val = settingService.get("ai_base_url");
        return (val != null && !val.isBlank()) ? val : null;
    }

    private String getApiKey() {
        String val = settingService.get("ai_api_key");
        return (val != null && !val.isBlank()) ? val : null;
    }

    private String getEmbeddingModel() {
        String val = settingService.get("ai_embedding_model");
        if (val != null && !val.isBlank()) return val;
        return "text-embedding-3-small";
    }

    private synchronized WebClient getWebClient() {
        String url = getEmbeddingUrl();
        String key = getApiKey();
        if (url == null) return null;
        if (cachedWebClient != null && url.equals(cachedUrl) && (key == null ? cachedKey == null : key.equals(cachedKey))) {
            return cachedWebClient;
        }
        WebClient.Builder builder = WebClient.builder().baseUrl(url);
        if (key != null) {
            builder.defaultHeader("Authorization", "Bearer " + key);
        }
        cachedWebClient = builder
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        cachedUrl = url;
        cachedKey = key;
        log.info("Embedding WebClient rebuilt: url={}, model={}", url, getEmbeddingModel());
        return cachedWebClient;
    }

    /** 是否已配置 Embedding API */
    public boolean isConfigured() {
        return getEmbeddingUrl() != null && getApiKey() != null;
    }

    /** 当前 API 是否可用（未永久降级） */
    public boolean isApiAvailable() {
        if (!apiAvailable && System.currentTimeMillis() - lastFailureTime > RETRY_INTERVAL_MS) {
            apiAvailable = true; // 自动恢复
        }
        return apiAvailable && isConfigured();
    }

    /** 重置状态（配置变更时调用） */
    public void resetApiStatus() {
        apiAvailable = true;
        lastFailureTime = 0;
    }

    /** 测试 Embedding API 连接 */
    public String testConnection() {
        WebClient wc = getWebClient();
        if (wc == null) throw new RuntimeException("Embedding API 未配置，请先在设置中配置 AI 模型");
        float[] vec = callEmbeddingApi("test");
        return "OK (维度: " + vec.length + ", 模型: " + getEmbeddingModel() + ")";
    }

    /**
     * 对单个文本生成 embedding 向量。
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[DIMENSION];
        }
        if (text.length() > 8000) {
            text = text.substring(0, 8000);
        }

        // 检查是否可以恢复
        if (!apiAvailable && System.currentTimeMillis() - lastFailureTime > RETRY_INTERVAL_MS) {
            apiAvailable = true;
            log.info("Embedding API retrying after cooldown");
        }

        WebClient wc = getWebClient();
        if (apiAvailable && wc != null) {
            try {
                return callEmbeddingApi(text);
            } catch (Exception e) {
                log.warn("Embedding API failed, falling back to hash: {}", e.getMessage());
                apiAvailable = false;
                lastFailureTime = System.currentTimeMillis();
            }
        }

        return hashEmbed(text);
    }

    /**
     * 批量生成 embedding。
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += 20) {
            int end = Math.min(i + 20, texts.size());
            List<String> batch = texts.subList(i, end);

            if (!apiAvailable && System.currentTimeMillis() - lastFailureTime > RETRY_INTERVAL_MS) {
                apiAvailable = true;
            }

            WebClient wc = getWebClient();
            if (apiAvailable && wc != null) {
                try {
                    results.addAll(callBatchEmbeddingApi(batch));
                    continue;
                } catch (Exception e) {
                    log.warn("Batch embedding API failed: {}", e.getMessage());
                    apiAvailable = false;
                    lastFailureTime = System.currentTimeMillis();
                }
            }

            for (String text : batch) {
                results.add(hashEmbed(text));
            }
        }
        return results;
    }

    private float[] callEmbeddingApi(String text) {
        WebClient wc = getWebClient();
        String model = getEmbeddingModel();
        Map<String, Object> body = Map.of("model", model, "input", text);
        String response = wc.post()
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
        WebClient wc = getWebClient();
        String model = getEmbeddingModel();
        Map<String, Object> body = Map.of("model", model, "input", texts);
        String response = wc.post()
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
     */
    private float[] hashEmbed(String text) {
        float[] vec = new float[DIMENSION];
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes());
            for (int i = 0; i < DIMENSION; i++) {
                vec[i] = ((hash[i % hash.length] & 0xFF) / 128.0f) - 1.0f;
            }
            float norm = 0;
            for (float v : vec) norm += v * v;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < vec.length; i++) vec[i] /= norm;
            }
        } catch (Exception e) {
            for (int i = 0; i < DIMENSION; i++) {
                vec[i] = (float) (Math.random() * 2 - 1);
            }
        }
        return vec;
    }
}
