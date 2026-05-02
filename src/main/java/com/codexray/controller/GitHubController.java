package com.codexray.controller;

import com.codexray.common.Result;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private static final Logger log = LoggerFactory.getLogger(GitHubController.class);
    private static final String CACHE_PREFIX = "codexray:gh:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final WebClient webClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public GitHubController(WebClient.Builder builder,
                            RedisTemplate<String, String> redisTemplate,
                            ObjectMapper objectMapper) {
        this.webClient = builder.baseUrl("https://api.github.com").build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/users/{username}")
    public Result<Map<String, Object>> getUserProfile(@PathVariable String username) {
        String cacheKey = CACHE_PREFIX + "profile:" + username;

        // 尝试缓存
        Map<String, Object> cached = getFromCache(cacheKey, Map.class);
        if (cached != null) {
            return Result.ok(cached);
        }

        try {
            Map<String, Object> profile = webClient.get()
                    .uri("/users/{username}", username)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));
            putToCache(cacheKey, profile);
            return Result.ok(profile);
        } catch (WebClientResponseException.NotFound e) {
            return Result.error("GitHub 用户 \"" + username + "\" 不存在");
        } catch (WebClientResponseException.Forbidden e) {
            log.warn("GitHub API rate limited for user profile: {}", username);
            return Result.error("GitHub API 请求次数已达上限，请稍后重试");
        } catch (Exception e) {
            log.error("GitHub API error for user {}: {}", username, e.getMessage());
            return Result.error("获取 GitHub 用户信息失败：" + e.getMessage());
        }
    }

    @GetMapping("/users/{username}/repos")
    public Result<List<Map<String, Object>>> getUserRepos(
            @PathVariable String username,
            @RequestParam(defaultValue = "updated") String sort,
            @RequestParam(defaultValue = "10") int per_page) {
        String cacheKey = CACHE_PREFIX + "repos:" + username + ":" + sort + ":" + per_page;

        List<Map<String, Object>> cached = getFromCacheList(cacheKey);
        if (cached != null) {
            return Result.ok(cached);
        }

        try {
            List repos = webClient.get()
                    .uri("/users/{username}/repos?sort={sort}&per_page={per_page}", username, sort, per_page)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(10));
            putToCache(cacheKey, repos);
            return Result.ok(repos);
        } catch (WebClientResponseException.NotFound e) {
            return Result.error("GitHub 用户 \"" + username + "\" 不存在");
        } catch (WebClientResponseException.Forbidden e) {
            log.warn("GitHub API rate limited for user repos: {}", username);
            return Result.error("GitHub API 请求次数已达上限，请稍后重试");
        } catch (Exception e) {
            log.error("GitHub API error for repos {}: {}", username, e.getMessage());
            return Result.error("获取仓库列表失败：" + e.getMessage());
        }
    }

    @GetMapping("/users/{username}/starred")
    public Result<List<Map<String, Object>>> getUserStarred(
            @PathVariable String username,
            @RequestParam(defaultValue = "10") int per_page) {
        String cacheKey = CACHE_PREFIX + "starred:" + username + ":" + per_page;

        List<Map<String, Object>> cached = getFromCacheList(cacheKey);
        if (cached != null) {
            return Result.ok(cached);
        }

        try {
            List starred = webClient.get()
                    .uri("/users/{username}/starred?per_page={per_page}", username, per_page)
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(10));
            putToCache(cacheKey, starred);
            return Result.ok(starred);
        } catch (WebClientResponseException.NotFound e) {
            return Result.error("GitHub 用户 \"" + username + "\" 不存在");
        } catch (WebClientResponseException.Forbidden e) {
            log.warn("GitHub API rate limited for user starred: {}", username);
            return Result.error("GitHub API 请求次数已达上限，请稍后重试");
        } catch (Exception e) {
            log.error("GitHub API error for starred {}: {}", username, e.getMessage());
            return Result.error("获取收藏仓库失败：" + e.getMessage());
        }
    }

    /** 手动刷新缓存：清除指定用户的 GitHub 缓存 */
    @PostMapping("/users/{username}/refresh")
    public Result<Void> refreshCache(@PathVariable String username) {
        String pattern = CACHE_PREFIX + "*:" + username + "*";
        redisTemplate.delete(redisTemplate.keys(pattern));
        log.info("GitHub cache cleared for user: {}", username);
        return Result.ok(null);
    }

    /** 获取缓存状态信息 */
    @GetMapping("/cache-info")
    public Result<Map<String, Object>> cacheInfo() {
        long totalKeys = redisTemplate.keys(CACHE_PREFIX + "*").size();
        return Result.ok(Map.of(
                "cachedUsers", totalKeys,
                "cacheTtlMinutes", CACHE_TTL.toMinutes(),
                "lastUpdated", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        ));
    }

    // ========== 缓存工具方法 ==========

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
