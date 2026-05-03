package com.codexray.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis 会话存储 — L2 缓存层。
 * Session history 存储为 JSON，TTL = 7 天。
 * Session meta 存储为独立 key，按 userId 建索引。
 */
@Service
public class RedisSessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionStore.class);
    private static final Duration SESSION_TTL = Duration.ofDays(7);
    private static final String HISTORY_PREFIX = "chat:history:";
    private static final String META_PREFIX = "chat:meta:";
    private static final String USER_SESSIONS_PREFIX = "chat:user_sessions:";

    private final RedisTemplate<String, String> redis;
    private final ObjectMapper objectMapper;

    public RedisSessionStore(RedisTemplate<String, String> redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    // --- Session History ---

    public void putHistory(String sessionId, List<Map<String, String>> history) {
        try {
            String json = objectMapper.writeValueAsString(history);
            redis.opsForValue().set(HISTORY_PREFIX + sessionId, json, SESSION_TTL);
        } catch (Exception e) {
            log.warn("Failed to write session history to Redis: {}", e.getMessage());
        }
    }

    public List<Map<String, String>> getHistory(String sessionId) {
        try {
            String json = redis.opsForValue().get(HISTORY_PREFIX + sessionId);
            if (json == null) return null;
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to read session history from Redis: {}", e.getMessage());
            return null;
        }
    }

    public void deleteHistory(String sessionId) {
        redis.delete(HISTORY_PREFIX + sessionId);
    }

    // --- Session Meta ---

    public void putMeta(String sessionId, CodeChatService.SessionInfo meta) {
        try {
            String json = objectMapper.writeValueAsString(meta);
            redis.opsForValue().set(META_PREFIX + sessionId, json, SESSION_TTL);
            // 添加到用户的 session 集合
            if (meta.userId() != null) {
                redis.opsForSet().add(USER_SESSIONS_PREFIX + meta.userId(), sessionId);
                redis.expire(USER_SESSIONS_PREFIX + meta.userId(), SESSION_TTL);
            }
        } catch (Exception e) {
            log.warn("Failed to write session meta to Redis: {}", e.getMessage());
        }
    }

    public CodeChatService.SessionInfo getMeta(String sessionId) {
        try {
            String json = redis.opsForValue().get(META_PREFIX + sessionId);
            if (json == null) return null;
            return objectMapper.readValue(json, CodeChatService.SessionInfo.class);
        } catch (Exception e) {
            log.warn("Failed to read session meta from Redis: {}", e.getMessage());
            return null;
        }
    }

    public List<CodeChatService.SessionInfo> getAllMeta(Long userId) {
        try {
            Set<String> sessionIds = redis.opsForSet().members(USER_SESSIONS_PREFIX + userId);
            if (sessionIds == null || sessionIds.isEmpty()) return List.of();

            List<String> keys = sessionIds.stream().map(id -> META_PREFIX + id).collect(Collectors.toList());
            List<String> jsons = redis.opsForValue().multiGet(keys);
            if (jsons == null) return List.of();

            List<CodeChatService.SessionInfo> result = new ArrayList<>();
            for (String json : jsons) {
                if (json != null) {
                    result.add(objectMapper.readValue(json, CodeChatService.SessionInfo.class));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to read all session metas from Redis: {}", e.getMessage());
            return List.of();
        }
    }

    public void deleteMeta(String sessionId, Long userId) {
        redis.delete(META_PREFIX + sessionId);
        if (userId != null) {
            redis.opsForSet().remove(USER_SESSIONS_PREFIX + userId, sessionId);
        }
    }
}
