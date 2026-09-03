package com.n0hana.echoes_server.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;

import com.n0hana.echoes_server.model.AuditLog;
import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.service.logs.AuditLogService;

@Controller
public class WebController {

    private final AuditLogService auditLogService;

    public WebController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/")
    public String home(Model model) {
        return "home";
    }

    @GetMapping("/register")
    public String register(Model model) {
        return "register";
    }

    @GetMapping("/recover")
    public String recover(Model model) {
        return "recover";
    }

    @GetMapping("/reactivate")
    public String reactivate(Model model) {
        return "reactivate";
    }

    @GetMapping("/dashboard")
    public String dashboard(@CookieValue(value = "access_token", required = false) String token) {

        if (token == null || token.isEmpty()) {
            return "redirect:/";
        }

        return "dashboard";
    }

    @GetMapping("/dashboard/profile")
    public String profile(Model model, @CookieValue(value = "access_token", required = false) String token) {
        if (token == null || token.isEmpty()) {
            return "redirect:/";
        }
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        model.addAttribute("user", user);

        return "profile";
    }

    @GetMapping("/dashboard/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public String logs(Model model,
                       @CookieValue(value = "access_token", required = false) String token,
                       @PageableDefault(size = 15, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        if (token == null || token.isEmpty()) {
            return "redirect:/";
        }

        Page<AuditLog> logs = auditLogService.getLogs(pageable);
        model.addAttribute("logs", logs);

        return "logs";
    }

    @GetMapping("/dashboard/admin/terms")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminTerms(@CookieValue(value = "access_token", required = false) String token) {
        if (token == null || token.isEmpty()) {
            return "redirect:/";
        }
        return "admin-terms";
    }
}
