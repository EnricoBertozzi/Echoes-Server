package com.n0hana.echoes_server.institution;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidade representante das instituições do sistema.
 * 
 * @since 0.1.5
 * @author Miguel Santana da Costa
 */
@Entity
@Table(name = "institutions", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "cnpj", "delete_token" }),
        @UniqueConstraint(columnNames = { "email", "delete_token" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted = false")
public class InstitutionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 200)
    private String address;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String acronym;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column(name = "nome_fantasia", length = 200)
    private String nomeFantasia;

    @Column(nullable = false)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "cep", length = 8)
    private String cep;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private InstitutionVerificationStatus verificationStatus = InstitutionVerificationStatus.PENDING_VERIFICATION;

    @Column(name = "last_verification_attempt_at")
    private LocalDateTime lastVerificationAttemptAt;

    @Builder.Default
    @Column(name = "verification_attempts", nullable = false)
    private int verificationAttempts = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @Builder.Default
    @Column(name = "delete_token", nullable = false)
    private String deleteToken = "ACTIVE";

    @CreationTimestamp
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
