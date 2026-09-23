package com.n0hana.echoes_server.term.dto;

import java.time.Instant;

import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermStatus;

public record TermResponseDTO(
    Long id,
    String version,
    String content,
    DocumentType type,
    TermStatus status,
    Instant timestamp
) {}
