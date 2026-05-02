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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String VERIFY_CODE_PREFIX = "codexray:verify:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final JavaMailSender mailSender;

    public AuthController(UserMapper userMapper, JwtUtil jwtUtil,
                          RedisTemplate<String, String> redisTemplate,
                          JavaMailSender mailSender) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
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
        user.setEmailVerified(false);
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

    // ========== 邮箱验证 ==========

    @PostMapping("/send-verification-code")
    public Result<Void> sendVerificationCode(@RequestBody Map<String, String> body) {
        Long userId = CurrentUser.get();
        if (userId == null) {
            return Result.error("未登录");
        }

        String email = body.get("email");
        if (email == null || !email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            return Result.error("请输入有效的邮箱地址");
        }

        // 检查邮箱是否已被其他用户绑定
        User existing = userMapper.selectOne(
                new QueryWrapper<User>().eq("email", email).ne("id", userId)
        );
        if (existing != null) {
            return Result.error("该邮箱已被其他用户绑定");
        }

        // 生成 6 位验证码
        String code = String.format("%06d", new Random().nextInt(1000000));
        String cacheKey = VERIFY_CODE_PREFIX + userId + ":" + email;
        redisTemplate.opsForValue().set(cacheKey, code, CODE_TTL);

        // 发送验证邮件
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("CodeXray 邮箱验证码");
            message.setText("您好，\n\n您的邮箱验证码是：" + code + "\n\n验证码 " + CODE_TTL.toMinutes() + " 分钟内有效。\n如非本人操作，请忽略此邮件。\n\n--- CodeXray");
            mailSender.send(message);
            log.info("Verification code sent to {} for user {}", email, userId);
            return Result.ok(null);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", email, e.getMessage());
            return Result.error("发送验证码失败，请检查邮箱配置是否正确");
        }
    }

    @PostMapping("/verify-email")
    public Result<UserResponse> verifyEmail(@RequestBody Map<String, String> body) {
        Long userId = CurrentUser.get();
        if (userId == null) {
            return Result.error("未登录");
        }

        String email = body.get("email");
        String code = body.get("code");
        if (email == null || code == null) {
            return Result.error("请提供邮箱和验证码");
        }

        String cacheKey = VERIFY_CODE_PREFIX + userId + ":" + email;
        String cachedCode = redisTemplate.opsForValue().get(cacheKey);
        if (cachedCode == null) {
            return Result.error("验证码已过期，请重新发送");
        }
        if (!cachedCode.equals(code.trim())) {
            return Result.error("验证码错误");
        }

        // 验证通过，绑定邮箱
        redisTemplate.delete(cacheKey);

        User user = userMapper.selectById(userId);
        user.setEmail(email);
        user.setEmailVerified(true);
        userMapper.updateById(user);

        return Result.ok(UserResponse.from(user, null));
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
