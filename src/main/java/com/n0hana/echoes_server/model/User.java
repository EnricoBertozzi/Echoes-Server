package com.n0hana.echoes_server.model;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class User implements UserDetails {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    @Column(unique = true)
    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    private int loginAttempts;

    private LocalDateTime lockUntil;

    private boolean active = true;

    public User(String name, String email, String password, UserRole role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
    }
    // Verifique se o atributo de MFA está declarado exatamente assim:
    private boolean mfaEnabled; 

    // Verifique se o atributo de auditoria de criação está declarado exatamente assim:
    @CreationTimestamp
    private Instant createdAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role.compare(UserRole.ADMIN)) {
            return List.of(
                new SimpleGrantedAuthority("ROLE_" + UserRole.ADMIN.getName())
            );
        } else if (role.compare(UserRole.TEACHER)) {
            return List.of(
                new SimpleGrantedAuthority("ROLE_" + UserRole.TEACHER.getName()) 
            );
        } else if (role.compare(UserRole.STUDENT)) {
            return List.of(
                new SimpleGrantedAuthority("ROLE_" + UserRole.STUDENT.getName()) 
            );
        }
        return List.of();
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return lockUntil == null || lockUntil.isBefore(LocalDateTime.now());
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

}
