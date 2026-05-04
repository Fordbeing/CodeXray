package com.codexray.controller;

import com.codexray.common.CurrentUser;
import com.codexray.common.Result;
import com.codexray.model.dto.ChatMessage;
import com.codexray.model.dto.ChatPending;
import com.codexray.model.dto.ChatRequest;
import com.codexray.model.dto.ChatResponse;
import com.codexray.model.dto.CrossRepoChatRequest;
import com.codexray.service.CodeChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 代码问答 — 支持多轮对话、会话管理、异步轮询。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final CodeChatService codeChatService;
    private final ExecutorService sseExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public ChatController(CodeChatService codeChatService) {
        this.codeChatService = codeChatService;
    }

    /** 发送问答消息（同步） */
    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        Long userId = CurrentUser.get();
        ChatMessage msg = codeChatService.ask(
                request.sessionId(), request.repoUrl(), request.taskId(), request.question(), userId);
        List<String> suggestions = codeChatService.generateFollowUps(request.question(), msg.content());
        return Result.ok(new ChatResponse(msg.sessionId(), msg.content(), msg.timestamp().toString(), suggestions));
    }

    /** 发送问答消息（异步） — 返回 pollId */
    @PostMapping("/send")
    public Result<Map<String, String>> sendAsync(@RequestBody ChatRequest request) {
        Long userId = CurrentUser.get();
        String pollId = codeChatService.sendAsync(
                request.sessionId(), request.repoUrl(), request.taskId(), request.question(), userId);
        return Result.ok(Map.of("pollId", pollId));
    }

    /** SSE 流式问答 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam String question,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String repoUrl,
            @RequestParam(required = false) String taskId) {
        Long userId = CurrentUser.get();
        SseEmitter emitter = new SseEmitter(300_000L);
        sseExecutor.execute(() -> {
            try {
                codeChatService.askStreaming(sessionId, repoUrl, taskId, question, userId, emitter);
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /** 轮询异步结果 */
    @GetMapping("/result/{pollId}")
    public Result<ChatPending> pollResult(@PathVariable String pollId) {
        ChatPending result = codeChatService.pollResult(pollId);
        if (result == null) {
            return Result.error("任务不存在");
        }
        return Result.ok(result);
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
        Long userId = CurrentUser.get();
        return Result.ok(codeChatService.getSessions(repoUrl, userId));
    }

    /** 创建新会话 */
    @PostMapping("/session")
    public Result<Map<String, String>> newSession(@RequestBody(required = false) Map<String, String> body) {
        String repoUrl = body != null ? body.get("repoUrl") : null;
        String taskId = body != null ? body.get("taskId") : null;
        Long userId = CurrentUser.get();
        String sessionId = codeChatService.newSession(repoUrl, taskId, userId);
        return Result.ok(Map.of("sessionId", sessionId));
    }

    /** 删除会话 */
    @DeleteMapping("/session/{sessionId}")
    public Result<Map<String, Boolean>> deleteSession(@PathVariable String sessionId) {
        codeChatService.deleteSession(sessionId);
        return Result.ok(Map.of("deleted", true));
    }

    /** 导出会话为 Markdown */
    @GetMapping("/session/{sessionId}/export")
    public Result<String> exportSession(@PathVariable String sessionId,
                                        @RequestParam(defaultValue = "md") String format) {
        return Result.ok(codeChatService.exportAsMarkdown(sessionId));
    }

    /** 跨仓库 SSE 流式问答 */
    @GetMapping(value = "/cross-repo/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter crossRepoStream(
            @RequestParam String question,
            @RequestParam(required = false) String taskIds) {
        Long userId = CurrentUser.get();
        List<String> taskIdList = (taskIds != null && !taskIds.isBlank())
                ? List.of(taskIds.split(","))
                : List.of();
        SseEmitter emitter = new SseEmitter(300_000L);
        sseExecutor.execute(() -> {
            try {
                codeChatService.chatAcrossReposStreaming(userId, question, taskIdList, emitter);
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
