package com.codexray.controller;

import com.codexray.common.Result;
import com.codexray.model.dto.ChatRequest;
import com.codexray.model.dto.ChatResponse;
import com.codexray.model.entity.ChatHistory;
import com.codexray.service.CodeChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final CodeChatService codeChatService;

    public ChatController(CodeChatService codeChatService) {
        this.codeChatService = codeChatService;
    }

    @PostMapping
    public Result<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String[] result = codeChatService.ask(request.repoUrl(), request.question(), request.sessionId());
        return Result.ok(new ChatResponse(result[0], request.repoUrl(), result[1]));
    }

    @GetMapping("/history/{sessionId}")
    public Result<List<ChatHistory>> getHistory(@PathVariable String sessionId) {
        return Result.ok(codeChatService.getHistory(sessionId));
    }

    @GetMapping("/sessions")
    public Result<List<CodeChatService.ChatSessionInfo>> listSessions(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(codeChatService.listSessions(limit));
    }
}
