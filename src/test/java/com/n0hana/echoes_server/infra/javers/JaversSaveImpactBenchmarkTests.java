package com.n0hana.echoes_server.infra.javers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.n0hana.echoes_server.user.UserRepository;
import com.n0hana.echoes_server.user.model.Student;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Benchmark do impacto do Javers no save. Desligado por padrão (não roda no CI); para executar:
 * {@code -Djavers.benchmark=true -Dtest=JaversSaveImpactBenchmarkTests}.
 *
 * <p>As duas variantes rodam no mesmo contexto e intercaladas, para dividir igualmente o aquecimento da JVM:
 * {@code users.save} passa pelo aspecto do Javers; o {@code persist} direto em transação não passa. Um benchmark
 * com dois contextos (um com {@code javers.spring-data-auditable-repository-aspect-enabled=false}) mostrou-se
 * enviesado pela ordem de execução.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "javers.benchmark", matches = "true")
class JaversSaveImpactBenchmarkTests {

    private static final int WARMUP = 200;
    private static final int SAMPLES = 1000;

    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @PersistenceContext private EntityManager em;

    @Test
    @DisplayName("save de 1000 usuários: com Javers (repositório) x sem Javers (persist em transação)")
    void javersSaveImpact() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<Long> with = new ArrayList<>();
        List<Long> without = new ArrayList<>();
        List<Student> created = new ArrayList<>();

        try {
            for (int i = 0; i < WARMUP + SAMPLES; i++) {
                Student a = student("with", i);
                long t0 = System.nanoTime();
                created.add(users.save(a));
                long withNs = System.nanoTime() - t0;

                Student b = student("without", i);
                long t1 = System.nanoTime();
                tx.executeWithoutResult(status -> em.persist(b));
                long withoutNs = System.nanoTime() - t1;
                created.add(b);

                if (i >= WARMUP) {
                    with.add(withNs);
                    without.add(withoutNs);
                }
            }
        } finally {
            created.forEach(c -> users.findById(c.getId()).ifPresent(u -> tx.executeWithoutResult(s -> em.remove(em.contains(u) ? u : em.merge(u)))));
        }

        String w = stats(with);
        String wo = stats(without);
        double ratio = mean(with) / mean(without);
        System.out.println("BENCHMARK com-javers   n=" + SAMPLES + " " + w);
        System.out.println("BENCHMARK sem-javers   n=" + SAMPLES + " " + wo);
        System.out.printf("BENCHMARK razao(media) com/sem = %.2fx%n", ratio);
    }

    private static Student student(String label, int i) {
        Student s = new Student();
        s.setEmail("bench-" + label + "-" + i + "-" + UUID.randomUUID() + "@test.local");
        s.setName("Bench " + i);
        s.setPassword("hash");
        return s;
    }

    private static double mean(List<Long> nanos) {
        return nanos.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
    }

    private static String stats(List<Long> nanos) {
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        double p95 = sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1) / 1e6;
        double max = sorted.get(sorted.size() - 1) / 1e6;
        return String.format("mean=%.3fms p95=%.3fms max=%.3fms", mean(nanos), p95, max);
    }
}
