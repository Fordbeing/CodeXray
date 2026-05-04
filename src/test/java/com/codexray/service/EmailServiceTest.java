package com.codexray.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.time.LocalDate;
import java.util.Properties;

/**
 * 独立邮件发送测试，不需要启动完整 Spring 上下文。
 * 运行前请设置 SMTP 凭据。
 */
class EmailServiceTest {

    private static final String SMTP_HOST = "smtp.163.com";
    private static final int SMTP_PORT = 465;
    private static final String FROM_EMAIL = System.getenv("TEST_MAIL_USERNAME");
    private static final String SMTP_PASSWORD = System.getenv("TEST_MAIL_PASSWORD");
    private static final String TO_EMAIL = System.getenv("TEST_MAIL_TO");

    @Test
    void testSendEmail() throws Exception {
        if (FROM_EMAIL == null || SMTP_PASSWORD == null || TO_EMAIL == null) {
            System.out.println(">>> 跳过测试：请设置环境变量 TEST_MAIL_USERNAME, TEST_MAIL_PASSWORD, TEST_MAIL_TO");
            return;
        }

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(SMTP_HOST);
        mailSender.setPort(SMTP_PORT);
        mailSender.setUsername(FROM_EMAIL);
        mailSender.setPassword(SMTP_PASSWORD);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.timeout", "10000");

        LocalDate today = LocalDate.now();
        String html = buildTestEmail(today);

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
        helper.setFrom(FROM_EMAIL);
        helper.setTo(TO_EMAIL);
        helper.setSubject("CodeXray GitHub 热点日报 - " + today);
        helper.setText(html, true);

        System.out.println(">>> 正在发送邮件到 " + TO_EMAIL + " ...");
        mailSender.send(mimeMessage);
        System.out.println(">>> 邮件发送成功！");
    }

    private String buildTestEmail(LocalDate today) {
        boolean isZh = true;
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"></head>");
        sb.append("<body style=\"margin:0;padding:0;background-color:#f4f6f9;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;\">");
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f4f6f9;\"><tr><td align=\"center\" style=\"padding:32px 16px;\">");
        sb.append("<table role=\"presentation\" width=\"640\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:640px;width:100%;background-color:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,0.06);\">");

        sb.append("<tr><td style=\"background:linear-gradient(135deg,#1a1a2e,#16213e);padding:28px 32px;text-align:center;\">");
        sb.append("<div style=\"font-size:22px;font-weight:700;color:#ffffff;margin-bottom:6px;\">CodeXray</div>");
        sb.append("<div style=\"font-size:13px;color:#a0aec0;\">GitHub 热点日报</div>");
        sb.append("<div style=\"font-size:12px;color:#718096;margin-top:4px;\">").append(today).append("</div>");
        sb.append("</td></tr>");

        sb.append("<tr><td style=\"padding:24px 32px 12px;\">");
        sb.append("<div style=\"font-size:16px;font-weight:700;color:#1a202c;border-bottom:2px solid #1a1a2e;padding-bottom:8px;\">每日热点</div>");
        sb.append("</td></tr>");

        sb.append("<tr><td style=\"padding:0 32px;\">");
        sb.append(buildCard("langchain-ai/langchain", "https://github.com/langchain-ai/langchain",
                "Build context-aware reasoning applications", "Python", "98,500", "+320", "15,200", 1,
                "LangChain 是一个用于构建 LLM 应用的开源框架，核心解决 LLM 与外部数据源的集成问题。基于 Python 模块化设计，核心组件包括 Models、Prompts、Chains、Agents、Memory。生态丰富，支持 160+ 集成。", isZh));
        sb.append(buildCard("openai/whisper", "https://github.com/openai/whisper",
                "Robust Speech Recognition via Large-Scale Weak Supervision", "Python", "72,300", "+180", "8,900", 2,
                "OpenAI 开源的通用语音识别模型，支持多语言转写和翻译。识别精度极高，支持 99 种语言，可本地部署。", isZh));
        sb.append(buildCard("vercel/next.js", "https://github.com/vercel/next.js",
                "The React Framework for the Web", "JavaScript", "128,000", "+95", "27,600", 3, null, isZh));
        sb.append("</td></tr>");

        sb.append("<tr><td style=\"padding:24px 32px 12px;\">");
        sb.append("<div style=\"font-size:16px;font-weight:700;color:#1a202c;border-bottom:2px solid #7c3aed;padding-bottom:8px;\">本周热榜</div>");
        sb.append("</td></tr>");

        sb.append("<tr><td style=\"padding:0 32px;\">");
        sb.append(buildWeeklyCard("langchain-ai/langchain", "https://github.com/langchain-ai/langchain",
                "Build context-aware reasoning applications", "Python", "98,500", "+2,150", "15,200", 7, 1,
                "连续 7 天上榜的明星项目，LLM 应用框架领域的标杆。", isZh));
        sb.append(buildWeeklyCard("openai/whisper", "https://github.com/openai/whisper",
                "Robust Speech Recognition via Large-Scale Weak Supervision", "Python", "72,300", "+890", "8,900", 5, 2, null, isZh));
        sb.append(buildWeeklyCard("vercel/next.js", "https://github.com/vercel/next.js",
                "The React Framework for the Web", "JavaScript", "128,000", "+450", "27,600", 4, 3,
                "Next.js 采用混合渲染模式，支持 SSR、SSG、ISR 和客户端渲染。", isZh));
        sb.append("</td></tr>");

        sb.append("<tr><td style=\"padding:24px 32px;text-align:center;border-top:1px solid #e2e8f0;\">");
        sb.append("<div style=\"font-size:11px;color:#a0aec0;\">Powered by CodeXray</div>");
        sb.append("</td></tr>");

        sb.append("</table></td></tr></table></body></html>");
        return sb.toString();
    }

    private String buildCard(String name, String url, String desc, String lang,
                             String stars, String todayStars, String forks, int rank,
                             String analysis, boolean isZh) {
        String rankColor = rank == 1 ? "#f59e0b" : rank == 2 ? "#9ca3af" : "#ea580c";
        StringBuilder sb = new StringBuilder();
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:12px;border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;\">");
        sb.append("<tr><td style=\"padding:14px 16px 10px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>");
        sb.append("<td width=\"32\" style=\"vertical-align:top;padding-right:10px;\">");
        sb.append("<div style=\"width:28px;height:28px;border-radius:6px;background:#fffbeb;color:").append(rankColor).append(";font-size:13px;font-weight:800;text-align:center;line-height:28px;\">").append(rank).append("</div>");
        sb.append("</td><td style=\"vertical-align:top;\">");
        sb.append("<a href=\"").append(url).append("\" style=\"font-size:15px;font-weight:700;color:#1a202c;text-decoration:none;\">").append(name).append("</a>");
        sb.append(" <span style=\"display:inline-block;font-size:11px;color:#4a5568;background:#edf2f7;border-radius:10px;padding:1px 8px;margin-left:6px;\">").append(lang).append("</span>");
        sb.append("</td></tr></table></td></tr>");
        sb.append("<tr><td style=\"padding:0 16px 8px 58px;font-size:13px;color:#4a5568;line-height:1.6;\">").append(desc).append("</td></tr>");
        sb.append("<tr><td style=\"padding:0 16px 12px 58px;\">");
        sb.append("<span style=\"font-size:12px;color:#718096;\">Stars: ").append(stars);
        sb.append(" <span style=\"color:#16a34a;font-weight:600;\">").append(todayStars).append("</span>");
        sb.append(" &nbsp;&middot;&nbsp; Forks: ").append(forks).append("</span></td></tr>");
        if (analysis != null) {
            sb.append("<tr><td style=\"padding:0 16px 14px 58px;\">");
            sb.append("<div style=\"background:#f0fdf4;border:1px solid #bbf7d0;border-radius:6px;padding:10px 12px;font-size:12px;color:#166534;line-height:1.7;\">");
            sb.append("<div style=\"font-weight:700;margin-bottom:4px;\">AI 分析</div>").append(analysis).append("</div></td></tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String buildWeeklyCard(String name, String url, String desc, String lang,
                                   String stars, String weeklyStars, String forks, int days, int rank,
                                   String analysis, boolean isZh) {
        String rankColor = rank == 1 ? "#f59e0b" : rank == 2 ? "#9ca3af" : "#ea580c";
        String badgeBg, badgeColor;
        if (days >= 5) { badgeBg = "#fef2f2"; badgeColor = "#dc2626"; }
        else if (days >= 3) { badgeBg = "#fff7ed"; badgeColor = "#ea580c"; }
        else { badgeBg = "#eff6ff"; badgeColor = "#2563eb"; }

        StringBuilder sb = new StringBuilder();
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin-bottom:12px;border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;\">");
        sb.append("<tr><td style=\"padding:14px 16px 10px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>");
        sb.append("<td width=\"32\" style=\"vertical-align:top;padding-right:10px;\">");
        sb.append("<div style=\"width:28px;height:28px;border-radius:6px;background:#fffbeb;color:").append(rankColor).append(";font-size:13px;font-weight:800;text-align:center;line-height:28px;\">").append(rank).append("</div>");
        sb.append("</td><td style=\"vertical-align:top;\">");
        sb.append("<a href=\"").append(url).append("\" style=\"font-size:15px;font-weight:700;color:#1a202c;text-decoration:none;\">").append(name).append("</a>");
        sb.append(" <span style=\"display:inline-block;font-size:11px;color:#4a5568;background:#edf2f7;border-radius:10px;padding:1px 8px;margin-left:6px;\">").append(lang).append("</span>");
        sb.append("</td><td width=\"80\" style=\"text-align:right;vertical-align:top;\">");
        sb.append("<span style=\"display:inline-block;font-size:11px;font-weight:700;color:").append(badgeColor).append(";background:").append(badgeBg).append(";border-radius:10px;padding:2px 10px;\">");
        sb.append("上榜 ").append(days).append(" 天</span>");
        sb.append("</td></tr></table></td></tr>");
        sb.append("<tr><td style=\"padding:0 16px 8px 58px;font-size:13px;color:#4a5568;line-height:1.6;\">").append(desc).append("</td></tr>");
        sb.append("<tr><td style=\"padding:0 16px 12px 58px;\">");
        sb.append("<span style=\"font-size:12px;color:#718096;\">Stars: ").append(stars);
        sb.append(" <span style=\"color:#16a34a;font-weight:600;\">").append(weeklyStars).append(" 周累计</span>");
        sb.append(" &nbsp;&middot;&nbsp; Forks: ").append(forks).append("</span></td></tr>");
        if (analysis != null) {
            sb.append("<tr><td style=\"padding:0 16px 14px 58px;\">");
            sb.append("<div style=\"background:#f0fdf4;border:1px solid #bbf7d0;border-radius:6px;padding:10px 12px;font-size:12px;color:#166534;line-height:1.7;\">");
            sb.append("<div style=\"font-weight:700;margin-bottom:4px;\">AI 分析</div>").append(analysis).append("</div></td></tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }
}
