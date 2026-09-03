package com.n0hana.echoes_server.dto;

import java.time.LocalDateTime;

/**
 * DTO dedicado exclusivamente para encapsular a exportação segura de dados 
 * em conformidade com as diretrizes de portabilidade de dados.
 */
public record UserProfileExportDTO(
    String name,
    String email,
    String role,
    LocalDateTime exportedAt
) {}
