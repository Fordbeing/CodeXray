package com.codexray.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);
    private static final String CACHE_PREFIX = "codexray:gh:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final WebClient webClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public GitHubService(WebClient.Builder builder,
                         RedisTemplate<String, String> redisTemplate,
                         ObjectMapper objectMapper) {
        this.webClient = builder.baseUrl("https://api.github.com").build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getUserProfile(String username) {
        String cacheKey = CACHE_PREFIX + "profile:" + username;
        Map<String, Object> cached = getFromCache(cacheKey, Map.class);
        if (cached != null) return cached;

        try {
            Map<String, Object> profile = webClient.get()
                    .uri("/users/{username}", username)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));
            putToCache(cacheKey, profile);
            return profile;
        } catch (WebClientResponseException.NotFound e) {
            throw new IllegalArgumentException("GitHub 用户 \"" + username + "\" 不存在");
        } catch (WebClientResponseException.BadRequest e) {
            log.warn("GitHub API bad request for user profile: {} - {}", username, e.getMessage());
            throw new IllegalArgumentException("请求 GitHub 失败，请检查用户名是否正确");
        } catch (WebClientResponseException.Forbidden e) {
            log.warn("GitHub API rate limited for user profile: {}", username);
            throw new RuntimeException("GitHub API 请求次数已达上限，请稍后重试");
        } catch (Exception e) {
            log.error("GitHub API error for user {}: {}", username, e.getMessage());
            throw new RuntimeException("获取 GitHub 用户信息失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUserRepos(String username, String sort, int perPage) {
        String cacheKey = CACHE_PREFIX + "repos:" + username + ":" + sort + ":" + perPage;
        List<Map<String, Object>> cached = getFromCacheList(cacheKey);
        if (cached != null) return cached;

        try {
            List<Map<String, Object>> repos = webClient.get()
                    .uri("/users/{username}/repos?sort={sort}&per_page={per_page}", username, sort, perPage)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(10));
            putToCache(cacheKey, repos);
            return repos;
        } catch (WebClientResponseException.NotFound e) {
            throw new IllegalArgumentException("GitHub 用户 \"" + username + "\" 不存在");
        } catch (WebClientResponseException.BadRequest e) {
            log.warn("GitHub API bad request for repos: {} - {}", username, e.getMessage());
            throw new IllegalArgumentException("请求 GitHub 失败，请检查用户名是否正确");
        } catch (WebClientResponseException.Forbidden e) {
            log.warn("GitHub API rate limited for user repos: {}", username);
            throw new RuntimeException("GitHub API 请求次数已达上限，请稍后重试");
        } catch (Exception e) {
            log.error("GitHub API error for repos {}: {}", username, e.getMessage());
            throw new RuntimeException("获取仓库列表失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUserStarred(String username, int perPage) {
        String cacheKey = CACHE_PREFIX + "starred:" + username + ":" + perPage;
        List<Map<String, Object>> cached = getFromCacheList(cacheKey);
        if (cached != null) return cached;

        try {
            List<Map<String, Object>> starred = webClient.get()
                    .uri("/users/{username}/starred?per_page={per_page}", username, perPage)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(10));
            putToCache(cacheKey, starred);
            return starred;
        } catch (WebClientResponseException.NotFound e) {
            throw new IllegalArgumentException("GitHub 用户 \"" + username + "\" 不存在");
        } catch (WebClientResponseException.BadRequest e) {
            log.warn("GitHub API bad request for starred: {} - {}", username, e.getMessage());
            throw new IllegalArgumentException("请求 GitHub 失败，请检查用户名是否正确");
        } catch (WebClientResponseException.Forbidden e) {
            log.warn("GitHub API rate limited for user starred: {}", username);
            throw new RuntimeException("GitHub API 请求次数已达上限，请稍后重试");
        } catch (Exception e) {
            log.error("GitHub API error for starred {}: {}", username, e.getMessage());
            throw new RuntimeException("获取收藏仓库失败：" + e.getMessage());
        }
    }

    public void refreshCache(String username) {
        String pattern = CACHE_PREFIX + "*:" + username + "*";
        redisTemplate.delete(redisTemplate.keys(pattern));
        log.info("GitHub cache cleared for user: {}", username);
    }

    public Map<String, Object> getCacheInfo() {
        long totalKeys = redisTemplate.keys(CACHE_PREFIX + "*").size();
        return Map.of(
                "cachedUsers", totalKeys,
                "cacheTtlMinutes", CACHE_TTL.toMinutes(),
                "lastUpdated", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
    }

    @SuppressWarnings("unchecked")
    private <T> T getFromCache(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return null;
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            map.put("_cached", true);
            map.put("_cacheTime", redisTemplate.getExpire(key));
            return type.cast(map);
        } catch (Exception e) {
            log.debug("Cache read failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> getFromCacheList(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return null;
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.debug("Cache read failed for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void putToCache(String key, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Cache write failed for key {}: {}", key, e.getMessage());
        }
    }
}
