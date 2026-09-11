package com.n0hana.echoes_server.auth.dto;

public record AuthFailedResponseDTO(
    String message,
    int attempts,
    int remainingAttempts
) {
    
}
