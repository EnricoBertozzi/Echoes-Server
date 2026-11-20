package com.n0hana.echoes_server.term.dto;

import com.n0hana.echoes_server.term.model.DocumentType;

public record AcceptTermRequestDTO(
    @jakarta.validation.constraints.NotNull(message = "O tipo do termo é obrigatório")
    DocumentType type
) {}
