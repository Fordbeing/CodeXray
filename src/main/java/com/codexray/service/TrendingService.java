package com.codexray.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codexray.llm.LlmClient;
import com.codexray.mapper.TrendingRepoMapper;
import com.codexray.model.dto.TrendingRepoResponse;
import com.codexray.model.dto.WeeklyTrendingRepoResponse;
import com.codexray.model.entity.TrendingRepo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class TrendingService {

    private static final Logger log = LoggerFactory.getLogger(TrendingService.class);
    private static final String TRENDING_URL = "https://github.com/trending";
    private static final String CACHE_PREFIX = "trending:";
    private static final long CACHE_TTL_HOURS = 6;
    private static final TypeReference<List<TrendingRepoResponse>> LIST_TYPE = new TypeReference<>() {};

    private final TrendingRepoMapper trendingRepoMapper;
    private final LlmClient llmClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor analysisExecutor;

    public TrendingService(TrendingRepoMapper trendingRepoMapper, LlmClient llmClient,
                           RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper,
                           @Qualifier("trendingExecutor") ThreadPoolTaskExecutor analysisExecutor) {
        this.trendingRepoMapper = trendingRepoMapper;
        this.llmClient = llmClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.analysisExecutor = analysisExecutor;
    }

    /**
     * 抓取 GitHub Trending 并保存到 DB（不含 LLM 分析），立即返回数据。
     * LLM 分析在后台异步执行，完成后更新 DB 和缓存。
     */
    public List<TrendingRepoResponse> scrapeAndSave() {
        List<TrendingRepo> repos = scrapeTrending();
        LocalDate today = LocalDate.now();

        // 删除今天旧数据
        trendingRepoMapper.delete(
                new QueryWrapper<TrendingRepo>().eq("trend_date", today)
        );

        // 保存新数据（不含 LLM 分析）
        for (TrendingRepo repo : repos) {
            repo.setTrendDate(today);
            repo.setCreatedAt(LocalDateTime.now());
            trendingRepoMapper.insert(repo);
        }

        log.info("Scraped and saved {} trending repos for {}", repos.size(), today);

        // 立即更新缓存（不含分析）
        List<TrendingRepoResponse> zhResponses = toResponses(repos, "zh");
        List<TrendingRepoResponse> enResponses = toResponses(repos, "en");
        putCache(today, "zh", zhResponses);
        putCache(today, "en", enResponses);

        // 后台异步执行 LLM 分析，完成后更新 DB 和缓存
        List<TrendingRepo> reposForAnalysis = repos;
        analysisExecutor.submit(() -> {
            try {
                analyzeAndUpdate(reposForAnalysis, today);
            } catch (Exception e) {
                log.error("Async LLM analysis failed", e);
            }
        });

        return zhResponses;
    }

    /**
     * 后台 LLM 分析：并发分析每个仓库，完成后更新 DB 和缓存。
     */
    private void analyzeAndUpdate(List<TrendingRepo> repos, LocalDate date) {
        List<Future<?>> futures = new ArrayList<>();
        for (TrendingRepo repo : repos) {
            futures.add(analysisExecutor.submit(() -> {
                try {
                    String zh = llmClient.analyzeTrendingRepo(repo.getRepoName(), repo.getDescription(), "zh");
                    repo.setAnalysisZh(zh);
                    String en = llmClient.analyzeTrendingRepo(repo.getRepoName(), repo.getDescription(), "en");
                    repo.setAnalysisEn(en);
                } catch (Exception e) {
                    log.warn("LLM analysis failed for {}: {}", repo.getRepoName(), e.getMessage());
                }
            }));
        }
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception e) { log.warn("Analysis task failed", e); }
        }

        // 更新 DB（已有 id，用 updateById 而非 delete+insert）
        for (TrendingRepo repo : repos) {
            trendingRepoMapper.updateById(repo);
        }

        // 更新缓存（含分析）
        putCache(date, "zh", toResponses(repos, "zh"));
        putCache(date, "en", toResponses(repos, "en"));
        log.info("Async LLM analysis completed for {} repos", repos.size());
    }

    /**
     * 同步刷新：抓取+保存，立即返回新数据。LLM 分析后台执行。
     */
    public List<TrendingRepoResponse> refreshAsync(String lang) {
        return scrapeAndSave();
    }

    /**
     * 查询指定日期的热门仓库（带 Redis 缓存）。
     */
    public List<TrendingRepoResponse> getTrending(LocalDate date, String lang) {
        String cacheKey = CACHE_PREFIX + date + ":" + lang;

        // 1. Redis 缓存
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                List<TrendingRepoResponse> result = objectMapper.readValue(cached, LIST_TYPE);
                if (!result.isEmpty()) {
                    log.debug("Redis cache hit: {}", cacheKey);
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed, falling back to DB: {}", cacheKey, e);
        }

        // 2. 数据库
        List<TrendingRepoResponse> results = loadFromDb(date, lang);

        // 3. 缓存回填
        if (!results.isEmpty()) {
            putCache(date, lang, results);
        }

        return results;
    }

    /**
     * 查询今日热门（带缓存），无数据时同步抓取（阻塞等待，约 15s）。
     * LLM 分析在后台异步执行。
     */
    public List<TrendingRepoResponse> getTodayTrending(String lang) {
        LocalDate today = LocalDate.now();
        List<TrendingRepoResponse> results = getTrending(today, lang);
        if (!results.isEmpty()) {
            return results;
        }
        // 无数据时同步抓取并保存（约 15s），LLM 分析在后台执行
        return scrapeAndSave();
    }

    /**
     * 查询近 7 天周榜：按仓库聚合，统计上榜天数、累计今日星标、最新数据。
     */
    public List<WeeklyTrendingRepoResponse> getWeeklyTrending(String lang) {
        String cacheKey = CACHE_PREFIX + "weekly:" + lang;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, new TypeReference<>() {});
            }
        } catch (Exception e) {
            log.warn("Redis weekly cache read failed: {}", cacheKey, e);
        }

        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);
        boolean isZh = !"en".equals(lang);

        List<TrendingRepo> repos = trendingRepoMapper.selectList(
                new QueryWrapper<TrendingRepo>()
                        .ge("trend_date", weekAgo)
                        .le("trend_date", today)
                        .orderByAsc("trend_date")
        );

        // 按 repoName 聚合
        LinkedHashMap<String, List<TrendingRepo>> grouped = new LinkedHashMap<>();
        for (TrendingRepo repo : repos) {
            grouped.computeIfAbsent(repo.getRepoName(), k -> new ArrayList<>()).add(repo);
        }

        List<WeeklyTrendingRepoResponse> results = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            List<TrendingRepo> list = entry.getValue();
            // 取最新一条作为基础数据
            TrendingRepo latest = list.get(list.size() - 1);
            int totalToday = 0;
            for (TrendingRepo r : list) {
                if (r.getTodayStars() != null && !r.getTodayStars().isBlank()) {
                    try { totalToday += Integer.parseInt(r.getTodayStars()); } catch (NumberFormatException ignored) {}
                }
            }
            results.add(new WeeklyTrendingRepoResponse(
                    latest.getRepoName(),
                    latest.getRepoUrl(),
                    latest.getDescription(),
                    latest.getLanguage(),
                    latest.getStars(),
                    latest.getForks(),
                    isZh ? latest.getAnalysisZh() : latest.getAnalysisEn(),
                    list.size(),
                    totalToday > 0 ? String.valueOf(totalToday) : null,
                    latest.getTrendDate()
            ));
        }

        // 按上榜天数降序，同天数按最新 stars 降序
        results.sort((a, b) -> {
            int cmp = Integer.compare(b.daysCount(), a.daysCount());
            if (cmp != 0) return cmp;
            long sa = 0, sb = 0;
            try { sa = Long.parseLong(a.stars() != null ? a.stars().replaceAll("[^0-9]", "") : "0"); } catch (Exception ignored) {}
            try { sb = Long.parseLong(b.stars() != null ? b.stars().replaceAll("[^0-9]", "") : "0"); } catch (Exception ignored) {}
            return Long.compare(sb, sa);
        });

        // 缓存 1 小时
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(results), 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache weekly trending: {}", cacheKey, e);
        }

        return results;
    }

    // ========== 内部方法 ==========

    private List<TrendingRepoResponse> loadFromDb(LocalDate date, String lang) {
        List<TrendingRepo> repos = trendingRepoMapper.selectList(
                new QueryWrapper<TrendingRepo>().eq("trend_date", date)
                        .orderByAsc("id")
        );
        return toResponses(repos, lang);
    }

    private void putCache(LocalDate date, String lang, List<TrendingRepoResponse> data) {
        String key = CACHE_PREFIX + date + ":" + lang;
        long ttl = date.equals(LocalDate.now())
                ? CACHE_TTL_HOURS
                : Math.max(ChronoUnit.HOURS.between(LocalDateTime.now(), date.plusDays(1).atStartOfDay()) + 24, 1);
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json, ttl, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache trending data: {}", key, e);
        }
    }

    private List<TrendingRepo> scrapeTrending() {
        List<TrendingRepo> repos = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(TRENDING_URL)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .timeout(15_000)
                    .get();

            Elements articles = doc.select("article.Box-row");
            for (Element article : articles) {
                TrendingRepo repo = new TrendingRepo();

                Element nameLink = article.selectFirst("h2 a");
                if (nameLink == null) continue;
                String repoPath = nameLink.attr("href").trim().replaceFirst("^/", "");
                repo.setRepoName(repoPath);
                repo.setRepoUrl("https://github.com/" + repoPath);

                Element desc = article.selectFirst("p");
                repo.setDescription(desc != null ? desc.text().trim() : "");

                Element lang = article.selectFirst("[itemprop=programmingLanguage]");
                repo.setLanguage(lang != null ? lang.text().trim() : "");

                Elements starLinks = article.select("a.Link--muted");
                for (Element link : starLinks) {
                    String href = link.attr("href");
                    if (href.contains("/stargazers")) {
                        repo.setStars(link.text().trim().replace(",", ""));
                        break;
                    }
                }

                Elements forkLinks = article.select("a.Link--muted.d-inline-block");
                for (Element link : forkLinks) {
                    String href = link.attr("href");
                    if (href.contains("/forks")) {
                        repo.setForks(link.text().trim().replace(",", ""));
                        break;
                    }
                }

                Element todayStar = article.selectFirst("span.d-inline-block.float-sm-right");
                if (todayStar != null) {
                    String text = todayStar.text().trim();
                    repo.setTodayStars(text.replaceAll("[^0-9]", ""));
                }

                repos.add(repo);
            }
        } catch (Exception e) {
            log.error("Failed to scrape GitHub Trending", e);
            throw new RuntimeException("GitHub Trending scraping failed: " + e.getMessage(), e);
        }
        return repos;
    }

    private List<TrendingRepoResponse> toResponses(List<TrendingRepo> repos, String lang) {
        boolean isZh = !"en".equals(lang);
        return repos.stream()
                .map(r -> new TrendingRepoResponse(
                        r.getRepoName(), r.getRepoUrl(), r.getDescription(),
                        r.getLanguage(), r.getStars(), r.getTodayStars(),
                        r.getForks(),
                        isZh ? r.getAnalysisZh() : r.getAnalysisEn(),
                        r.getTrendDate()))
                .toList();
    }
}
