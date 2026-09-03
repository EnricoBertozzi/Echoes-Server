package com.n0hana.echoes_server.service;

import com.n0hana.echoes_server.dto.UserProfileExportDTO;
// IMPORTANTE: Garanta que o import abaixo aponta para a SUA entidade User, e não para org.springframework.security.core.userdetails.User
import com.n0hana.echoes_server.model.User; 
import com.n0hana.echoes_server.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Busca o usuário autenticado pelo e-mail e converte os dados para o formato de exportação.
     * @param email Email extraído do Token JWT.
     * @return UserProfileExportDTO contendo apenas dados seguros.
     */
   
}
