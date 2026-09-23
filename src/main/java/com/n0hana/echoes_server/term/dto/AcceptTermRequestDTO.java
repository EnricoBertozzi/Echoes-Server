package com.n0hana.echoes_server.term.dto;

import com.n0hana.echoes_server.term.model.DocumentType;

public record AcceptTermRequestDTO(
    String email,
    DocumentType type
) {}
