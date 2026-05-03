package com.codexray.controller;

import com.codexray.common.Result;
import com.codexray.model.dto.LoginRequest;
import com.codexray.model.dto.RegisterRequest;
import com.codexray.model.dto.ChangePasswordRequest;
import com.codexray.model.dto.UpdateProfileRequest;
import com.codexray.model.dto.UserResponse;
import com.codexray.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req.username(), req.password(), req.nickname(), req.githubUsername());
    }

    @PostMapping("/login")
    public Result<UserResponse> login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req.username(), req.password());
    }

    @GetMapping("/me")
    public Result<UserResponse> me() {
        return authService.getCurrentUser();
    }

    @PutMapping("/profile")
    public Result<UserResponse> updateProfile(@RequestBody UpdateProfileRequest req) {
        return authService.updateProfile(req.nickname(), req.githubUsername());
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest req) {
        return authService.changePassword(req.oldPassword(), req.newPassword());
    }

    @PostMapping("/send-verification-code")
    public Result<Void> sendVerificationCode(@RequestBody Map<String, String> body) {
        return authService.sendVerificationCode(body.get("email"));
    }

    @PostMapping("/verify-email")
    public Result<UserResponse> verifyEmail(@RequestBody Map<String, String> body) {
        return authService.verifyEmail(body.get("email"), body.get("code"));
    }
}
