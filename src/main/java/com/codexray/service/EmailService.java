package com.codexray.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.codexray.mapper.EmailSubscriberMapper;
import com.codexray.model.dto.TrendingRepoResponse;
import com.codexray.model.dto.WeeklyTrendingRepoResponse;
import com.codexray.model.entity.EmailSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailSubscriberMapper subscriberMapper;
    private final SettingService settingService;

    private final String defaultFromEmail;
    private final boolean defaultMailEnabled;
    private final String defaultMailHost;
    private final int defaultMailPort;
    private final String defaultMailPassword;

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

        if (mailSender != null
                && host.equals(mailSenderHost)
                && port == mailSenderPort
                && username.equals(mailSenderUsername)
                && password.equals(mailSenderPassword)) {
            return mailSender;
        }

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

    // ========================================================================
    // 发送入口
    // ========================================================================

    /**
     * 向所有活跃订阅者发送 Trending 日报（含日榜 + 周榜）。
     */
    public int sendTrendingToAll(List<TrendingRepoResponse> reposZh, List<TrendingRepoResponse> reposEn,
                                 List<WeeklyTrendingRepoResponse> weeklyZh, List<WeeklyTrendingRepoResponse> weeklyEn) {
        List<EmailSubscriber> subscribers = subscriberMapper.selectList(
                new QueryWrapper<EmailSubscriber>().eq("active", true)
        );
        int sent = 0;
        for (EmailSubscriber sub : subscribers) {
            try {
                boolean isZh = !"en".equals(sub.getLanguage());
                sendTrendingDigest(sub.getEmail(),
                        isZh ? reposZh : reposEn,
                        isZh ? weeklyZh : weeklyEn,
                        isZh);
                sent++;
            } catch (Exception e) {
                log.error("Failed to send to {}", sub.getEmail(), e);
            }
        }
        log.info("Trending digest sent to {}/{} subscribers", sent, subscribers.size());
        return sent;
    }

    /**
     * 发送 HTML 格式的 GitHub Trending 日报邮件（含日榜 + 周榜）。
     */
    public void sendTrendingDigest(String toEmail,
                                   List<TrendingRepoResponse> dailyRepos,
                                   List<WeeklyTrendingRepoResponse> weeklyRepos,
                                   boolean isZh) {
        if (!isMailEnabled()) {
            log.debug("Mail disabled, skipping trending digest to {}", toEmail);
            return;
        }

        LocalDate today = LocalDate.now();
        String subject = isZh
                ? "CodeXray GitHub 热点日报 - " + today
                : "CodeXray GitHub Trending - " + today;

        String html = buildEmailHtml(dailyRepos, weeklyRepos, isZh, today);

        try {
            MimeMessage mimeMessage = getMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(getFromEmail());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true); // true = HTML
            getMailSender().send(mimeMessage);
            log.info("Trending digest sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send email to {}", toEmail, e);
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    // ========================================================================
    // 订阅管理
    // ========================================================================

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

    public boolean unsubscribe(String email) {
        EmailSubscriber sub = subscriberMapper.selectOne(
                new QueryWrapper<EmailSubscriber>().eq("email", email)
        );
        if (sub == null) return false;
        sub.setActive(false);
        subscriberMapper.updateById(sub);
        return true;
    }

    public List<EmailSubscriber> listSubscribers() {
        return subscriberMapper.selectList(
                new QueryWrapper<EmailSubscriber>().orderByDesc("created_at")
        );
    }

    // ========================================================================
    // HTML 模板构建
    // ========================================================================

    private String buildEmailHtml(List<TrendingRepoResponse> dailyRepos,
                                  List<WeeklyTrendingRepoResponse> weeklyRepos,
                                  boolean isZh, LocalDate today) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>");
        sb.append("<html lang=\"").append(isZh ? "zh-CN" : "en").append("\">");
        sb.append("<head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>");
        sb.append("<body style=\"margin:0;padding:0;background-color:#f4f6f9;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;\">");

        // 外层容器
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f4f6f9;\">");
        sb.append("<tr><td align=\"center\" style=\"padding:32px 16px;\">");
        sb.append("<table role=\"presentation\" width=\"640\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:640px;width:100%;background-color:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,0.06);\">");

        // ---- Header ----
        sb.append("<tr><td style=\"background:linear-gradient(135deg,#1a1a2e,#16213e);padding:28px 32px;text-align:center;\">");
        sb.append("<div style=\"font-size:22px;font-weight:700;color:#ffffff;margin-bottom:6px;\">CodeXray</div>");
        sb.append("<div style=\"font-size:13px;color:#a0aec0;\">").append(isZh ? "GitHub 热点日报" : "GitHub Trending Daily Report").append("</div>");
        sb.append("<div style=\"font-size:12px;color:#718096;margin-top:4px;\">").append(today).append("</div>");
        sb.append("</td></tr>");

        // ---- 日榜 ----
        if (dailyRepos != null && !dailyRepos.isEmpty()) {
            sb.append(buildSectionHeader(isZh ? "每日热点" : "Daily Trending", "1a1a2e"));
            sb.append(buildDailySection(dailyRepos, isZh));
        }

        // ---- 周榜 ----
        if (weeklyRepos != null && !weeklyRepos.isEmpty()) {
            sb.append(buildSectionHeader(isZh ? "本周热榜" : "Weekly Hot List", "7c3aed"));
            sb.append(buildWeeklySection(weeklyRepos, isZh));
        }

        // ---- Footer ----
        sb.append("<tr><td style=\"padding:24px 32px;text-align:center;border-top:1px solid #e2e8f0;\">");
        sb.append("<div style=\"font-size:11px;color:#a0aec0;\">Powered by CodeXray</div>");
        sb.append("</td></tr>");

        sb.append("</table>");
        sb.append("</td></tr>");
        sb.append("</table>");
        sb.append("</body></html>");

        return sb.toString();
    }

    private String buildSectionHeader(String title, String color) {
        StringBuilder sb = new StringBuilder();
        sb.append("<tr><td style=\"padding:24px 32px 12px;\">");
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">");
        sb.append("<tr><td style=\"font-size:16px;font-weight:700;color:#1a202c;border-bottom:2px solid #").append(color).append(";padding-bottom:8px;\">");
        sb.append(title);
        sb.append("</td></tr></table>");
        sb.append("</td></tr>");
        return sb.toString();
    }

    private String buildDailySection(List<TrendingRepoResponse> repos, boolean isZh) {
        StringBuilder sb = new StringBuilder();
        sb.append("<tr><td style=\"padding:0 32px;\">");

        int rank = 1;
        for (TrendingRepoResponse repo : repos) {
            sb.append(buildDailyCard(repo, rank++, isZh));
        }

        sb.append("</td></tr>");
        return sb.toString();
    }

    private String buildDailyCard(TrendingRepoResponse repo, int rank, boolean isZh) {
        String rankColor = rank == 1 ? "#f59e0b" : rank == 2 ? "#9ca3af" : rank == 3 ? "#ea580c" : "#cbd5e1";
        String rankBg = rank <= 3 ? "#fffbeb" : "#f8fafc";

        StringBuilder sb = new StringBuilder();
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:12px;border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;\">");

        // 卡片头部：排名 + 仓库名
        sb.append("<tr><td style=\"padding:14px 16px 10px;\">");
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">");
        sb.append("<tr>");
        sb.append("<td width=\"32\" style=\"vertical-align:top;padding-right:10px;\">");
        sb.append("<div style=\"width:28px;height:28px;border-radius:6px;background:").append(rankBg).append(";color:").append(rankColor).append(";font-size:13px;font-weight:800;text-align:center;line-height:28px;\">").append(rank).append("</div>");
        sb.append("</td>");
        sb.append("<td style=\"vertical-align:top;\">");
        sb.append("<a href=\"").append(repo.repoUrl()).append("\" style=\"font-size:15px;font-weight:700;color:#1a202c;text-decoration:none;\">").append(esc(repo.repoName())).append("</a>");
        if (repo.language() != null && !repo.language().isEmpty()) {
            sb.append(" <span style=\"display:inline-block;font-size:11px;color:#4a5568;background:#edf2f7;border-radius:10px;padding:1px 8px;margin-left:6px;\">").append(esc(repo.language())).append("</span>");
        }
        sb.append("</td></tr></table>");
        sb.append("</td></tr>");

        // 描述
        if (repo.description() != null && !repo.description().isEmpty()) {
            sb.append("<tr><td style=\"padding:0 16px 8px 58px;font-size:13px;color:#4a5568;line-height:1.6;\">").append(esc(repo.description())).append("</td></tr>");
        }

        // 统计信息
        sb.append("<tr><td style=\"padding:0 16px 12px 58px;\">");
        sb.append("<span style=\"font-size:12px;color:#718096;\">");
        sb.append(isZh ? "Stars: " : "Stars: ").append(repo.stars() != null ? repo.stars() : "N/A");
        if (repo.todayStars() != null && !repo.todayStars().isEmpty()) {
            sb.append(" <span style=\"color:#16a34a;font-weight:600;\">+").append(repo.todayStars()).append("</span>");
        }
        if (repo.forks() != null && !repo.forks().isEmpty()) {
            sb.append(" &nbsp;&middot;&nbsp; ").append(isZh ? "Forks: " : "Forks: ").append(repo.forks());
        }
        sb.append("</span>");
        sb.append("</td></tr>");

        // AI 分析
        if (repo.analysis() != null && !repo.analysis().isBlank()) {
            sb.append("<tr><td style=\"padding:0 16px 14px 58px;\">");
            sb.append("<div style=\"background:#f0fdf4;border:1px solid #bbf7d0;border-radius:6px;padding:10px 12px;font-size:12px;color:#166534;line-height:1.7;\">");
            sb.append("<div style=\"font-weight:700;margin-bottom:4px;\">").append(isZh ? "AI 分析" : "AI Analysis").append("</div>");
            sb.append(markdownToSimpleHtml(repo.analysis()));
            sb.append("</div>");
            sb.append("</td></tr>");
        }

        sb.append("</table>");
        return sb.toString();
    }

    private String buildWeeklySection(List<WeeklyTrendingRepoResponse> repos, boolean isZh) {
        StringBuilder sb = new StringBuilder();
        sb.append("<tr><td style=\"padding:0 32px;\">");

        int rank = 1;
        for (WeeklyTrendingRepoResponse repo : repos) {
            sb.append(buildWeeklyCard(repo, rank++, isZh));
        }

        sb.append("</td></tr>");
        return sb.toString();
    }

    private String buildWeeklyCard(WeeklyTrendingRepoResponse repo, int rank, boolean isZh) {
        String rankColor = rank == 1 ? "#f59e0b" : rank == 2 ? "#9ca3af" : rank == 3 ? "#ea580c" : "#cbd5e1";
        String rankBg = rank <= 3 ? "#fffbeb" : "#f8fafc";

        // 天数徽章颜色
        String badgeBg, badgeColor;
        if (repo.daysCount() >= 5) { badgeBg = "#fef2f2"; badgeColor = "#dc2626"; }
        else if (repo.daysCount() >= 3) { badgeBg = "#fff7ed"; badgeColor = "#ea580c"; }
        else { badgeBg = "#eff6ff"; badgeColor = "#2563eb"; }

        StringBuilder sb = new StringBuilder();
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:12px;border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;\">");

        // 卡片头部
        sb.append("<tr><td style=\"padding:14px 16px 10px;\">");
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">");
        sb.append("<tr>");
        sb.append("<td width=\"32\" style=\"vertical-align:top;padding-right:10px;\">");
        sb.append("<div style=\"width:28px;height:28px;border-radius:6px;background:").append(rankBg).append(";color:").append(rankColor).append(";font-size:13px;font-weight:800;text-align:center;line-height:28px;\">").append(rank).append("</div>");
        sb.append("</td>");
        sb.append("<td style=\"vertical-align:top;\">");
        sb.append("<a href=\"").append(repo.repoUrl()).append("\" style=\"font-size:15px;font-weight:700;color:#1a202c;text-decoration:none;\">").append(esc(repo.repoName())).append("</a>");
        if (repo.language() != null && !repo.language().isEmpty()) {
            sb.append(" <span style=\"display:inline-block;font-size:11px;color:#4a5568;background:#edf2f7;border-radius:10px;padding:1px 8px;margin-left:6px;\">").append(esc(repo.language())).append("</span>");
        }
        sb.append("</td>");
        // 天数徽章
        sb.append("<td width=\"80\" style=\"text-align:right;vertical-align:top;\">");
        sb.append("<span style=\"display:inline-block;font-size:11px;font-weight:700;color:").append(badgeColor).append(";background:").append(badgeBg).append(";border-radius:10px;padding:2px 10px;\">");
        sb.append(isZh ? "上榜 " : "Hot ").append(repo.daysCount()).append(isZh ? " 天" : "d");
        sb.append("</span>");
        sb.append("</td>");
        sb.append("</tr></table>");
        sb.append("</td></tr>");

        // 描述
        if (repo.description() != null && !repo.description().isEmpty()) {
            sb.append("<tr><td style=\"padding:0 16px 8px 58px;font-size:13px;color:#4a5568;line-height:1.6;\">").append(esc(repo.description())).append("</td></tr>");
        }

        // 统计
        sb.append("<tr><td style=\"padding:0 16px 12px 58px;\">");
        sb.append("<span style=\"font-size:12px;color:#718096;\">");
        sb.append(isZh ? "Stars: " : "Stars: ").append(repo.stars() != null ? repo.stars() : "N/A");
        if (repo.totalTodayStars() != null && !repo.totalTodayStars().isEmpty()) {
            sb.append(" <span style=\"color:#16a34a;font-weight:600;\">+").append(repo.totalTodayStars()).append(isZh ? " 周累计" : " weekly</span>");
        }
        if (repo.forks() != null && !repo.forks().isEmpty()) {
            sb.append(" &nbsp;&middot;&nbsp; ").append(isZh ? "Forks: " : "Forks: ").append(repo.forks());
        }
        sb.append("</span>");
        sb.append("</td></tr>");

        // AI 分析
        if (repo.analysis() != null && !repo.analysis().isBlank()) {
            sb.append("<tr><td style=\"padding:0 16px 14px 58px;\">");
            sb.append("<div style=\"background:#f0fdf4;border:1px solid #bbf7d0;border-radius:6px;padding:10px 12px;font-size:12px;color:#166534;line-height:1.7;\">");
            sb.append("<div style=\"font-weight:700;margin-bottom:4px;\">").append(isZh ? "AI 分析" : "AI Analysis").append("</div>");
            sb.append(markdownToSimpleHtml(repo.analysis()));
            sb.append("</div>");
            sb.append("</td></tr>");
        }

        sb.append("</table>");
        return sb.toString();
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    /** 转义 HTML 特殊字符 */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * 将 LLM 返回的 Markdown 转为邮件友好的简单 HTML：
     * - 去掉 Markdown 标题符号 (#)
     * - 去掉粗体标记 (**)
     * - 换行转 <br>
     * - 转义 HTML
     */
    private static String markdownToSimpleHtml(String md) {
        if (md == null) return "";
        // 先转义
        String s = esc(md);
        // 去掉 ### 标题标记，保留文字并加粗
        s = s.replaceAll("(?m)^#{1,6}\\s+(.+)$", "<strong>$1</strong>");
        // 去掉 **粗体**，转为 <strong>
        s = s.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        // 去掉 *斜体*，转为 <em>
        s = s.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        // 去掉行首的 - 列表标记
        s = s.replaceAll("(?m)^\\s*-\\s+", "&bull; ");
        // 去掉 ``` 代码块标记
        s = s.replaceAll("```\\w*", "");
        // 换行
        s = s.replace("\n", "<br>");
        // 去掉多余的 ---
        s = s.replaceAll("<br>-{3,}<br>", "<br>");
        return s;
    }
}
