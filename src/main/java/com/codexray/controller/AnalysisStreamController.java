package com.codexray.controller;

import com.codexray.service.AnalysisEventService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 分析进度 SSE 推送端点。
 */
@RestController
@RequestMapping("/api/analysis")
public class AnalysisStreamController {

    private final AnalysisEventService eventService;

    public AnalysisStreamController(AnalysisEventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping(value = "/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String taskId) {
        return eventService.subscribe(taskId);
    }
}
