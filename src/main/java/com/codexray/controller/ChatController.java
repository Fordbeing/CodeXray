package com.codexray.controller;

import com.codexray.common.Result;
import com.codexray.model.dto.ChatMessage;
import com.codexray.model.dto.ChatRequest;
import com.codexray.model.dto.ChatResponse;
import com.codexray.service.CodeChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 代码问答 — 支持多轮对话、会话管理、流式输出。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final CodeChatService codeChatService;
    private final ObjectMapper objectMapper;
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatController(CodeChatService codeChatService, ObjectMapper objectMapper) {
        this.codeChatService = codeChatService;
        this.objectMapper = objectMapper;
    }

    /** 发送问答消息（普通） */
    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        ChatMessage msg = codeChatService.ask(
                request.sessionId(), request.repoUrl(), request.taskId(), request.question());
        return Result.ok(new ChatResponse(msg.sessionId(), msg.content(), msg.timestamp().toString()));
    }

    /** 发送问答消息（SSE 流式输出） */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(180_000L);

        virtualExecutor.execute(() -> {
            try {
                // 先创建会话获取 sessionId
                String sessionId = request.sessionId();
                boolean isNew = sessionId == null || sessionId.isBlank();

                // 获取完整回答
                ChatMessage msg = codeChatService.ask(
                        sessionId, request.repoUrl(), request.taskId(), request.question());

                // 发送会话 ID
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(objectMapper.writeValueAsString(
                                Map.of("type", "session", "sessionId", msg.sessionId()))));

                // 模拟流式输出：按字符分批发送
                String answer = msg.content();
                int batchSize = 3;
                for (int i = 0; i < answer.length(); i += batchSize) {
                    int end = Math.min(i + batchSize, answer.length());
                    String chunk = answer.substring(i, end);
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(objectMapper.writeValueAsString(
                                    Map.of("type", "token", "content", chunk))));
                    Thread.sleep(15);
                }

                emitter.send(SseEmitter.event().name("message").data("[DONE]"));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(objectMapper.writeValueAsString(
                                    Map.of("message", e.getMessage()))));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /** 获取会话历史 */
    @GetMapping("/history/{sessionId}")
    public Result<List<ChatMessage>> getHistory(@PathVariable String sessionId) {
        return Result.ok(codeChatService.getHistory(sessionId));
    }

    /** 获取所有会话列表 */
    @GetMapping("/sessions")
    public Result<List<CodeChatService.SessionInfo>> getSessions(
            @RequestParam(required = false) String repoUrl) {
        return Result.ok(codeChatService.getSessions(repoUrl));
    }

    /** 创建新会话 */
    @PostMapping("/session")
    public Result<Map<String, String>> newSession(@RequestBody(required = false) Map<String, String> body) {
        String repoUrl = body != null ? body.get("repoUrl") : null;
        String taskId = body != null ? body.get("taskId") : null;
        String sessionId = codeChatService.newSession(repoUrl, taskId);
        return Result.ok(Map.of("sessionId", sessionId));
    }

    /** 删除会话 */
    @DeleteMapping("/session/{sessionId}")
    public Result<Map<String, Boolean>> deleteSession(@PathVariable String sessionId) {
        codeChatService.deleteSession(sessionId);
        return Result.ok(Map.of("deleted", true));
    }
}
