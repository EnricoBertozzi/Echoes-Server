package com.n0hana.echoes_server.infra.logs;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.user.model.User;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final ApplicationEventPublisher eventPublisher;

    private final HttpServletRequest request;

    private final ExpressionParser parser = new SpelExpressionParser();

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void onSuccess(JoinPoint jp, Auditable auditable, Object result) {
        eventPublisher.publishEvent(
                AuditLog.builder()
                        .userId(this.getUserId())
                        .action(auditable.action())
                        .entity(auditable.entity())
                        .entityId(this.evaluate(auditable.entityId(), jp, result))
                        .institutionId(this.evaluate(auditable.institutionId(), jp, result))
                        .status("SUCCESS")
                        .ip(this.getClientIp())
                        .build());
    }

    @AfterThrowing(pointcut = "@annotation(auditable)", throwing = "ex")
    public void onFailure(JoinPoint jp, Auditable auditable, Exception ex) {
        eventPublisher.publishEvent(
                AuditLog.builder()
                        .userId(this.getUserId())
                        .action(auditable.action())
                        .entity(auditable.entity())
                        .entityId(this.evaluate(auditable.entityId(), jp, null))
                        .institutionId(this.evaluate(auditable.institutionId(), jp, null))
                        .status("FAILED")
                        .ip(this.getClientIp())
                        .details(ex.getMessage())
                        .build());
    }

    /**
     * Avalia a expressão SpEL da anotação. Qualquer falha (expressão inválida,
     * valor ausente) resulta em null: a auditoria nunca pode quebrar a operação.
     */
    private String evaluate(String expression, JoinPoint jp, Object result) {
        if (expression == null || expression.isBlank())
            return null;

        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            String[] names = ((MethodSignature) jp.getSignature()).getParameterNames();
            Object[] args = jp.getArgs();
            if (names != null) {
                for (int i = 0; i < names.length && i < args.length; i++) {
                    context.setVariable(names[i], args[i]);
                }
            }
            context.setVariable("result", result);

            Object value = parser.parseExpression(expression).getValue(context);
            return value == null ? null : value.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String getUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            return user.getId().toString();
        }
        return null;
    }

    private String getClientIp() {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || ip.equalsIgnoreCase("unknown")) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
