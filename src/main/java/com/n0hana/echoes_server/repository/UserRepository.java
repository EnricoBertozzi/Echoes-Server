package com.n0hana.echoes_server.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.n0hana.echoes_server.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findUserByEmail(String email);
    
    // Adicione esta linha para resolver o erro do UserService:
    Optional<User> findByEmail(String email); 
}
