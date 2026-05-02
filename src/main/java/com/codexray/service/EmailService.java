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
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailSubscriberMapper subscriberMapper;
    private final SettingService settingService;

    // 默认值来自 application.yml
    private final String defaultFromEmail;
    private final boolean defaultMailEnabled;
    private final String defaultMailHost;
    private final int defaultMailPort;
    private final String defaultMailPassword;

    // 可重建的 mailSender
    private JavaMailSender mailSender;
    private String mailSenderHost;
    private int mailSenderPort;
    private String mailSenderUsername;
    private String mailSenderPassword;

    public EmailService(JavaMailSender mailSender,
                        EmailSubscriberMapper subscriberMapper,
                        SettingService settingService,
                        @Value("${spring.mail.username:}") String defaultFromEmail,
                        @Value("${codexray.mail.enabled:false}") boolean defaultMailEnabled,
                        @Value("${spring.mail.host:smtp.163.com}") String defaultMailHost,
                        @Value("${spring.mail.port:465}") int defaultMailPort,
                        @Value("${spring.mail.password:}") String defaultMailPassword) {
        this.mailSender = mailSender;
        this.subscriberMapper = subscriberMapper;
        this.settingService = settingService;
        this.defaultFromEmail = defaultFromEmail;
        this.defaultMailEnabled = defaultMailEnabled;
        this.defaultMailHost = defaultMailHost;
        this.defaultMailPort = defaultMailPort;
        this.defaultMailPassword = defaultMailPassword;
    }

    private boolean isMailEnabled() {
        String val = settingService.get("mail_enabled");
        if (val != null) return "true".equalsIgnoreCase(val);
        return defaultMailEnabled;
    }

    private String getFromEmail() {
        String val = settingService.get("mail_username");
        return (val != null && !val.isBlank()) ? val : defaultFromEmail;
    }

    private synchronized JavaMailSender getMailSender() {
        String host = settingService.get("mail_host");
        host = (host != null && !host.isBlank()) ? host : defaultMailHost;
        String portStr = settingService.get("mail_port");
        int port = defaultMailPort;
        if (portStr != null && !portStr.isBlank()) {
            try { port = Integer.parseInt(portStr); } catch (NumberFormatException ignored) {}
        }
        String username = settingService.get("mail_username");
        username = (username != null && !username.isBlank()) ? username : defaultFromEmail;
        String password = settingService.get("mail_password");
        password = (password != null && !password.isBlank()) ? password : defaultMailPassword;

        // 如果配置没变，复用现有实例
        if (mailSender != null
                && host.equals(mailSenderHost)
                && port == mailSenderPort
                && username.equals(mailSenderUsername)
                && password.equals(mailSenderPassword)) {
            return mailSender;
        }

        // 重建 JavaMailSender
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(host);
        impl.setPort(port);
        impl.setUsername(username);
        impl.setPassword(password);

        Properties props = impl.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
        }

        this.mailSender = impl;
        this.mailSenderHost = host;
        this.mailSenderPort = port;
        this.mailSenderUsername = username;
        this.mailSenderPassword = password;
        log.info("JavaMailSender rebuilt: host={}, port={}, username={}", host, port, username);
        return mailSender;
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
        if (!isMailEnabled()) {
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
        message.setFrom(getFromEmail());
        message.setTo(toEmail);
        message.setSubject(isZh
                ? "CodeXray 每日热点 - " + java.time.LocalDate.now()
                : "CodeXray Trending - " + java.time.LocalDate.now());
        message.setText(body.toString());
        getMailSender().send(message);
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
