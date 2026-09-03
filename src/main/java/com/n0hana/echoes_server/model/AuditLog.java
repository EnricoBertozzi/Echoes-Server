package com.n0hana.echoes_server.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Immutable
@Table(name = "audit_log")
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
    private String ip;

    @CreationTimestamp
    private Instant timestamp;

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

            return log;
        }
    }
}
