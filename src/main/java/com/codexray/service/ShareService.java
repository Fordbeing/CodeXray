package com.codexray.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codexray.mapper.AnalysisTaskMapper;
import com.codexray.mapper.ShareLinkMapper;
import com.codexray.mapper.UserMapper;
import com.codexray.model.dto.AnalysisResultResponse;
import com.codexray.model.entity.AnalysisTask;
import com.codexray.model.entity.ShareLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);

    private final ShareLinkMapper shareLinkMapper;
    private final AnalysisTaskMapper taskMapper;
    private final UserMapper userMapper;

    public ShareService(ShareLinkMapper shareLinkMapper, AnalysisTaskMapper taskMapper, UserMapper userMapper) {
        this.shareLinkMapper = shareLinkMapper;
        this.taskMapper = taskMapper;
        this.userMapper = userMapper;
    }

    public Map<String, Object> createShare(String taskId, Long userId, String password, int expiresInDays) {
        // 验证任务存在
        AnalysisTask task = taskMapper.selectOne(
                new QueryWrapper<AnalysisTask>().eq("task_id", taskId)
        );
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        ShareLink link = new ShareLink();
        link.setShareToken(generateToken());
        link.setTaskId(taskId);
        link.setUserId(userId);
        link.setViewCount(0);
        link.setCreatedAt(LocalDateTime.now());

        if (password != null && !password.isBlank()) {
            link.setPasswordProtected(true);
            link.setPasswordHash(hashPassword(password));
        } else {
            link.setPasswordProtected(false);
        }

        if (expiresInDays > 0) {
            link.setExpiresAt(LocalDateTime.now().plusDays(expiresInDays));
        }

        shareLinkMapper.insert(link);

        return Map.of(
                "shareToken", link.getShareToken(),
                "passwordProtected", link.isPasswordProtected(),
                "expiresAt", link.getExpiresAt() != null ? link.getExpiresAt().toString() : null
        );
    }

    public Map<String, Object> getSharedReport(String shareToken, String password) {
        ShareLink link = shareLinkMapper.selectOne(
                new QueryWrapper<ShareLink>().eq("share_token", shareToken)
        );
        if (link == null) {
            throw new RuntimeException("分享链接不存在");
        }
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("分享链接已过期");
        }
        if (link.isPasswordProtected()) {
            if (password == null || !verifyPassword(password, link.getPasswordHash())) {
                return Map.of("passwordRequired", true);
            }
        }

        // 增加查看次数
        link.setViewCount(link.getViewCount() + 1);
        shareLinkMapper.updateById(link);

        // 获取任务信息
        AnalysisTask task = taskMapper.selectOne(
                new QueryWrapper<AnalysisTask>().eq("task_id", link.getTaskId())
        );
        if (task == null) {
            throw new RuntimeException("关联的分析任务不存在");
        }

        // 获取用户昵称
        var user = userMapper.selectById(link.getUserId());
        String nickname = user != null && user.getNickname() != null ? user.getNickname() : "匿名用户";

        return Map.of(
                "passwordRequired", false,
                "taskId", task.getTaskId(),
                "repoUrl", task.getRepoUrl(),
                "status", task.getStatus(),
                "report", task.getReport(),
                "sharedBy", nickname,
                "viewCount", link.getViewCount(),
                "createdAt", link.getCreatedAt()
        );
    }

    public List<Map<String, Object>> listShares(Long userId) {
        List<ShareLink> links = shareLinkMapper.selectList(
                new QueryWrapper<ShareLink>()
                        .eq("user_id", userId)
                        .orderByDesc("created_at")
        );
        return links.stream().map(l -> {
            AnalysisTask task = taskMapper.selectOne(
                    new QueryWrapper<AnalysisTask>().eq("task_id", l.getTaskId())
            );
            return Map.<String, Object>of(
                    "shareToken", l.getShareToken(),
                    "taskId", l.getTaskId(),
                    "repoUrl", task != null ? task.getRepoUrl() : "unknown",
                    "passwordProtected", l.isPasswordProtected(),
                    "viewCount", l.getViewCount(),
                    "expiresAt", l.getExpiresAt() != null ? l.getExpiresAt().toString() : null,
                    "createdAt", l.getCreatedAt()
            );
        }).toList();
    }

    public void revokeShare(String shareToken, Long userId) {
        ShareLink link = shareLinkMapper.selectOne(
                new QueryWrapper<ShareLink>().eq("share_token", shareToken).eq("user_id", userId)
        );
        if (link != null) {
            shareLinkMapper.deleteById(link);
        }
    }

    private String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashPassword(String password) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }

    private boolean verifyPassword(String password, String storedHash) {
        try {
            String[] parts = storedHash.split(":");
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] actualHash = factory.generateSecret(spec).getEncoded();
            return java.util.Arrays.equals(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }
}
