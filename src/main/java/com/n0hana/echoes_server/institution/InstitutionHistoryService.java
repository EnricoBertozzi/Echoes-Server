package com.n0hana.echoes_server.institution;

import java.util.List;
import java.util.UUID;

import org.javers.core.Javers;
import org.javers.core.diff.changetype.InitialValueChange;
import org.javers.core.diff.changetype.ValueChange;
import org.javers.repository.jql.QueryBuilder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InstitutionHistoryService {

    private final Javers javers;

    // alterações da instituição, da mais recente para a mais antiga
    public List<InstitutionChangeDTO> findHistory(UUID institutionId, int limit) {
        var query = QueryBuilder.byInstanceId(institutionId, InstitutionModel.class)
                .limit(limit)
                .build();

        // o primeiro registro da instituição não é uma alteração, então fica de fora
        return javers.findChanges(query).stream()
                .filter(change -> change instanceof ValueChange && !(change instanceof InitialValueChange))
                .map(change -> InstitutionChangeDTO.from((ValueChange) change))
                .toList();
    }
}
