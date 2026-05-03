package com.codexray.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * Embedding 向量 Redis 缓存。Key = emb:{SHA-256(text)}，TTL = 7 天。
 */
@Service
public class EmbeddingCacheService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingCacheService.class);
    private static final String KEY_PREFIX = "emb:";
    private static final Duration TTL = Duration.ofDays(7);
    private static final int DIMENSION = 768;

    private final RedisTemplate<String, String> redis;

    public EmbeddingCacheService(RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    public String hashText(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }

    public float[] get(String textHash) {
        try {
            String val = redis.opsForValue().get(KEY_PREFIX + textHash);
            if (val == null || val.isBlank()) return null;
            return parseVector(val);
        } catch (Exception e) {
            log.debug("Cache read failed for {}: {}", textHash, e.getMessage());
            return null;
        }
    }

    public void put(String textHash, float[] vector) {
        try {
            String val = serializeVector(vector);
            redis.opsForValue().set(KEY_PREFIX + textHash, val, TTL);
        } catch (Exception e) {
            log.debug("Cache write failed for {}: {}", textHash, e.getMessage());
        }
    }

    private String serializeVector(float[] vec) {
        StringBuilder sb = new StringBuilder(vec.length * 8);
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(vec[i]));
        }
        return sb.toString();
    }

    private float[] parseVector(String val) {
        String[] parts = val.split(",");
        float[] vec = new float[DIMENSION];
        for (int i = 0; i < Math.min(parts.length, DIMENSION); i++) {
            vec[i] = Float.parseFloat(parts[i].trim());
        }
        return vec;
    }
}
