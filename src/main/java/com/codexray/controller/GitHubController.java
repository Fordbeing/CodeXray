package com.codexray.controller;

import com.codexray.common.Result;
import com.codexray.service.GitHubService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubService gitHubService;

    public GitHubController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @GetMapping("/users/{username}")
    public Result<Map<String, Object>> getUserProfile(@PathVariable String username) {
        try {
            return Result.ok(gitHubService.getUserProfile(username));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/users/{username}/repos")
    public Result<List<Map<String, Object>>> getUserRepos(
            @PathVariable String username,
            @RequestParam(defaultValue = "updated") String sort,
            @RequestParam(defaultValue = "10") int per_page) {
        try {
            return Result.ok(gitHubService.getUserRepos(username, sort, per_page));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/users/{username}/starred")
    public Result<List<Map<String, Object>>> getUserStarred(
            @PathVariable String username,
            @RequestParam(defaultValue = "10") int per_page) {
        try {
            return Result.ok(gitHubService.getUserStarred(username, per_page));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/users/{username}/events")
    public Result<List<Map<String, Object>>> getUserEvents(
            @PathVariable String username,
            @RequestParam(defaultValue = "100") int per_page) {
        try {
            return Result.ok(gitHubService.getUserEvents(username, per_page));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/users/{username}/orgs")
    public Result<List<Map<String, Object>>> getUserOrgs(@PathVariable String username) {
        try {
            return Result.ok(gitHubService.getUserOrgs(username));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/users/{username}/stats")
    public Result<Map<String, Object>> getUserRepoStats(@PathVariable String username) {
        try {
            return Result.ok(gitHubService.getUserRepoStats(username));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/users/{username}/refresh")
    public Result<Void> refreshCache(@PathVariable String username) {
        gitHubService.refreshCache(username);
        return Result.ok(null);
    }

    @GetMapping("/cache-info")
    public Result<Map<String, Object>> cacheInfo() {
        return Result.ok(gitHubService.getCacheInfo());
    }
}
