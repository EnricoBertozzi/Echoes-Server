package com.n0hana.echoes_server.term.dto;

import com.n0hana.echoes_server.term.model.DocumentType;
import com.n0hana.echoes_server.term.model.TermStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTermRequestDTO(
    @NotBlank String version,
    @NotBlank String content,
    @NotNull DocumentType type,
    TermStatus status
) {}
