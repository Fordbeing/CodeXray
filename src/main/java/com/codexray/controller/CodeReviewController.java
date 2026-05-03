package com.codexray.controller;

import com.codexray.agent.CodeReviewAgent;
import com.codexray.common.CurrentUser;
import com.codexray.common.Result;
import com.codexray.mapper.CodeReviewRecordMapper;
import com.codexray.model.dto.CodeReviewRequest;
import com.codexray.model.dto.CodeReviewResult;
import com.codexray.model.entity.CodeReviewRecord;
import com.codexray.rag.VectorStoreService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 代码审查端点（支持持久化）。
 */
@RestController
@RequestMapping("/api/review")
public class CodeReviewController {

    private final CodeReviewAgent codeReviewAgent;
    private final VectorStoreService vectorStoreService;
    private final CodeReviewRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    public CodeReviewController(CodeReviewAgent codeReviewAgent, VectorStoreService vectorStoreService,
                                CodeReviewRecordMapper recordMapper, ObjectMapper objectMapper) {
        this.codeReviewAgent = codeReviewAgent;
        this.vectorStoreService = vectorStoreService;
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
    }

    /** 提交 diff 进行 AI 代码审查，或从已有分析中审查文件 */
    @PostMapping
    public Result<CodeReviewResult> review(@RequestBody CodeReviewRequest request) {
        CodeReviewResult result;
        String inputType;
        String diffContent = null;
        String filePath = null;
        String sourceTaskId = null;

        // 文件审查模式
        if (request.taskId() != null && request.filePath() != null && !request.filePath().isBlank()) {
            result = codeReviewAgent.reviewFile(request.taskId(), request.filePath(), vectorStoreService);
            inputType = "file";
            filePath = request.filePath();
            sourceTaskId = request.taskId();
        } else {
            // Diff 审查模式
            String diff = request.diff();
            if (diff == null || diff.isBlank()) {
                return Result.error("diff 内容不能为空");
            }
            result = codeReviewAgent.review(diff);
            inputType = "diff";
            diffContent = diff.length() > 50000 ? diff.substring(0, 50000) : diff;
        }

        // 持久化保存
        try {
            CodeReviewRecord record = new CodeReviewRecord();
            record.setReviewId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            record.setUserId(CurrentUser.get());
            record.setInputType(inputType);
            record.setDiffContent(diffContent);
            record.setFilePath(filePath);
            record.setSourceTaskId(sourceTaskId);
            record.setResultJson(objectMapper.writeValueAsString(result));
            record.setScore(result.score());
            record.setCreatedAt(LocalDateTime.now());
            recordMapper.insert(record);
        } catch (JsonProcessingException e) {
            // 保存失败不影响返回
        }

        return Result.ok(result);
    }

    /** 获取审查历史列表 */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listRecords(@RequestParam(defaultValue = "30") int limit) {
        Long userId = CurrentUser.get();
        QueryWrapper<CodeReviewRecord> wrapper = new QueryWrapper<>();
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        wrapper.orderByDesc("created_at")
                .last("LIMIT " + Math.min(limit, 100));

        List<CodeReviewRecord> records = recordMapper.selectList(wrapper);
        List<Map<String, Object>> list = new ArrayList<>();
        for (CodeReviewRecord r : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reviewId", r.getReviewId());
            m.put("inputType", r.getInputType());
            m.put("filePath", r.getFilePath());
            m.put("sourceTaskId", r.getSourceTaskId());
            m.put("score", r.getScore());
            m.put("createdAt", r.getCreatedAt());
            // diff 预览：前 80 字符
            if (r.getDiffContent() != null) {
                String preview = r.getDiffContent().substring(0, Math.min(80, r.getDiffContent().length()));
                m.put("diffPreview", preview);
            }
            list.add(m);
        }
        return Result.ok(list);
    }

    /** 获取单条审查结果 */
    @GetMapping("/{reviewId}")
    public Result<CodeReviewResult> getRecord(@PathVariable String reviewId) {
        QueryWrapper<CodeReviewRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("review_id", reviewId);
        CodeReviewRecord record = recordMapper.selectOne(wrapper);
        if (record == null) {
            return Result.error("审查记录不存在");
        }
        try {
            CodeReviewResult result = objectMapper.readValue(record.getResultJson(), CodeReviewResult.class);
            return Result.ok(result);
        } catch (Exception e) {
            return Result.error("审查结果解析失败");
        }
    }

    /** 删除审查记录 */
    @DeleteMapping("/{reviewId}")
    public Result<Void> deleteRecord(@PathVariable String reviewId) {
        QueryWrapper<CodeReviewRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("review_id", reviewId);
        recordMapper.delete(wrapper);
        return Result.ok(null);
    }
}
