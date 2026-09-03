package com.n0hana.echoes_server.dto;

public record ReactivateRequestDTO(
    String email,
    String code
) {}