package com.n0hana.echoes_server.infra.javers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.n0hana.echoes_server.infra.javers.retention.JaversRetentionPolicy;
import com.n0hana.echoes_server.infra.javers.retention.JaversRetentionPolicyInitializer;
import com.n0hana.echoes_server.infra.javers.retention.JaversRetentionPolicyRepository;

@SpringBootTest
@ActiveProfiles("test")
class JaversRetentionPolicyInitializerTests {

    @Autowired private JaversRetentionPolicyRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Seed cria as 10 políticas, é idempotente e não sobrescreve ajuste manual")
    void seedsIdempotentlyWithoutOverwriting() {
        JaversRetentionPolicyInitializer initializer = new JaversRetentionPolicyInitializer(repository);

        assertEquals(10, initializer.seed());
        assertEquals(10, repository.count());

        JaversRetentionPolicy user = repository.findById("com.n0hana.echoes_server.user.model.User").orElseThrow();
        assertEquals(90, user.getHotWindowDays());
        assertEquals(1825, user.getRetentionDays());
        assertTrue(user.getLegalBasis().contains("LGPD Art. 7, II"));

        user.setHotWindowDays(7);
        repository.save(user);

        assertEquals(0, initializer.seed());
        assertEquals(10, repository.count());
        assertEquals(7, repository.findById(user.getEntityType()).orElseThrow().getHotWindowDays());
        assertEquals(10, repository.findByActiveTrue().size());
    }
}
