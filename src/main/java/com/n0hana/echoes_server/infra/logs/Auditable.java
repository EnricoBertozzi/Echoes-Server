package com.n0hana.echoes_server.infra.logs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotação para implementação de auditoria em métodos
 * 
 * @author Enrico Bertozzi
 * @since 0.1.1
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();

    String entity() default "";

    /**
     * Expressão SpEL do id do registro afetado. Variáveis: os parâmetros do
     * método pelo nome (ex.: {@code #id}) e {@code #result} (retorno, só no
     * sucesso). Ex.: {@code "#result.id"}. Vazio = não registra.
     */
    String entityId() default "";

    /**
     * Expressão SpEL do nome legível do registro afetado (mesmas variáveis),
     * salvo no momento da ação. Ex.: {@code "#result.title"}. Vazio = não
     * registra.
     */
    String entityName() default "";

    /**
     * Expressão SpEL dos detalhes adicionais, avaliada no sucesso e salva no
     * campo {@code details} do registro (mesmas variáveis). Vazio = não
     * registra.
     */
    String details() default "";

    /** Expressão SpEL da instituição do registro afetado (mesmas variáveis). */
    String institutionId() default "";
}
