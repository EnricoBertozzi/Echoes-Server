package com.n0hana.echoes_server.user;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;

public interface UserRepository extends JpaRepository<UserModel, UUID> {
  
  /**
   * Busca dados do usuário por email e retorna um {@code UserDetails}. Uso para Spring Security.
   *
   * @param email - Email do Usuário.
   * */
  UserDetails findUserByEmail(String email);  
}
