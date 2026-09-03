package com.n0hana.echoes_server.service.logs;

import java.util.Optional;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.model.AuditLog;
import com.n0hana.echoes_server.model.User;
import com.n0hana.echoes_server.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {
    
    private final AuditLogService service;
    private final HttpServletRequest request;
    private final UserRepository userRepository;

    @AfterReturning("@annotation(auditable)")
    public void onSuccess(JoinPoint jp, Auditable auditable) {
        service.register(
            AuditLog.builder()
                .userId(getUserId())
                .action(auditable.action())
                .entity(auditable.entity())
                .status("SUCCESS")
                .ip(request.getRemoteAddr())
                .build()
        );
    }

    @AfterThrowing(pointcut = "@annotation(auditable)", throwing="ex")
    public void onFailure(JoinPoint jp, Auditable auditable, Exception ex) {
        service.register(
            AuditLog.builder()
                .userId(getUserId())
                .action(auditable.action())
                .entity(auditable.entity())
                .status("FAILED")
                .ip(request.getRemoteAddr())
                .details(ex.getMessage())
                .build()
        );
    }

    private String getUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            
            Optional<User> opt = userRepository.findUserByEmail(auth.getName());
            
            if (opt.isPresent()) {
                User user = opt.get();

                return user.getId().toString();
            }
        } 
        return "Unknow"; 
    }
}
