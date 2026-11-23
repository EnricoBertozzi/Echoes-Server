package com.n0hana.echoes_server.infra.javers.retention;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Cria as políticas padrão que ainda não existem; nunca sobrescreve uma política já ajustada. */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class JaversRetentionPolicyInitializer implements ApplicationRunner {

    static final int FIVE_YEARS = 1825;

    private record Seed(String type, int hotDays, String legalBasis) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("com.n0hana.echoes_server.user.model.User", 90, "LGPD Art. 7, II e Art. 16, I; CDC Art. 27"),
            new Seed("com.n0hana.echoes_server.institution.InstitutionModel", 180, "LGPD Art. 7, II e Art. 16, I"),
            new Seed("com.n0hana.echoes_server.classroom.ClassroomModel", 180, "LGPD Art. 7, II; guarda acadêmica"),
            new Seed("com.n0hana.echoes_server.classroom.ClassroomMember", 180, "LGPD Art. 7, II; guarda acadêmica"),
            new Seed("com.n0hana.echoes_server.device.DeviceModel", 90, "LGPD Art. 7, IX"),
            new Seed("com.n0hana.echoes_server.device.DeviceScenarioModel", 90, "LGPD Art. 7, IX"),
            new Seed("com.n0hana.echoes_server.device.ClinicalScenarioPresetModel", 90, "LGPD Art. 7, IX"),
            new Seed("com.n0hana.echoes_server.scenario.ScenarioModel", 90, "LGPD Art. 7, IX"),
            new Seed("com.n0hana.echoes_server.animal.AnimalModel", 90, "LGPD Art. 7, IX"),
            new Seed("com.n0hana.echoes_server.auscultation.AuscultationPointModel", 90, "LGPD Art. 7, IX"));

    private final JaversRetentionPolicyRepository repository;

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** @return quantas políticas novas foram criadas */
    public int seed() {
        int created = 0;
        for (Seed seed : SEEDS) {
            if (repository.existsById(seed.type()))
                continue;

            repository.save(JaversRetentionPolicy.builder()
                    .entityType(seed.type())
                    .hotWindowDays(seed.hotDays())
                    .retentionDays(FIVE_YEARS)
                    .legalBasis(seed.legalBasis())
                    .purpose("Histórico de alterações (Javers)")
                    .active(true)
                    .updatedAt(Instant.now())
                    .updatedBy("system:seed")
                    .build());
            created++;
        }
        if (created > 0)
            log.info("Políticas de retenção do Javers criadas: {}", created);
        return created;
    }
}
