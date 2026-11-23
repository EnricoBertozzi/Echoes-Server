package com.n0hana.echoes_server.infra.javers.retention;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "javers_retention_policy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JaversRetentionPolicy {

    /** nome completo da classe da entidade versionada */
    @Id
    @Column(name = "entity_type", length = 255)
    private String entityType;

    /** snapshots mais novos que isto nunca são podados */
    @Column(name = "hot_window_days", nullable = false)
    private int hotWindowDays;

    /** snapshots mais velhos que isto podem ser podados */
    @Column(name = "retention_days", nullable = false)
    private int retentionDays;

    @Column(name = "legal_basis", nullable = false, length = 512)
    private String legalBasis;

    @Column(length = 512)
    private String purpose;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;
}
