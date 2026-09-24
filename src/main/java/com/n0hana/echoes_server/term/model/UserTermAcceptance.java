package com.n0hana.echoes_server.term.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.n0hana.echoes_server.user.model.User;

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

    public UserTermAcceptance(User user, TermModel term) {
        this.user = user;
        this.term = term;
    }
}
