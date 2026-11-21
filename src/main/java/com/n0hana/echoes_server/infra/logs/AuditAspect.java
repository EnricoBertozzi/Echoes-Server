
package com.n0hana.echoes_server.infra.logs;

import java.util.Set;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import com.n0hana.echoes_server.infra.web.ClientIpResolver;
import com.n0hana.echoes_server.institution.InstitutionRepository;

import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.user.model.User;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.Student;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final ApplicationEventPublisher eventPublisher;
    private final HttpServletRequest request;
    private final AuditLogService auditLogService;
    private final InstitutionRepository institutionRepository;

    /** só confia em X-Forwarded-For atrás de proxy conhecido (S13) */
    @Value("${api.security.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    private static final int MAX_DETAILS = 255;

    private final ExpressionParser parser =
            new SpelExpressionParser();

    /**
     * Entidades cujo contexto pode ser institucional.
     *
     * Eventos globais não recebem instituição
     * automaticamente.
     */
    private static final Set<String> INSTITUTIONAL_ENTITIES =
            Set.of(
                "Institution",
                "Manager",
                "Teacher",
                "Student",
                "Classroom",
                "ClassroomDevice",
                "Device",
                "DeviceScenario",
                "User",
                "Term",
                "ClinicalPreset"
            );

    /**
     * Captura operações concluídas.
     */
    @AfterReturning(
        pointcut = "@annotation(auditable)",
        returning = "result"
    )
    public void onSuccess(
            JoinPoint jp,
            Auditable auditable,
            Object result
    ) {
        Actor actor = this.resolveActor();

        eventPublisher.publishEvent(
            AuditLog.builder()
                .userId(this.getUserId())
                .actorName(actor.name())
                .actorRole(actor.role())
                .actorInstitutionName(actor.institutionName())
                .action(auditable.action())
                .entity(auditable.entity())
                .entityId(
                    this.evaluate(
                        auditable.entityId(),
                        jp,
                        result
                    )
                )
                .entityName(
                    this.evaluate(
                        auditable.entityName(),
                        jp,
                        result
                    )
                )
                .details(
                    PiiMasker.mask(
                        this.evaluate(
                            auditable.details(),
                            jp,
                            result
                        ),
                        MAX_DETAILS
                    )
                )
                .institutionId(
                    this.resolveInstitutionId(
                        auditable,
                        jp,
                        result
                    )
                )
                .status("SUCCESS")
                .ip(this.getClientIp())
                .build()
        );
    }

    /**
     * Captura operações que lançaram uma exceção.
     */
    @AfterThrowing(
        pointcut = "@annotation(auditable)",
        throwing = "ex"
    )
    public void onFailure(
            JoinPoint jp,
            Auditable auditable,
            Exception ex
    ) {
        Actor actor = this.resolveActor();

        // Falha gravada de forma síncrona em transação própria (REQUIRES_NEW), como
        // ocorre com dontRollbackOn no login (B4).
        auditLogService.recordFailure(
            AuditLog.builder()
                .userId(this.getUserId())
                .actorName(actor.name())
                .actorRole(actor.role())
                .actorInstitutionName(actor.institutionName())
                .action(auditable.action())
                .entity(auditable.entity())
                .entityId(
                    this.evaluate(
                        auditable.entityId(),
                        jp,
                        null
                    )
                )
                .entityName(
                    this.evaluate(
                        auditable.entityName(),
                        jp,
                        null
                    )
                )
                .institutionId(
                    this.resolveInstitutionId(
                        auditable,
                        jp,
                        null
                    )
                )
                .status("FAILED")
                .ip(this.getClientIp())
                // sem e-mail/token da mensagem da exceção (L11); o tipo basta para diagnosticar
                .details(PiiMasker.mask(
                    ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage()),
                    MAX_DETAILS))
                .build()
        );
    }

    /**
     * Avalia expressões SpEL presentes em @Auditable.
     *
     * Variáveis disponíveis:
     * - parâmetros do método, por nome
     * - #result, quando existe retorno
     *
     * Falhas de avaliação não interrompem a operação.
     */
    private String evaluate(
            String expression,
            JoinPoint jp,
            Object result
    ) {
        if (
            expression == null ||
            expression.isBlank()
        ) {
            return null;
        }

        try {
            StandardEvaluationContext context =
                    new StandardEvaluationContext();

            String[] names =
                    ((MethodSignature) jp.getSignature())
                        .getParameterNames();

            Object[] args = jp.getArgs();

            if (names != null) {
                for (
                    int i = 0;
                    i < names.length && i < args.length;
                    i++
                ) {
                    context.setVariable(
                        names[i],
                        args[i]
                    );
                }
            }

            context.setVariable(
                "result",
                result
            );

            Object value = parser
                    .parseExpression(expression)
                    .getValue(context);

            return value == null
                ? null
                : value.toString();

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolve a instituição de um evento.
     *
     * Primeiro tenta a expressão da anotação.
     * Se estiver ausente e for uma entidade
     * institucional operada por um usuário
     * autenticado com instituição (MANAGER,
     * TEACHER ou STUDENT), utiliza a instituição
     * desse usuário.
     */
    private String resolveInstitutionId(
            Auditable auditable,
            JoinPoint jp,
            Object result
    ) {
        String evaluated = evaluate(
            auditable.institutionId(),
            jp,
            result
        );

        if (
            evaluated != null &&
            !evaluated.isBlank()
        ) {
            return evaluated;
        }

        if (
            !INSTITUTIONAL_ENTITIES.contains(
                auditable.entity()
            )
        ) {
            return null;
        }

        var auth = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (auth == null) {
            return null;
        }

        Object principal = auth.getPrincipal();

        InstitutionModel institution = null;

        if (principal instanceof Manager manager) {
            institution = manager.getInstitution();
        } else if (principal instanceof Teacher teacher) {
            institution = teacher.getInstitution();
        } else if (principal instanceof Student student) {
            institution = student.getInstitution();
        }

        if (
            institution != null &&
            institution.getId() != null
        ) {
            return institution
                    .getId()
                    .toString();
        }

        return null;
    }

    /**
     * Resolve o autor do evento a partir do mesmo usuário autenticado usado
     * por {@link #getUserId()}.
     *
     * ADMIN e eventos sem principal ficam sem instituição. Qualquer falha na
     * leitura devolve valores nulos para não interromper a operação auditada.
     */
    private Actor resolveActor() {
        try {
            var auth = SecurityContextHolder
                    .getContext()
                    .getAuthentication();

            if (
                auth == null ||
                !auth.isAuthenticated() ||
                !(auth.getPrincipal() instanceof User user)
            ) {
                return new Actor(null, null, null);
            }

            InstitutionModel institution = null;

            if (user instanceof Manager manager) {
                institution = manager.getInstitution();
            } else if (user instanceof Teacher teacher) {
                institution = teacher.getInstitution();
            } else if (user instanceof Student student) {
                institution = student.getInstitution();
            }

            return new Actor(
                user.getName(),
                user.getUserRole() == null
                    ? null
                    : user.getUserRole().name(),
                institutionName(institution)
            );

        } catch (Exception e) {
            return new Actor(null, null, null);
        }
    }

    /**
     * Nome da instituição do ator. A associação é LAZY: ler o nome direto do proxy
     * fora de uma sessão lança LazyInitializationException, o que zerava nome e
     * papel do ator (ex.: gestor no logout). Busca pelo id, em transação própria.
     */
    private String institutionName(InstitutionModel institution) {
        if (institution == null || institution.getId() == null) {
            return null;
        }
        return institutionRepository.findById(institution.getId())
                .map(InstitutionModel::getName)
                .orElse(null);
    }

    private record Actor(String name, String role, String institutionName) {}

    private String getUserId() {
        var auth = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (
            auth != null &&
            auth.isAuthenticated() &&
            auth.getPrincipal() instanceof User user
        ) {
            return user.getId().toString();
        }

        return null;
    }

    private String getClientIp() {
        try {
            return ClientIpResolver.resolve(request, trustForwardedFor);
        } catch (IllegalStateException e) {
            // sem requisição HTTP (scheduler, @Async): o evento fica sem IP
            return null;
        }
    }
}
