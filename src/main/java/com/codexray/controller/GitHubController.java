package com.codexray.controller;

import com.codexray.common.Result;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final WebClient webClient;

    public GitHubController(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("https://api.github.com").build();
    }

    @GetMapping("/users/{username}")
    public Result<Map<String, Object>> getUserProfile(@PathVariable String username) {
        Map<String, Object> profile = webClient.get()
                .uri("/users/{username}", username)
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        return Result.ok(profile);
    }

    @GetMapping("/users/{username}/repos")
    public Result<List<Map<String, Object>>> getUserRepos(
            @PathVariable String username,
            @RequestParam(defaultValue = "updated") String sort,
            @RequestParam(defaultValue = "10") int per_page) {
        List repos = webClient.get()
                .uri("/users/{username}/repos?sort={sort}&per_page={per_page}", username, sort, per_page)
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve()
                .bodyToMono(List.class)
                .block();
        return Result.ok(repos);
    }

    @GetMapping("/users/{username}/starred")
    public Result<List<Map<String, Object>>> getUserStarred(
            @PathVariable String username,
            @RequestParam(defaultValue = "10") int per_page) {
        List starred = webClient.get()
                .uri("/users/{username}/starred?per_page={per_page}", username, per_page)
                .header("Accept", "application/vnd.github.v3+json")
                .retrieve()
                .bodyToMono(List.class)
                .block();
        return Result.ok(starred);
    }
}
