package com.n0hana.echoes_server.auth.dto;

public record AuthRequestDTO(
    String email,
    String password
) {

}
