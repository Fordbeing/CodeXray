package com.codexray.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codexray.mapper.EmailSubscriberMapper;
import com.codexray.model.dto.TrendingRepoResponse;
import com.codexray.model.entity.EmailSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final EmailSubscriberMapper subscriberMapper;

    @Value("${spring.mail.username:noreply@codexray.com}")
    private String fromEmail;

    @Value("${codexray.mail.enabled:false}")
    private boolean mailEnabled;

    public EmailService(JavaMailSender mailSender, EmailSubscriberMapper subscriberMapper) {
        this.mailSender = mailSender;
        this.subscriberMapper = subscriberMapper;
    }

    /**
     * 向所有活跃订阅者发送 Trending 日报，根据订阅者语言偏好选择中文或英文版本。
     */
    public int sendTrendingToAll(List<TrendingRepoResponse> reposZh, List<TrendingRepoResponse> reposEn) {
        List<EmailSubscriber> subscribers = subscriberMapper.selectList(
                new QueryWrapper<EmailSubscriber>().eq("active", true)
        );
        int sent = 0;
        for (EmailSubscriber sub : subscribers) {
            try {
                boolean isZh = !"en".equals(sub.getLanguage());
                sendTrendingDigest(sub.getEmail(), isZh ? reposZh : reposEn, isZh);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send to {}", sub.getEmail(), e);
            }
        }
        log.info("Trending digest sent to {}/{} subscribers", sent, subscribers.size());
        return sent;
    }

    /**
     * 发送 GitHub Trending 日报邮件。
     */
    public void sendTrendingDigest(String toEmail, List<TrendingRepoResponse> repos, boolean isZh) {
        if (!mailEnabled) {
            log.debug("Mail disabled, skipping trending digest to {}", toEmail);
            return;
        }

        StringBuilder body = new StringBuilder();
        if (isZh) {
            body.append("CodeXray - GitHub 每日热点日报\n");
            body.append("==============================\n\n");
        } else {
            body.append("CodeXray - GitHub Trending Daily Report\n");
            body.append("=======================================\n\n");
        }

        int rank = 1;
        for (TrendingRepoResponse repo : repos) {
            body.append("#").append(rank++).append("  ").append(repo.repoName()).append("\n");
            if (repo.description() != null && !repo.description().isEmpty()) {
                body.append("   ").append(repo.description()).append("\n");
            }
            body.append("   ").append(isZh ? "Stars" : "Stars").append(": ").append(repo.stars() != null ? repo.stars() : "N/A");
            if (repo.todayStars() != null && !repo.todayStars().isEmpty()) {
                body.append(" (+").append(repo.todayStars()).append(" ").append(isZh ? "今日" : "today").append(")");
            }
            if (repo.language() != null && !repo.language().isEmpty()) {
                body.append(" | ").append(isZh ? "语言" : "Language").append(": ").append(repo.language());
            }
            body.append("\n   ").append(repo.repoUrl()).append("\n");
            if (repo.analysis() != null && !repo.analysis().isBlank()) {
                body.append("   ---\n");
                for (String line : repo.analysis().split("\n")) {
                    body.append("   ").append(line.trim()).append("\n");
                }
            }
            body.append("\n");
        }

        body.append("\n--- Powered by CodeXray ---");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject(isZh
                ? "CodeXray 每日热点 - " + java.time.LocalDate.now()
                : "CodeXray Trending - " + java.time.LocalDate.now());
        message.setText(body.toString());
        mailSender.send(message);
        log.info("Trending digest sent to {}", toEmail);
    }

    /**
     * 订阅邮件。
     */
    public EmailSubscriber subscribe(String email, String language) {
        EmailSubscriber existing = subscriberMapper.selectOne(
                new QueryWrapper<EmailSubscriber>().eq("email", email)
        );
        if (existing != null) {
            existing.setActive(true);
            if (language != null) existing.setLanguage(language);
            subscriberMapper.updateById(existing);
            return existing;
        }
        EmailSubscriber sub = new EmailSubscriber();
        sub.setEmail(email);
        sub.setActive(true);
        sub.setLanguage(language != null ? language : "zh");
        sub.setCreatedAt(LocalDateTime.now());
        subscriberMapper.insert(sub);
        return sub;
    }

    /**
     * 取消订阅。
     */
    public boolean unsubscribe(String email) {
        EmailSubscriber sub = subscriberMapper.selectOne(
                new QueryWrapper<EmailSubscriber>().eq("email", email)
        );
        if (sub == null) return false;
        sub.setActive(false);
        subscriberMapper.updateById(sub);
        return true;
    }

    /**
     * 获取所有订阅者。
     */
    public List<EmailSubscriber> listSubscribers() {
        return subscriberMapper.selectList(
                new QueryWrapper<EmailSubscriber>().orderByDesc("created_at")
        );
    }
}
