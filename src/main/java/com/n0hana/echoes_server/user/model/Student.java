package com.n0hana.echoes_server.user.model;

import com.n0hana.echoes_server.institution.InstitutionModel;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("STUDENT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Student extends User {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "institution_id")
    private InstitutionModel institution;

    @Override
    public UserRole getUserRole() {
        return UserRole.STUDENT;
    }
}
