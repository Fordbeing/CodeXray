package com.codexray.controller;

import com.codexray.common.CurrentUser;
import com.codexray.common.Result;
import com.codexray.model.dto.ChatMessage;
import com.codexray.model.dto.ChatPending;
import com.codexray.model.dto.ChatRequest;
import com.codexray.model.dto.ChatResponse;
import com.codexray.service.CodeChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 代码问答 — 支持多轮对话、会话管理、异步轮询。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final CodeChatService codeChatService;

    public ChatController(CodeChatService codeChatService) {
        this.codeChatService = codeChatService;
    }

    /** 发送问答消息（同步） */
    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        Long userId = CurrentUser.get();
        ChatMessage msg = codeChatService.ask(
                request.sessionId(), request.repoUrl(), request.taskId(), request.question(), userId);
        return Result.ok(new ChatResponse(msg.sessionId(), msg.content(), msg.timestamp().toString()));
    }

    /** 发送问答消息（异步） — 返回 pollId */
    @PostMapping("/send")
    public Result<Map<String, String>> sendAsync(@RequestBody ChatRequest request) {
        Long userId = CurrentUser.get();
        String pollId = codeChatService.sendAsync(
                request.sessionId(), request.repoUrl(), request.taskId(), request.question(), userId);
        return Result.ok(Map.of("pollId", pollId));
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
}
