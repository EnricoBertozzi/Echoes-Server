package com.n0hana.echoes_server.institution;

import java.util.List;
import java.util.UUID;

import org.javers.core.Javers;
import org.javers.core.diff.changetype.ValueChange;
import org.javers.repository.jql.QueryBuilder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class institutionHistoryService{
    private final Javers javers;

    //alterações da intituição, da mais recente para a mais antiga
    public List<institutionChangeDTO> findHistory(UUID institutionID, int limit){
        var query = QueryBuilder.byInstanceId(institutionId, InstitutionModel.class)
            .limit(limit)
            .build();

        return javers.findChanges(query).stream()
            .filter(ValueChange.class::isInstance)
            .map(change -> institutionChangeDTO.from((ValueChange) change))
            .toList(); e
    }
}
