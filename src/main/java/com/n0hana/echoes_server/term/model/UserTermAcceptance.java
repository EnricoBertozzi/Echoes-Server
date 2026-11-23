package com.n0hana.echoes_server.term.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.n0hana.echoes_server.user.model.User;

import com.n0hana.echoes_server.infra.crypto.AcceptanceIpConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_terms_acceptance")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserTermAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "terms_id", nullable = false)
    private TermModel term;

    @CreationTimestamp
    private Instant acceptedAt;

    private Instant revokedAt;

    @Convert(converter = AcceptanceIpConverter.class)
    @Column(name = "ip_address", length = ENCRYPTED_IP_COLUMN_LENGTH)
    private String ipAddress;

    @Column(name = "user_agent", length = USER_AGENT_MAX_LENGTH)
    private String userAgent;

    public static final int IP_MAX_LENGTH = 45;
    /** IPv6 de 45 caracteres cifrado: prefixo + Base64(12 + 45 + 16 bytes) ~ 107 caracteres */
    public static final int ENCRYPTED_IP_COLUMN_LENGTH = 128;
    public static final int USER_AGENT_MAX_LENGTH = 512;

    public UserTermAcceptance(User user, TermModel term) {
        this.user = user;
        this.term = term;
    }

    public UserTermAcceptance(User user, TermModel term, String ipAddress, String userAgent) {
        this(user, term);
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
}
