package com.n0hana.echoes_server.dto;

import com.n0hana.echoes_server.model.DocumentType;

public record CreateTermsRequestDTO(
    String version,
    String content,
    DocumentType type
) {}
