package com.n0hana.echoes_server.auth.loginattempt;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.n0hana.echoes_server.user.model.User;

/**
 * Interface JPA para manipulação dos atributos attempts e
 * lockedUntil de usuários do sistema
 * 
 * @author Enrico Beertozzi
 */
public interface LoginAttemptRepository extends JpaRepository<User, UUID> {

    @Modifying
    @Query("update User u set u.loginAttempts = :attempts where id = :id")
    void updateUserAttempts(@Param("id") UUID id, @Param("attempts") Integer attempts);

    @Modifying
    @Query("update User u set u.lockUntil = :lockUntil where id = :id")
    void updateUserLockedUntil(@Param("id") UUID id, @Param("lockUntil") LocalDateTime lockUntil);

    Optional<User> findUserByEmail(String email);
}
