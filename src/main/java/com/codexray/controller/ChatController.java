package com.codexray.controller;

import com.codexray.common.Result;
import com.codexray.model.dto.ChatMessage;
import com.codexray.model.dto.ChatRequest;
import com.codexray.model.dto.ChatResponse;
import com.codexray.service.CodeChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 代码问答 — 支持多轮对话、会话管理。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final CodeChatService codeChatService;

    public ChatController(CodeChatService codeChatService) {
        this.codeChatService = codeChatService;
    }

    /** 发送问答消息 */
    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        ChatMessage msg = codeChatService.ask(
                request.sessionId(), request.repoUrl(), request.taskId(), request.question());
        return Result.ok(new ChatResponse(msg.sessionId(), msg.content(), msg.timestamp().toString()));
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
