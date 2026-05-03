package com.codexray.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("comparison_record")
public class ComparisonRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String comparisonId;
    private Long userId;
    private String taskA;
    private String taskB;
    private String repoUrl;
    private String resultJson;
    private Integer scoreDiff;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getComparisonId() { return comparisonId; }
    public void setComparisonId(String comparisonId) { this.comparisonId = comparisonId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTaskA() { return taskA; }
    public void setTaskA(String taskA) { this.taskA = taskA; }

    public String getTaskB() { return taskB; }
    public void setTaskB(String taskB) { this.taskB = taskB; }

    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public Integer getScoreDiff() { return scoreDiff; }
    public void setScoreDiff(Integer scoreDiff) { this.scoreDiff = scoreDiff; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
