package com.codexray.controller;

import com.codexray.common.Result;
import com.codexray.model.dto.TrendingRepoResponse;
import com.codexray.model.dto.WeeklyTrendingRepoResponse;
import com.codexray.service.EmailService;
import com.codexray.service.TrendingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/trending")
public class TrendingController {

    private final TrendingService trendingService;
    private final EmailService emailService;

    public TrendingController(TrendingService trendingService, EmailService emailService) {
        this.trendingService = trendingService;
        this.emailService = emailService;
    }

    @GetMapping("/today")
    public Result<List<TrendingRepoResponse>> today(
            @RequestParam(defaultValue = "zh") String lang) {
        return Result.ok(trendingService.getTodayTrending(lang));
    }

    @GetMapping("/weekly")
    public Result<List<WeeklyTrendingRepoResponse>> weekly(
            @RequestParam(defaultValue = "zh") String lang) {
        return Result.ok(trendingService.getWeeklyTrending(lang));
    }

    @GetMapping
    public Result<List<TrendingRepoResponse>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "zh") String lang) {
        return Result.ok(trendingService.getTrending(date, lang));
    }

    @PostMapping("/refresh")
    public Result<List<TrendingRepoResponse>> refresh(
            @RequestParam(defaultValue = "zh") String lang) {
        return Result.ok(trendingService.refreshAsync(lang));
    }

    @PostMapping("/send-digest")
    public Result<String> sendDigest() {
        List<TrendingRepoResponse> reposZh = trendingService.getTodayTrending("zh");
        if (reposZh.isEmpty()) {
            reposZh = trendingService.scrapeAndSave();
        }
        List<TrendingRepoResponse> reposEn = trendingService.getTodayTrending("en");
        List<WeeklyTrendingRepoResponse> weeklyZh = trendingService.getWeeklyTrending("zh");
        List<WeeklyTrendingRepoResponse> weeklyEn = trendingService.getWeeklyTrending("en");
        int sent = emailService.sendTrendingToAll(reposZh, reposEn, weeklyZh, weeklyEn);
        return Result.ok("已发送给 " + sent + " 位订阅者");
    }
}
