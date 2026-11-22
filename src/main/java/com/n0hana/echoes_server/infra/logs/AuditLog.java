package com.n0hana.echoes_server.infra.logs;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import com.n0hana.echoes_server.infra.crypto.AuditLogIpConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Immutable
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_log_institution_timestamp", columnList = "institution_id, timestamp"),
        @Index(name = "idx_audit_log_user", columnList = "user_id")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String userId;
    private String action;
    private String entity;
    private String status;
    private String details;

    @Convert(converter = AuditLogIpConverter.class)
    @Column(length = 255)
    private String ip;

    /** Id do registro afetado (ex.: o dispositivo alterado). Pode ser nulo. */
    private String entityId;

    /**
     * Nome legível do registro afetado, salvo no momento da ação (ex.: o título
     * da turma). Pode ser nulo.
     */
    @Column(length = 255)
    private String entityName;

    /** Instituição dona do registro afetado; permite ao gestor ver só os logs dela. */
    private String institutionId;

    /** Nome do autor da ação, salvo no momento do evento. Pode ser nulo. */
    @Column(length = 255)
    private String actorName;

    /** Papel do autor da ação (ex.: MANAGER). Pode ser nulo. */
    @Column(length = 32)
    private String actorRole;

    /** Instituição do autor no momento da ação. Pode ser nulo. */
    @Column(length = 255)
    private String actorInstitutionName;

    /** definido pelo serviço antes de gravar, para fazer parte da assinatura */
    private Instant timestamp;

    private String signature;

    @PreRemove
    private void preventDelete() {
        throw new UnsupportedOperationException("AuditLog entries cannot be deleted");
    }

    public static AuditLogBuilder builder() {
        return new AuditLogBuilder();
    }

    public static class AuditLogBuilder {
        private String userId;
        private String action;
        private String entity;
        private String status;
        private String details;
        private String ip;
        private String entityId;
        private String entityName;
        private String institutionId;
        private String actorName;
        private String actorRole;
        private String actorInstitutionName;

        public AuditLogBuilder entityId(String entityId) {
            this.entityId = entityId;
            return this;
        }

        public AuditLogBuilder entityName(String entityName) {
            this.entityName = entityName;
            return this;
        }

        public AuditLogBuilder institutionId(String institutionId) {
            this.institutionId = institutionId;
            return this;
        }

        public AuditLogBuilder actorName(String actorName) {
            this.actorName = actorName;
            return this;
        }

        public AuditLogBuilder actorRole(String actorRole) {
            this.actorRole = actorRole;
            return this;
        }

        public AuditLogBuilder actorInstitutionName(String actorInstitutionName) {
            this.actorInstitutionName = actorInstitutionName;
            return this;
        }

        public AuditLogBuilder ip(String ip) {
            this.ip = ip;
            return this;
        }

        public AuditLogBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public AuditLogBuilder action(String action) {
            this.action = action;
            return this;
        }

        public AuditLogBuilder entity(String entity) {
            this.entity = entity;
            return this;
        }

        public AuditLogBuilder status(String status) {
            this.status = status;
            return this;
        }

        public AuditLogBuilder details(String details) {
            this.details = details;
            return this;
        }

        public AuditLog build() {
            AuditLog log = new AuditLog();
            log.userId = this.userId;
            log.action = this.action;
            log.entity = this.entity;
            log.status = this.status;
            log.details = this.details;
            log.ip = this.ip;
            log.entityId = this.entityId;
            log.entityName = this.entityName;
            log.institutionId = this.institutionId;
            log.actorName = this.actorName;
            log.actorRole = this.actorRole;
            log.actorInstitutionName = this.actorInstitutionName;

            return log;
        }
    }
}
