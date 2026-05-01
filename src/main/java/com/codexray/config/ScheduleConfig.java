package com.codexray.config;

import com.codexray.model.dto.TrendingRepoResponse;
import com.codexray.service.EmailService;
import com.codexray.service.TrendingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

@Configuration
@EnableScheduling
public class ScheduleConfig {

    private static final Logger log = LoggerFactory.getLogger(ScheduleConfig.class);

    private final TrendingService trendingService;
    private final EmailService emailService;

    public ScheduleConfig(TrendingService trendingService, EmailService emailService) {
        this.trendingService = trendingService;
        this.emailService = emailService;
    }

    /**
     * 每天早上 8 点抓取 GitHub Trending 并推送邮件。
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void dailyTrendingAndPush() {
        log.info("Starting daily GitHub Trending scrape...");
        try {
            List<TrendingRepoResponse> repos = trendingService.scrapeAndSave();
            log.info("Daily Trending scrape completed, {} repos", repos.size());

            // 推送邮件
            if (!repos.isEmpty()) {
                List<TrendingRepoResponse> reposEn = trendingService.getTodayTrending("en");
                int sent = emailService.sendTrendingToAll(repos, reposEn);
                log.info("Trending email pushed to {} subscribers", sent);
            }
        } catch (Exception e) {
            log.error("Daily Trending task failed", e);
        }
    }
}
