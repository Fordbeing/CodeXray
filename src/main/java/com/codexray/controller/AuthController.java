package com.codexray.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codexray.common.CurrentUser;
import com.codexray.common.JwtUtil;
import com.codexray.common.Result;
import com.codexray.mapper.UserMapper;
import com.codexray.model.dto.LoginRequest;
import com.codexray.model.dto.RegisterRequest;
import com.codexray.model.dto.ChangePasswordRequest;
import com.codexray.model.dto.UpdateProfileRequest;
import com.codexray.model.dto.UserResponse;
import com.codexray.model.entity.User;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    public AuthController(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public Result<UserResponse> register(@Valid @RequestBody RegisterRequest req) {
        User existing = userMapper.selectOne(
                new QueryWrapper<User>().eq("username", req.username())
        );
        if (existing != null) {
            return Result.error("用户名已存在");
        }

        User user = new User();
        user.setUsername(req.username());
        user.setPasswordHash(hashPassword(req.password()));
        user.setNickname(req.nickname() != null ? req.nickname() : req.username());
        user.setGithubUsername(req.githubUsername());
        userMapper.insert(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return Result.ok(UserResponse.from(user, token));
    }

    @PostMapping("/login")
    public Result<UserResponse> login(@Valid @RequestBody LoginRequest req) {
        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("username", req.username())
        );
        if (user == null || !verifyPassword(req.password(), user.getPasswordHash())) {
            return Result.error("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return Result.ok(UserResponse.from(user, token));
    }

    @GetMapping("/me")
    public Result<UserResponse> me() {
        Long userId = CurrentUser.get();
        if (userId == null) {
            return Result.error("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.ok(UserResponse.from(user, null));
    }

    @PutMapping("/profile")
    public Result<UserResponse> updateProfile(@RequestBody UpdateProfileRequest req) {
        Long userId = CurrentUser.get();
        if (userId == null) {
            return Result.error("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (req.nickname() != null) {
            user.setNickname(req.nickname());
        }
        if (req.githubUsername() != null) {
            user.setGithubUsername(req.githubUsername());
        }
        userMapper.updateById(user);
        return Result.ok(UserResponse.from(user, null));
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest req) {
        Long userId = CurrentUser.get();
        if (userId == null) {
            return Result.error("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (!verifyPassword(req.oldPassword(), user.getPasswordHash())) {
            return Result.error("原密码错误");
        }
        user.setPasswordHash(hashPassword(req.newPassword()));
        userMapper.updateById(user);
        return Result.ok(null);
    }

    // ========== 密码哈希工具 ==========

    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private String hashPassword(String password) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    private boolean verifyPassword(String password, String stored) {
        try {
            String[] parts = stored.split(":");
            if (parts.length != 2) return false;
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] actualHash = factory.generateSecret(spec).getEncoded();
            return java.util.Arrays.equals(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }
}
