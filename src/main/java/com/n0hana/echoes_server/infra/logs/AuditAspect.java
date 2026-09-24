package com.n0hana.echoes_server.infra.logs;

import java.util.Optional;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.User;

import jakarta.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuditAspect {

    @Autowired
    private AuditLogService service;

    @Autowired
    private HttpServletRequest request;

    @Autowired
    private UserRepository userRepository;

    @AfterReturning("@annotation(auditable)")
    public void onSuccess(JoinPoint jp, Auditable auditable) {
        service.register(
                AuditLog.builder()
                        .userId(this.getUserId())
                        .action(auditable.action())
                        .entity(auditable.entity())
                        .status("SUCCESS")
                        .ip(this.getClientIp())
                        .build());
    }

    @AfterThrowing(pointcut = "@annotation(auditable)", throwing = "ex")
    public void onFailure(JoinPoint jp, Auditable auditable, Exception ex) {
        service.register(
                AuditLog.builder()
                        .userId(this.getUserId())
                        .action(auditable.action())
                        .entity(auditable.entity())
                        .status("FAILED")
                        .ip(this.getClientIp())
                        .details(ex.getMessage())
                        .build());
    }

    private String getUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {

            Optional<User> opt = userRepository.findByEmail(auth.getName());

            if (opt.isPresent()) {
                User user = opt.get();

                return user.getId().toString();
            }
        }
        return null;
    }

    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || ip.equalsIgnoreCase("unknown")) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
