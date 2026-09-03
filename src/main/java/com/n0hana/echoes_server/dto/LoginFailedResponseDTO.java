package com.n0hana.echoes_server.dto;

public record LoginFailedResponseDTO(
    String message,
    int attempts,
    int remainingAttempts
) {
    
}
