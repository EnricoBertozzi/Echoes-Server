package com.n0hana.echoes_server.infra.javers;

import org.javers.core.Javers;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstitutionJaversBaseline {

    private final InstitutionRepository repository;
    private final Javers javers;

    // registra o estado atual das instituições que ainda não têm histórico
    // assim a primeira alteração de uma instituição antiga mostra o antes
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        try {
            for (InstitutionModel institution : repository.findAll()) {
                if (javers.getLatestSnapshot(institution.getId(), InstitutionModel.class).isEmpty()) {
                    javers.commit("sistema", institution);
                }
            }
        } catch (Exception e) {
            log.error("Falha ao registrar o estado inicial das instituições no JaVers", e);
        }
    }
}
