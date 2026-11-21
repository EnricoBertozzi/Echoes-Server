package com.n0hana.echoes_server.infra.javers.retention;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;

/**
 * O Javers grava o nome da classe concreta (ex.: Student, não User). Este resolvedor devolve o tipo
 * informado na política mais as subclasses concretas da aplicação.
 */
@Component
public class JaversTypeResolver {

    private static final String BASE_PACKAGE = "com.n0hana.echoes_server";

    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    public List<String> concreteTypeNames(String entityType) {
        return cache.computeIfAbsent(entityType, this::resolve);
    }

    private List<String> resolve(String entityType) {
        List<String> names = new ArrayList<>();
        names.add(entityType);

        Class<?> base;
        try {
            base = Class.forName(entityType);
        } catch (ClassNotFoundException e) {
            return names;
        }

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                return true;
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(base));
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            String name = candidate.getBeanClassName();
            if (name != null && !names.contains(name))
                names.add(name);
        }
        return names;
    }
}
