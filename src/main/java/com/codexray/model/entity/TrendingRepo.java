package com.codexray.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("trending_repo")
public class TrendingRepo {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String repoName;
    private String repoUrl;
    private String description;
    private String language;
    private String stars;
    private String todayStars;
    private String forks;
    private String analysisZh;
    private String analysisEn;
    private LocalDate trendDate;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }

    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getStars() { return stars; }
    public void setStars(String stars) { this.stars = stars; }

    public String getTodayStars() { return todayStars; }
    public void setTodayStars(String todayStars) { this.todayStars = todayStars; }

    public String getForks() { return forks; }
    public void setForks(String forks) { this.forks = forks; }

    public String getAnalysisZh() { return analysisZh; }
    public void setAnalysisZh(String analysisZh) { this.analysisZh = analysisZh; }

    public String getAnalysisEn() { return analysisEn; }
    public void setAnalysisEn(String analysisEn) { this.analysisEn = analysisEn; }

    public LocalDate getTrendDate() { return trendDate; }
    public void setTrendDate(LocalDate trendDate) { this.trendDate = trendDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
