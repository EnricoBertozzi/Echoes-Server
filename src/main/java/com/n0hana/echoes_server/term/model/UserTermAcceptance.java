package com.n0hana.echoes_server.term.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.n0hana.echoes_server.user.model.User;

import jakarta.persistence.Column;
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

    /**
     * Preenchido quando o titular revoga o consentimento (RF17). O registro é
     * mantido como prova do consentimento dado; só aceites ativos (revokedAt nulo)
     * contam para liberar o uso do sistema.
     */
    private Instant revokedAt;

    /**
     * Origem do aceite, como prova do consentimento (LGPD art. 8º, §2º): IP do
     * cliente e User-Agent do navegador no momento em que o titular aceitou.
     * Ficam nulos quando o aceite não vem de uma requisição HTTP.
     */
    @Column(name = "ip_address", length = IP_MAX_LENGTH)
    private String ipAddress;

    @Column(name = "user_agent", length = USER_AGENT_MAX_LENGTH)
    private String userAgent;

    public static final int IP_MAX_LENGTH = 45;
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
