package com.n0hana.echoes_server.term.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateTermContentRequestDTO(
    @NotBlank String content
) {}
