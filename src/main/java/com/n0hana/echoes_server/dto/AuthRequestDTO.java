package com.n0hana.echoes_server.dto;

public record AuthRequestDTO(
    String email,
    String password
) {

}
