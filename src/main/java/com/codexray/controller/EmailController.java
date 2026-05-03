package com.codexray.controller;

import com.codexray.common.Result;
import com.codexray.model.entity.EmailSubscriber;
import com.codexray.service.EmailService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/email")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping("/subscribe")
    public Result<EmailSubscriber> subscribe(
            @RequestParam String email,
            @RequestParam(defaultValue = "zh") String language) {
        return Result.ok(emailService.subscribe(email, language));
    }

    @PostMapping("/unsubscribe")
    public Result<Void> unsubscribe(@RequestParam String email) {
        if (emailService.unsubscribe(email)) {
            return Result.ok(null);
        }
        return Result.error("Email not found: " + email);
    }

    @GetMapping("/subscribers")
    public Result<List<EmailSubscriber>> listSubscribers() {
        if (!com.codexray.common.CurrentUser.isLoggedIn()) {
            return Result.error("Unauthorized");
        }
        return Result.ok(emailService.listSubscribers());
    }
}
