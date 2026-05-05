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
    private final SettingService settingService;

    public GitHubService(WebClient.Builder builder,
                         RedisTemplate<String, String> redisTemplate,
                         ObjectMapper objectMapper,
                         SettingService settingService) {
        this.webClient = builder.baseUrl("https://api.github.com").build();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.settingService = settingService;
    }

    private WebClient.RequestHeadersSpec<?> githubRequest(String uri, Object... vars) {
        String token = settingService.get("github_token");
        var spec = webClient.get().uri(uri, vars)
                .header("Accept", "application/vnd.github.v3+json");
        if (token != null && !token.isBlank()) {
            spec.header("Authorization", "Bearer " + token);
        }
        return spec;
    }

    public Map<String, Object> getUserProfile(String username) {
        String cacheKey = CACHE_PREFIX + "profile:" + username;
        Map<String, Object> cached = getFromCache(cacheKey, Map.class);
        if (cached != null) return cached;

        try {
            Map<String, Object> profile = githubRequest("/users/{username}", username)
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
            List<Map<String, Object>> repos = githubRequest(
                    "/users/{username}/repos?sort={sort}&per_page={per_page}", username, sort, perPage)
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
            List<Map<String, Object>> starred = githubRequest(
                    "/users/{username}/starred?per_page={per_page}", username, perPage)
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

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUserEvents(String username, int perPage) {
        String cacheKey = CACHE_PREFIX + "events:" + username + ":" + perPage;
        List<Map<String, Object>> cached = getFromCacheList(cacheKey);
        if (cached != null) return cached;

        try {
            List<Map<String, Object>> events = githubRequest(
                    "/users/{username}/events?per_page={per_page}", username, perPage)
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(10));
            putToCache(cacheKey, events);
            return events;
        } catch (WebClientResponseException.NotFound e) {
            throw new IllegalArgumentException("GitHub 用户 \"" + username + "\" 不存在");
        } catch (WebClientResponseException.Forbidden e) {
            log.warn("GitHub API rate limited for user events: {}", username);
            throw new RuntimeException("GitHub API 请求次数已达上限，请稍后重试");
        } catch (Exception e) {
            log.error("GitHub API error for events {}: {}", username, e.getMessage());
            throw new RuntimeException("获取用户动态失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUserOrgs(String username) {
        String cacheKey = CACHE_PREFIX + "orgs:" + username;
        List<Map<String, Object>> cached = getFromCacheList(cacheKey);
        if (cached != null) return cached;

        try {
            List<Map<String, Object>> orgs = githubRequest("/users/{username}/orgs", username)
                    .retrieve()
                    .bodyToMono(List.class)
                    .block(Duration.ofSeconds(10));
            putToCache(cacheKey, orgs);
            return orgs;
        } catch (WebClientResponseException.NotFound e) {
            // Not an error — many users aren't in any org
            List<Map<String, Object>> empty = List.of();
            putToCache(cacheKey, empty);
            return empty;
        } catch (WebClientResponseException.Forbidden e) {
            log.warn("GitHub API rate limited for user orgs: {}", username);
            throw new RuntimeException("GitHub API 请求次数已达上限，请稍后重试");
        } catch (Exception e) {
            log.error("GitHub API error for orgs {}: {}", username, e.getMessage());
            throw new RuntimeException("获取组织信息失败：" + e.getMessage());
        }
    }

    /** Aggregated stats across all user repos (cached separately, shorter TTL) */
    public Map<String, Object> getUserRepoStats(String username) {
        String cacheKey = CACHE_PREFIX + "stats:" + username;
        Map<String, Object> cached = getFromCache(cacheKey, Map.class);
        if (cached != null) return cached;

        // Fetch all repos (up to 100) to compute aggregate stats
        List<Map<String, Object>> repos = getUserRepos(username, "pushed", 100);

        int totalStars = 0, totalForks = 0, totalIssues = 0;
        long totalSize = 0;
        Map<String, Integer> langCounts = new java.util.LinkedHashMap<>();
        Map<String, Long> langBytes = new java.util.LinkedHashMap<>();
        String oldestRepo = null;
        String earliestDate = null;
        String biggestRepo = null;
        long biggestSize = 0;
        int originalCount = 0;
        int hasLicense = 0;
        int hasHomepage = 0;
        int hasTopics = 0;

        for (Map<String, Object> r : repos) {
            totalStars += ((Number) r.getOrDefault("stargazers_count", 0)).intValue();
            totalForks += ((Number) r.getOrDefault("forks_count", 0)).intValue();
            totalIssues += ((Number) r.getOrDefault("open_issues_count", 0)).intValue();
            int size = ((Number) r.getOrDefault("size", 0)).intValue();
            totalSize += size;

            String lang = (String) r.get("language");
            if (lang != null) {
                langCounts.merge(lang, 1, Integer::sum);
                langBytes.merge(lang, (long) size, Long::sum);
            }

            String created = (String) r.get("created_at");
            if (created != null && (earliestDate == null || created.compareTo(earliestDate) < 0)) {
                earliestDate = created;
                oldestRepo = (String) r.get("full_name");
            }

            if (size > biggestSize) {
                biggestSize = size;
                biggestRepo = (String) r.get("full_name");
            }

            if (Boolean.TRUE.equals(r.get("fork"))) { } else { originalCount++; }
            if (r.get("license") != null) hasLicense++;
            if (r.get("homepage") != null && !((String) r.get("homepage")).isBlank()) hasHomepage++;
            if (r.get("topics") instanceof List && !((List<?>) r.get("topics")).isEmpty()) hasTopics++;
        }

        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("totalStars", totalStars);
        stats.put("totalForks", totalForks);
        stats.put("totalIssues", totalIssues);
        stats.put("totalSize", totalSize);
        stats.put("totalRepos", repos.size());
        stats.put("originalRepos", originalCount);
        stats.put("forkedRepos", repos.size() - originalCount);
        stats.put("hasLicense", hasLicense);
        stats.put("hasHomepage", hasHomepage);
        stats.put("hasTopics", hasTopics);
        stats.put("oldestRepo", oldestRepo);
        stats.put("earliestDate", earliestDate);
        stats.put("biggestRepo", biggestRepo);
        stats.put("biggestSize", biggestSize);
        stats.put("langCounts", langCounts);
        stats.put("langBytes", langBytes);

        // Calculate top languages by bytes
        List<Map<String, Object>> topLanguages = langBytes.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> Map.<String, Object>of("name", e.getKey(), "bytes", e.getValue(),
                        "repos", langCounts.getOrDefault(e.getKey(), 0)))
                .toList();
        stats.put("topLanguages", topLanguages);

        putToCache(cacheKey, stats);
        return stats;
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
