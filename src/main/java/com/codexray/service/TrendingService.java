package com.codexray.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codexray.llm.LlmClient;
import com.codexray.mapper.TrendingRepoMapper;
import com.codexray.model.dto.TrendingRepoResponse;
import com.codexray.model.entity.TrendingRepo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final ExecutorService analysisExecutor = Executors.newFixedThreadPool(3);

    public TrendingService(TrendingRepoMapper trendingRepoMapper, LlmClient llmClient,
                           RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.trendingRepoMapper = trendingRepoMapper;
        this.llmClient = llmClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 抓取 GitHub Trending 并保存（含 LLM 分析），然后更新缓存。
     */
    public List<TrendingRepoResponse> scrapeAndSave() {
        List<TrendingRepo> repos = scrapeTrending();
        LocalDate today = LocalDate.now();

        // 删除今天旧数据
        trendingRepoMapper.delete(
                new QueryWrapper<TrendingRepo>().eq("trend_date", today)
        );

        // 并发调用 LLM 分析
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

        // 保存新数据
        for (TrendingRepo repo : repos) {
            repo.setTrendDate(today);
            repo.setCreatedAt(LocalDateTime.now());
            trendingRepoMapper.insert(repo);
        }

        log.info("Scraped and saved {} trending repos for {}", repos.size(), today);

        // 更新缓存
        List<TrendingRepoResponse> zhResponses = toResponses(repos, "zh");
        List<TrendingRepoResponse> enResponses = toResponses(repos, "en");
        putCache(today, "zh", zhResponses);
        putCache(today, "en", enResponses);

        return zhResponses;
    }

    /**
     * 异步刷新：后台抓取+分析，完成后再更新缓存。立即返回当前数据。
     */
    public List<TrendingRepoResponse> refreshAsync(String lang) {
        // 先获取当前数据返回
        List<TrendingRepoResponse> current = loadFromDb(LocalDate.now(), lang);

        // 后台执行刷新
        analysisExecutor.submit(() -> {
            try {
                scrapeAndSave();
                log.info("Async trending refresh completed");
            } catch (Exception e) {
                log.error("Async trending refresh failed", e);
            }
        });

        return current;
    }

    /**
     * 查询指定日期的热门仓库（带 Redis 缓存）。
     */
    public List<TrendingRepoResponse> getTrending(LocalDate date, String lang) {
        String cacheKey = CACHE_PREFIX + date + ":" + lang;

        // 1. Redis 缓存
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            try {
                List<TrendingRepoResponse> result = objectMapper.readValue(cached, LIST_TYPE);
                if (!result.isEmpty()) {
                    log.debug("Redis cache hit: {}", cacheKey);
                    return result;
                }
            } catch (Exception e) {
                log.warn("Failed to deserialize Redis cache: {}", cacheKey, e);
            }
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
     * 查询今日热门（带缓存），无数据时自动抓取。
     */
    public List<TrendingRepoResponse> getTodayTrending(String lang) {
        LocalDate today = LocalDate.now();
        List<TrendingRepoResponse> results = getTrending(today, lang);
        if (!results.isEmpty()) {
            return results;
        }
        scrapeAndSave();
        return getTrending(today, lang);
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
