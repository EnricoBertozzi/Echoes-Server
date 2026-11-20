package com.n0hana.echoes_server.user.dto;

import java.util.UUID;

public record UserEmailDTO(UUID id, String name, String email, UUID institutionId) {}
