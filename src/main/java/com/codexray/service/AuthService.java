package com.codexray.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codexray.common.CurrentUser;
import com.codexray.common.JwtUtil;
import com.codexray.common.Result;
import com.codexray.mapper.UserMapper;
import com.codexray.model.dto.UserResponse;
import com.codexray.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.Base64;
import java.util.Random;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String VERIFY_CODE_PREFIX = "codexray:verify:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final JavaMailSender mailSender;

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil,
                       RedisTemplate<String, String> redisTemplate,
                       JavaMailSender mailSender) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.mailSender = mailSender;
    }

    public Result<UserResponse> register(String username, String password, String nickname, String githubUsername) {
        User existing = userMapper.selectOne(
                new QueryWrapper<User>().eq("username", username)
        );
        if (existing != null) {
            return Result.error("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(hashPassword(password));
        user.setNickname(nickname != null ? nickname : username);
        user.setGithubUsername(githubUsername);
        user.setEmailVerified(false);
        userMapper.insert(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return Result.ok(UserResponse.from(user, token));
    }

    public Result<UserResponse> login(String username, String password) {
        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("username", username)
        );
        if (user == null || !verifyPassword(password, user.getPasswordHash())) {
            return Result.error("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return Result.ok(UserResponse.from(user, token));
    }

    public Result<UserResponse> getCurrentUser() {
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

    public Result<UserResponse> updateProfile(String nickname, String githubUsername) {
        Long userId = CurrentUser.get();
        if (userId == null) {
            return Result.error("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (nickname != null) {
            user.setNickname(nickname);
        }
        if (githubUsername != null) {
            user.setGithubUsername(githubUsername);
        }
        userMapper.updateById(user);
        return Result.ok(UserResponse.from(user, null));
    }

    public Result<Void> changePassword(String oldPassword, String newPassword) {
        Long userId = CurrentUser.get();
        if (userId == null) {
            return Result.error("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (!verifyPassword(oldPassword, user.getPasswordHash())) {
            return Result.error("原密码错误");
        }
        user.setPasswordHash(hashPassword(newPassword));
        userMapper.updateById(user);
        return Result.ok(null);
    }

    public Result<Void> sendVerificationCode(String email) {
        Long userId = CurrentUser.get();
        if (userId == null) {
            return Result.error("未登录");
        }

        if (email == null || !email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            return Result.error("请输入有效的邮箱地址");
        }

        User existing = userMapper.selectOne(
                new QueryWrapper<User>().eq("email", email).ne("id", userId)
        );
        if (existing != null) {
            return Result.error("该邮箱已被其他用户绑定");
        }

        String code = String.format("%06d", new Random().nextInt(1000000));
        String cacheKey = VERIFY_CODE_PREFIX + userId + ":" + email;
        redisTemplate.opsForValue().set(cacheKey, code, CODE_TTL);

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

    public Result<UserResponse> verifyEmail(String email, String code) {
        Long userId = CurrentUser.get();
        if (userId == null) {
            return Result.error("未登录");
        }

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

        redisTemplate.delete(cacheKey);

        User user = userMapper.selectById(userId);
        user.setEmail(email);
        user.setEmailVerified(true);
        userMapper.updateById(user);

        return Result.ok(UserResponse.from(user, null));
    }

    public String hashPassword(String password) {
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

    public boolean verifyPassword(String password, String stored) {
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
