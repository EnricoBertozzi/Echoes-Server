package com.n0hana.echoes_server.term.dto;

import com.n0hana.echoes_server.term.model.TermStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateTermStatusRequestDTO(
    @NotNull TermStatus status
) {}
